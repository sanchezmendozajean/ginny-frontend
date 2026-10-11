package com.ginny.frontend.data.scrap

import android.content.Context
import android.net.Uri
import coil.Coil
import coil.memory.MemoryCache
import com.ginny.frontend.data.GameIndexRepository
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.scrap.steamgriddb.SteamGridDbClient
import com.ginny.frontend.data.scrap.steamgriddb.SteamGridDbImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Cuántos íconos se guardan, como máximo, por juego (el actual cuenta). */
const val MAX_ICON_ALTERNATIVES = 5

/** Un ícono alternativo guardado en \`icons-alt/<romFileName>/<index>.png\`. */
data class IconAlternativeFile(val index: Int, val uri: Uri, val modified: Long)

/** Un ícono alternativo con la huella de su contenido (para saber cuál es el que se muestra y evitar repetidos). */
data class IconAlternative(val index: Int, val uri: Uri, val modified: Long, val hash: String)

/**
 * Lo guardado de los íconos de un juego: el que se muestra ([active], \`icons/<rom>.png\`), los
 * alternativos, y cuál de ellos es el que se muestra ([activeIndex]; null si el actual no está entre
 * los alternativos todavía).
 */
data class IconLibrary(val active: Uri?, val alternatives: List<IconAlternative>, val activeIndex: Int?)

/** Qué número de ícono alternativo toca usar. Código puro. */
object IconSlots {
    /**
     * El número (1..[max]) donde guardar un ícono nuevo: el primero libre; si están todos ocupados, el del
     * ícono **más antiguo que no sea el que se muestra** ([activeIndex]). [alternatives] son pares
     * (número, última modificación).
     */
    fun slotForNew(alternatives: List<Pair<Int, Long>>, activeIndex: Int?, max: Int = MAX_ICON_ALTERNATIVES): Int? {
        val used = alternatives.map { it.first }.toSet()
        (1..max).firstOrNull { it !in used }?.let { return it }
        return alternatives.filter { it.first != activeIndex }.minByOrNull { it.second }?.first
    }
}

/**
 * Elegir el ícono de un juego entre varios.
 *
 * **Cómo se guarda.** El ícono que se muestra sigue siendo \`Assets/<sistema>/icons/<rom>.png\` (lo que ya
 * leen la grilla, el escaneo y Ginny Assets Manager). Las opciones van aparte, en
 * \`Assets/<sistema>/icons-alt/<rom>/1.png, 2.png...\` (hasta [MAX_ICON_ALTERNATIVES]). "Usar" copia la
 * elegida sobre \`icons/<rom>.png\`; el ícono que había se conserva antes como una alternativa. Cuál es la
 * que se muestra se averigua comparando contenido: no hay ningún archivo de estado.
 */
class IconChooser(
    private val context: Context,
    private val storage: ScrapStorage,
    private val steamGridDb: SteamGridDbClient,
    private val gameIndex: GameIndexRepository,
) {

    /** Los íconos guardados de [game]. Si no se puede abrir la Carpeta de Usuario, una biblioteca vacía. */
    suspend fun load(game: GameEntry): IconLibrary {
        val assets = (storage.openSystem(game.systemId) as? ScrapResult.Ok)?.value
            ?: return IconLibrary(null, emptyList(), null)
        return load(assets, game.romFileName)
    }

    private suspend fun load(assets: SystemAssets, rom: String): IconLibrary = withContext(Dispatchers.IO) {
        val activeFile = assets.find(AssetFolder.ICONS, rom)
        val alternatives = assets.listIconAlternatives(rom).mapNotNull { file ->
            readBytes(file.uri)?.let { IconAlternative(file.index, file.uri, file.modified, sha1(it)) }
        }
        val activeHash = activeFile?.let { readBytes(it.uri) }?.let(::sha1)
        IconLibrary(
            active = activeFile?.uri,
            alternatives = alternatives,
            activeIndex = activeHash?.let { hash -> alternatives.firstOrNull { it.hash == hash }?.index },
        )
    }

    /** Las imágenes de SteamGridDB para elegir. Usa el nombre estándar (de los metadatos) si lo hay. */
    suspend fun search(game: GameEntry): ScrapResult<List<SteamGridDbImage>> {
        val assets = (storage.openSystem(game.systemId) as? ScrapResult.Ok)?.value
        val metadata = assets?.readMetadata(game.romFileName)
        return steamGridDb.findIconCandidates(metadata?.name ?: game.displayName, metadata?.releaseYear)
    }

    /** Pone [alternative] como el ícono del juego. Devuelve el ícono que ahora se muestra. */
    suspend fun use(game: GameEntry, alternative: IconAlternative): ScrapResult<Uri> {
        val assets = when (val opened = storage.openSystem(game.systemId)) {
            is ScrapResult.Ok -> opened.value
            is ScrapResult.Failure -> return opened
        }
        val png = readBytes(alternative.uri) ?: return ScrapResult.StorageFailed("no se pudo leer el ícono elegido")
        preserveActive(assets, game.romFileName)?.let { return it }
        return activate(game, assets, png)
    }

    /**
     * Descarga [image] de SteamGridDB, la guarda como un ícono alternativo nuevo (o reutiliza uno igual que
     * ya esté) y la pone como el ícono del juego. Devuelve el ícono que ahora se muestra.
     */
    suspend fun addAndUse(game: GameEntry, image: SteamGridDbImage): ScrapResult<Uri> {
        val assets = when (val opened = storage.openSystem(game.systemId)) {
            is ScrapResult.Ok -> opened.value
            is ScrapResult.Failure -> return opened
        }
        val png = when (val prepared = download(image)) {
            is ScrapResult.Ok -> prepared.value
            is ScrapResult.Failure -> return prepared
        }
        val rom = game.romFileName
        preserveActive(assets, rom)?.let { return it }

        // Una igual ya guardada: no se repite.
        val library = load(assets, rom)
        val hash = sha1(png)
        if (library.alternatives.none { it.hash == hash }) {
            val slot = IconSlots.slotForNew(library.alternatives.map { it.index to it.modified }, library.activeIndex)
                ?: return ScrapResult.StorageFailed("no hay dónde guardar el ícono")
            when (val saved = assets.writeIconAlternative(rom, slot, png)) {
                is ScrapResult.Ok -> invalidate(saved.value)
                is ScrapResult.Failure -> return saved
            }
        }
        return activate(game, assets, png)
    }

    // ── Interno ───────────────────────────────────────────────────────────────────────────────

    /** Descarga la imagen y la deja como PNG cuadrado de hasta 512 (como el scrapper). */
    private suspend fun download(image: SteamGridDbImage): ScrapResult<ByteArray> {
        val buffer = ByteArrayOutputStream()
        when (val downloaded = steamGridDb.download(image, buffer)) {
            is ScrapResult.Failure -> return downloaded
            is ScrapResult.Ok -> Unit
        }
        return withContext(Dispatchers.Default) { IconProcessor.toSquarePng(buffer.toByteArray()) }
    }

    /**
     * Si hay un ícono mostrándose que no está entre los alternativos, lo guarda como uno: así cambiar de
     * ícono nunca lo pierde. Devuelve un fallo, o null si todo bien.
     */
    private suspend fun preserveActive(assets: SystemAssets, rom: String): ScrapResult.Failure? {
        val library = load(assets, rom)
        val active = library.active ?: return null
        if (library.activeIndex != null) return null
        val png = readBytes(active) ?: return null
        val slot = IconSlots.slotForNew(library.alternatives.map { it.index to it.modified }, library.activeIndex)
            ?: return ScrapResult.StorageFailed("no hay dónde conservar el ícono actual")
        return when (val saved = assets.writeIconAlternative(rom, slot, png)) {
            is ScrapResult.Ok -> { invalidate(saved.value); null }
            is ScrapResult.Failure -> saved
        }
    }

    /** Escribe [png] como el ícono que se muestra y avisa a la grilla. */
    private suspend fun activate(game: GameEntry, assets: SystemAssets, png: ByteArray): ScrapResult<Uri> {
        val written = assets.write(AssetFolder.ICONS, game.romFileName, "png", overwrite = true) { out ->
            out.write(png)
            ScrapResult.Ok(png.size.toLong())
        }
        val uri = when (written) {
            is ScrapResult.Ok -> written.value
            is ScrapResult.Failure -> return written
        }
        // El archivo tiene la misma dirección de siempre: se descarta la imagen que Coil guardó de la anterior
        // y la grilla recibe una dirección con versión, para que recargue.
        invalidate(uri)
        val shown = uri.buildUpon().appendQueryParameter("v", System.currentTimeMillis().toString()).build()
        gameIndex.updateIcon(game, shown)
        return ScrapResult.Ok(shown)
    }

    private fun invalidate(uri: Uri) {
        Coil.imageLoader(context).memoryCache?.remove(MemoryCache.Key(uri.toString()))
    }

    private suspend fun readBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
