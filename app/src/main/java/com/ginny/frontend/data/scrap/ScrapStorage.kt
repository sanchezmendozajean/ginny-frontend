package com.ginny.frontend.data.scrap

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ginny.frontend.data.SettingsRepository
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.util.SafUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream

private const val ASSETS_DIR_NAME = "Assets"
private const val PART_SUFFIX = ".part"
// "application/octet-stream" y no la del contenido: los proveedores SAF añaden la extensión canónica
// de la mime al nombre (ver GinnyConfigRepository), y este nombre ya lleva la suya.
private const val PART_MIME_TYPE = "application/octet-stream"

/**
 * Escribe lo que descarga el scrapper en `Assets/<sistema>/…` de la Carpeta de Usuario (ver
 * [AssetFolder] para las carpetas y el formato de los nombres).
 */
class ScrapStorage(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    /**
     * Abre (y crea si falta) `Assets/<sistema>/`. Falla con un [ScrapResult.StorageFailed] **fatal** si
     * no hay Carpeta de Usuario o ya no se puede abrir: sin ella no hay dónde guardar nada.
     */
    suspend fun openSystem(systemId: SystemId): ScrapResult<SystemAssets> = guarded {
        withContext(Dispatchers.IO) {
            val userUri = settings.userFolderUri.first()?.let(Uri::parse)
                ?: return@withContext fatal("no hay Carpeta de Usuario; elígela en Configuración > Carpetas")
            val root = SafUtils.treeDocument(context, userUri)
                ?: return@withContext fatal("no se pudo abrir la Carpeta de Usuario")
            val assets = SafUtils.getOrCreateDirectory(root, ASSETS_DIR_NAME)
                ?: return@withContext fatal("no se pudo abrir la carpeta Assets")
            val systemDir = SafUtils.getOrCreateDirectory(assets, systemId.folderName)
                ?: return@withContext fatal("no se pudo abrir la carpeta de ${systemId.folderName}")
            ScrapResult.Ok(SystemAssets(context, systemDir))
        }
    }

    /**
     * Borra todo lo que el scrapper (o el Assets Manager) guardó de un juego en cualquier carpeta:
     * ícono, wheel, cajas, video y metadatos. Lo usa "Eliminar juego". Devuelve cuántos archivos borró.
     */
    suspend fun deleteGameAssets(systemId: SystemId, romFileName: String): Int {
        val system = (openSystem(systemId) as? ScrapResult.Ok)?.value ?: return 0
        return system.deleteGame(romFileName)
    }

    private fun fatal(detail: String) = ScrapResult.StorageFailed(detail, fatal = true)
}

/**
 * Las carpetas de un sistema abiertas para escribir. Lista cada carpeta **una sola vez** (cada
 * consulta por SAF es lenta) y mantiene esa lista al día con lo que escribe. No es seguro usarla desde
 * varias corrutinas a la vez: el orquestador la usa de a un juego.
 */
class SystemAssets internal constructor(
    private val context: Context,
    private val systemDir: DocumentFile,
) {
    private class FolderState(val dir: DocumentFile, val files: MutableMap<String, DocumentFile>)

    private val folders = HashMap<AssetFolder, FolderState>()

    /** El archivo ya guardado de [romFileName] en [folder] (con cualquiera de sus extensiones), o null. */
    suspend fun find(folder: AssetFolder, romFileName: String): DocumentFile? = withContext(Dispatchers.IO) {
        val state = state(folder, create = false) ?: return@withContext null
        folder.extensions.firstNotNullOfOrNull { ext -> state.files["${romFileName.lowercase()}.$ext"] }
    }

    suspend fun has(folder: AssetFolder, romFileName: String): Boolean = find(folder, romFileName) != null

    /**
     * Guarda en `<carpeta>/<romFileName>.<extension>` lo que escriba [produce]. **Nunca sobrescribe**:
     * si ya hay un archivo de ese juego en la carpeta, no llama a [produce] y devuelve el existente.
     *
     * Escribe primero en un `.part` y recién al terminar bien lo renombra, así un corte a mitad (cierre
     * de la app, cancelación, error de red) no deja un archivo truncado que luego se tome por bueno; los
     * `.part` sueltos se borran la siguiente vez que se abre la carpeta.
     */
    suspend fun write(
        folder: AssetFolder,
        romFileName: String,
        extension: String,
        produce: suspend (OutputStream) -> ScrapResult<Long>,
    ): ScrapResult<Uri> = guarded {
        withContext(Dispatchers.IO) {
            val state = state(folder, create = true)
                ?: return@withContext ScrapResult.StorageFailed("no se pudo crear la carpeta ${folder.dirName}")
            val ext = extension.lowercase().trimStart('.')
            folder.extensions.firstNotNullOfOrNull { state.files["${romFileName.lowercase()}.$it"] }
                ?.let { return@withContext ScrapResult.Ok(it.uri) }

            val finalName = "$romFileName.$ext"
            val part = state.dir.createFile(PART_MIME_TYPE, finalName + PART_SUFFIX)
                ?: return@withContext ScrapResult.StorageFailed("no se pudo crear $finalName")

            var finished = false
            try {
                val out = context.contentResolver.openOutputStream(part.uri, "w")
                    ?: return@withContext ScrapResult.StorageFailed("no se pudo abrir $finalName para escribir")
                val result = out.use { produce(it) }
                if (result is ScrapResult.Failure) return@withContext result
                if (!part.renameTo(finalName)) {
                    return@withContext ScrapResult.StorageFailed("no se pudo renombrar $finalName")
                }
                state.files[finalName.lowercase()] = part
                finished = true
                ScrapResult.Ok(part.uri)
            } finally {
                // También al cancelarse: nunca queda un .part a medias.
                if (!finished) runCatching { part.delete() }
            }
        }
    }

    /** Lee los metadatos guardados de un juego; null si no hay o el archivo no es válido (p. ej. truncado). */
    suspend fun readMetadata(romFileName: String): GameMetadata? = withContext(Dispatchers.IO) {
        val file = find(AssetFolder.METADATA, romFileName) ?: return@withContext null
        val text = runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { it.reader(Charsets.UTF_8).readText() }
        }.getOrNull() ?: return@withContext null
        GameMetadata.fromJson(text)
    }

    /**
     * Guarda los metadatos de un juego, **reemplazando** los anteriores si los hay (a diferencia de las
     * imágenes: el JSON es del scrapper, no del usuario).
     */
    suspend fun writeMetadata(romFileName: String, metadata: GameMetadata): ScrapResult<Uri> = guarded {
        val bytes = metadata.toJson().toByteArray(Charsets.UTF_8)
        val existing = find(AssetFolder.METADATA, romFileName)
        if (existing == null) {
            write(AssetFolder.METADATA, romFileName, "json") { out ->
                out.write(bytes)
                ScrapResult.Ok(bytes.size.toLong())
            }
        } else {
            withContext(Dispatchers.IO) {
                val out = context.contentResolver.openOutputStream(existing.uri, "wt")
                    ?: return@withContext ScrapResult.StorageFailed("no se pudo abrir los metadatos para escribir")
                out.use { it.write(bytes) }
                ScrapResult.Ok(existing.uri)
            }
        }
    }

    /** Borra todo lo guardado de [romFileName] en todas las carpetas. Devuelve cuántos archivos borró. */
    suspend fun deleteGame(romFileName: String): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (folder in AssetFolder.entries) {
            val state = state(folder, create = false) ?: continue
            for (ext in folder.extensions) {
                val key = "${romFileName.lowercase()}.$ext"
                val file = state.files[key] ?: continue
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    state.files.remove(key)
                    deleted++
                }
            }
        }
        deleted
    }

    /** Estado de una carpeta (su lista de archivos); null si no existe y no se pidió crearla. */
    private fun state(folder: AssetFolder, create: Boolean): FolderState? {
        folders[folder]?.let { return it }
        val dir = if (create) {
            SafUtils.getOrCreateDirectory(systemDir, folder.dirName)
        } else {
            SafUtils.findChild(systemDir, folder.dirName)?.takeIf { it.isDirectory }
        } ?: return null

        val files = HashMap<String, DocumentFile>()
        for (file in dir.listFiles()) {
            val name = file.name ?: continue
            if (name.endsWith(PART_SUFFIX, ignoreCase = true)) {
                runCatching { file.delete() } // resto de una escritura que no terminó
            } else if (file.isFile) {
                files[name.lowercase()] = file
            }
        }
        return FolderState(dir, files).also { folders[folder] = it }
    }
}

/**
 * Ejecuta [block] convirtiendo cualquier excepción de almacenamiento (permiso perdido, archivo que
 * desapareció, proveedor SAF que falla) en un [ScrapResult.StorageFailed]. La cancelación pasa de largo.
 */
private suspend inline fun <T> guarded(block: () -> ScrapResult<T>): ScrapResult<T> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SecurityException) {
        ScrapResult.StorageFailed("se perdió el permiso sobre la Carpeta de Usuario", fatal = true)
    } catch (e: Exception) {
        ScrapResult.StorageFailed(ScrapRedactor.redact(e.message ?: e.javaClass.simpleName))
    }
