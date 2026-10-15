package com.ginny.frontend.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.util.SafUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val ASSETS_DIR_NAME = "Assets"
private const val ICONS_DIR_NAME = "icons"
private const val SYSTEM_LOGO_FILE_NAME = "system.png"
private const val NAMES_DICTIONARY_FILE_NAME = "names.json"

/** Reads game icons and system logos from the user-selected "Carpeta de Usuario" (Assets/<system>/...). */
class AssetRepository(private val context: Context) {

    /** Assets/<systemFolder> directory for [systemId], or null if the User folder or subtree doesn't exist. */
    private fun systemAssetsDir(userTreeUri: Uri, systemId: SystemId): DocumentFile? {
        val root = SafUtils.treeDocument(context, userTreeUri) ?: return null
        val assets = SafUtils.findChild(root, ASSETS_DIR_NAME)?.takeIf { it.isDirectory } ?: return null
        return SafUtils.findChild(assets, systemId.folderName)?.takeIf { it.isDirectory }
    }

    /** Maps lowercase ROM file name (e.g. "bomberman.nes") -> icon Uri, for every icon present. */
    suspend fun loadIconIndex(userTreeUri: Uri, systemId: SystemId): Map<String, Uri> = withContext(Dispatchers.IO) {
        val systemDir = systemAssetsDir(userTreeUri, systemId) ?: return@withContext emptyMap()
        val iconsDir = SafUtils.findChild(systemDir, ICONS_DIR_NAME)?.takeIf { it.isDirectory }
            ?: return@withContext emptyMap()
        iconsDir.listFiles()
            .filter { it.isFile && it.name != null }
            .associate { it.name!!.lowercase() to it.uri }
    }

    /**
     * Los logos (`Assets/<sistema>/system.png`) de varios sistemas de una vez, por id; los que no tienen no están en el mapa.
     *
     * Mucho más rápido que llamar a [systemLogoUri] en un bucle: cada consulta SAF cuesta decenas de ms, y aquella repetía
     * (por sistema) el listado de la raíz y el de `Assets`. Aquí se listan **una sola vez** y lo único que queda por
     * sistema —listar su carpeta para hallar `system.png`— se hace **en paralelo**. Mismo criterio de búsqueda: sin
     * distinguir mayúsculas.
     */
    suspend fun systemLogoUris(userTreeUri: Uri, systemIds: Collection<SystemId>): Map<SystemId, Uri> = withContext(Dispatchers.IO) {
        val root = SafUtils.treeDocument(context, userTreeUri) ?: return@withContext emptyMap()
        val assets = SafUtils.findChild(root, ASSETS_DIR_NAME)?.takeIf { it.isDirectory } ?: return@withContext emptyMap()
        // Una sola lectura de Assets; si hay dos carpetas que solo difieren en mayúsculas, vale la primera (como findChild).
        val systemDirs = HashMap<String, DocumentFile>()
        for (dir in assets.listFiles()) {
            val name = dir.name ?: continue
            if (dir.isDirectory) systemDirs.putIfAbsent(name.lowercase(), dir)
        }
        coroutineScope {
            systemIds
                .map { id ->
                    async {
                        val dir = systemDirs[id.folderName.lowercase()] ?: return@async null
                        runCatching { SafUtils.findChildFileIgnoreCase(dir, SYSTEM_LOGO_FILE_NAME) }.getOrNull()?.let { id to it.uri }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }
    }

    suspend fun systemLogoUri(userTreeUri: Uri, systemId: SystemId): Uri? = withContext(Dispatchers.IO) {
        val systemDir = systemAssetsDir(userTreeUri, systemId) ?: return@withContext null
        SafUtils.findChildFileIgnoreCase(systemDir, SYSTEM_LOGO_FILE_NAME)?.uri
    }

    /**
     * Maps lowercase ROM/folder name (e.g. "3countb.zip") -> display name override, read from the
     * optional `Assets/<system>/names.json` dictionary: `[{"file": "...", "name": "..."}, ...]`.
     * Returns an empty map if the dictionary doesn't exist or fails to parse — callers fall back to
     * the file name as the display name, same as when no dictionary is present at all.
     */
    suspend fun loadNameIndex(userTreeUri: Uri, systemId: SystemId): Map<String, String> = withContext(Dispatchers.IO) {
        val systemDir = systemAssetsDir(userTreeUri, systemId) ?: return@withContext emptyMap()
        val dictFile = SafUtils.findChildFileIgnoreCase(systemDir, NAMES_DICTIONARY_FILE_NAME)
            ?: return@withContext emptyMap()
        val text = runCatching {
            context.contentResolver.openInputStream(dictFile.uri)?.use { it.reader().readText() }
        }.getOrNull() ?: return@withContext emptyMap()

        runCatching {
            val entries = JSONArray(text)
            buildMap {
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    val file = entry.optString("file").takeIf { it.isNotBlank() } ?: continue
                    val name = entry.optString("name").takeIf { it.isNotBlank() } ?: continue
                    put(file.lowercase(), name)
                }
            }
        }.getOrDefault(emptyMap())
    }

    /** Expected icon file name for a given ROM file name, per the "<romFileName>.png" convention. */
    fun expectedIconFileName(romFileName: String): String = "$romFileName.png"

    /** Deletes the icon (if any) for a game being removed via "Eliminar juego". Returns false if
     * there's no User folder, no Assets subtree for [systemId], or no icon file to delete. */
    suspend fun deleteIcon(userTreeUri: Uri, systemId: SystemId, romFileName: String): Boolean =
        withContext(Dispatchers.IO) {
            val systemDir = systemAssetsDir(userTreeUri, systemId) ?: return@withContext false
            val iconsDir = SafUtils.findChild(systemDir, ICONS_DIR_NAME)?.takeIf { it.isDirectory }
                ?: return@withContext false
            val iconFile = SafUtils.findChildFileIgnoreCase(iconsDir, expectedIconFileName(romFileName))
                ?: return@withContext false
            iconFile.delete()
        }
}
