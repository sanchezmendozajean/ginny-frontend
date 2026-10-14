package com.ginny.frontend.data.scrap

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ginny.frontend.data.GameIndexRepository
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.RomOrganization
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.data.scrap.screenscraper.RomLookup
import com.ginny.frontend.data.scrap.screenscraper.ScreenScraperClient
import com.ginny.frontend.data.scrap.screenscraper.ScreenScraperMedia
import com.ginny.frontend.data.scrap.screenscraper.ScreenScraperSelector
import com.ginny.frontend.data.scrap.steamgriddb.SteamGridDbClient
import com.ginny.frontend.data.scrap.steamgriddb.SteamGridDbImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Orquesta el scrapeo de un lote de juegos: para cada uno, decide qué falta ([ScrapPlanner]),
 * consulta ScreenScraper (metadatos, wheel, cajas, video) y SteamGridDB (ícono), y lo guarda en
 * `Assets/` ([ScrapStorage]).
 *
 * - **Secuencial**: un juego a la vez y una petición a la vez (la cuenta de ScreenScraper permite 1 hilo).
 * - **Reanudable**: no pide lo que ya está guardado ni lo que el proveedor ya dijo que no tiene; si el
 *   proceso muere o se cancela, la próxima corrida sigue donde quedó.
 * - **Tolerante**: un proveedor que rechaza las credenciales o agota el cupo se **deja de usar** y el
 *   lote sigue con el otro; solo se corta si no queda ninguno, falta la Carpeta de Usuario, se pierde la
 *   red o la conexión falla [MAX_CONSECUTIVE_FAILURES] veces seguidas.
 * - **Cancelable**: cancelar el flujo aborta la petición en curso y no deja archivos a medias.
 *
 * Sin probar por red dentro de la app todavía (la lógica de decisión sí tiene pruebas unitarias).
 */
class ScrapRepository(
    private val context: Context,
    private val scrapperSettings: ScrapperSettingsRepository,
    private val gameIndex: GameIndexRepository,
    private val storage: ScrapStorage,
    private val screenScraper: ScreenScraperClient,
    private val steamGridDb: SteamGridDbClient,
    private val network: ScrapNetwork,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Toda la biblioteca, agrupada por sistema (así `Assets/<sistema>` se abre una sola vez por sistema). */
    suspend fun libraryGames(): List<GameEntry> =
        gameIndex.observeAllGames().first().sortedWith(compareBy({ it.systemId.ordinal }, { it.displayName.lowercase() }))

    suspend fun systemGames(systemId: SystemId): List<GameEntry> = gameIndex.observeGames(systemId).first()

    /**
     * Calcula, sistema por sistema, cuántos juegos tienen algo por pedir y cuántos el proveedor no
     * tenía, mirando lo que ya hay guardado en `Assets/`. Emite un [SystemScrapStats] por sistema a
     * medida que termina (leer los metadatos de cada juego por SAF es lento en bibliotecas grandes).
     */
    fun inspect(games: List<GameEntry>): Flow<SystemScrapStats> = flow {
        val wanted = scrapperSettings.enabledMedia.first()
        val now = clock()
        for ((systemId, systemGames) in games.groupBy { it.systemId }) {
            currentCoroutineContext().ensureActive()
            val assets = (storage.openSystem(systemId) as? ScrapResult.Ok)?.value
            val pending = mutableSetOf<String>()
            val notFound = mutableSetOf<String>()
            for (game in systemGames) {
                currentCoroutineContext().ensureActive()
                val rom = game.romFileName
                if (assets == null) {
                    pending += rom // sin acceso a Assets no se sabe nada: se lo da por pendiente
                    continue
                }
                val previous = assets.readMetadata(rom)
                val present = presentMedia(assets, rom)
                when {
                    !ScrapPlanner.plan(previous, present, wanted, force = false, now = now).isEmpty -> pending += rom
                    !ScrapPlanner.plan(previous, present, wanted, force = true, now = now).isEmpty -> notFound += rom
                }
            }
            emit(SystemScrapStats(systemId, systemGames.size, pending, notFound))
        }
    }

    private suspend fun presentMedia(assets: SystemAssets, rom: String): Set<ScrapMedia> =
        ScrapMedia.entries
            .filter { it != ScrapMedia.METADATA && assets.has(AssetFolder.of(it), rom) }
            .toSet()

    /**
     * Scrapea [games] en orden y va emitiendo el avance. El último estado emitido trae
     * [ScrapProgress.finished] = true (y [ScrapProgress.stopReason] si se cortó antes de terminar).
     * [force]: ignora las cachés negativas (juegos "no encontrados" y medias ausentes). [overwrite]:
     * vuelve a pedir también lo que ya está guardado y lo reemplaza (si la descarga nueva falla, lo
     * anterior queda intacto); sin él, **nunca** se sobrescribe un archivo existente.
     */
    /**
     * true mientras el modal de avance está abierto: el lote se pone al ritmo de la pantalla (cada juego dura al menos
     * [UI_MIN_GAME_MS], para que se vea el fundido entre una caja de datos y la siguiente). Con el modal cerrado, el lote
     * corre de corrido. Lo lee [run] antes de cada juego, así que se puede abrir y cerrar el modal a mitad de lote.
     */
    @Volatile
    var paceForUi: Boolean = false

    fun run(games: List<GameEntry>, force: Boolean = false, overwrite: Boolean = false): Flow<ScrapProgress> = flow {
        val session = Session(wanted = scrapperSettings.enabledMedia.first(), force = force, overwrite = overwrite)
        val onlyOnWifi = scrapperSettings.onlyOnWifi.first()
        var progress = ScrapProgress(total = games.size)
        emit(progress)

        var openSystem: SystemId? = null
        var assets: SystemAssets? = null

        for ((position, game) in games.withIndex()) {
            currentCoroutineContext().ensureActive()
            val gameStartedAt = System.currentTimeMillis()

            (network.check(onlyOnWifi) as? ScrapResult.Failure)?.let {
                emit(progress.stopped(it.message))
                return@flow
            }
            if (assets == null || openSystem != game.systemId) {
                when (val opened = storage.openSystem(game.systemId)) {
                    is ScrapResult.Ok -> {
                        assets = opened.value
                        openSystem = game.systemId
                    }
                    is ScrapResult.Failure -> {
                        emit(progress.stopped(opened.message))
                        return@flow
                    }
                }
            }

            val systemName = SystemCatalog.byId.getValue(game.systemId).displayName
            // El juego en curso se guarda en `progress` (no solo se emite): entre un juego y el siguiente, lo que se emite sigue
            // trayendo el último juego con su resultado. Antes llevaba `current = null` en ese intervalo, y el modal de avance
            // quitaba la caja de datos de golpe (sin fundido) y la volvía a poner con el juego siguiente.
            val report: suspend (ScrapCurrent) -> Unit = {
                progress = progress.withCurrent(it)
                emit(progress)
            }

            when (val result = processGame(game, assets, session, position + 1, systemName, report)) {
                is GameResult.Done -> progress = progress.after(result.outcome).copy(warnings = session.warnings.toList())
                is GameResult.Stop -> {
                    emit(progress.copy(warnings = session.warnings.toList()).stopped(result.reason))
                    return@flow
                }
            }
            emit(progress)
            if (paceForUi) {
                // La caja del juego que terminó se queda a la vista lo que falte hasta el mínimo; el siguiente juego la
                // desvanece (y la nueva entra con otro fundido) mientras ya se está procesando.
                val remaining = UI_MIN_GAME_MS - (System.currentTimeMillis() - gameStartedAt)
                if (remaining > 0) delay(remaining)
            }
        }
        emit(progress.completed())
    }

    // ── Un juego ──────────────────────────────────────────────────────────────────────────────

    private suspend fun processGame(
        game: GameEntry,
        assets: SystemAssets,
        session: Session,
        index: Int,
        systemName: String,
        report: suspend (ScrapCurrent) -> Unit,
    ): GameResult {
        val rom = game.romFileName
        val now = clock()
        val previous = assets.readMetadata(rom)
        val present = presentMedia(assets, rom)

        val state = GameState(previous, now, present)
        val shown = session.wanted.filter { it != ScrapMedia.METADATA }
        // Lo que se muestra al empezar: lo que el juego ya tiene (con "sobrescribir", se vuelve a pedir todo).
        val metadataWanted = ScrapMedia.METADATA in session.wanted
        state.metadataShown = if (metadataWanted && previous?.hasDetails == true && !session.overwrite) true else null
        shown.forEach { state.mediaShown[it] = if (it in present && !session.overwrite) true else null }
        suspend fun publish(step: String?, outcome: ScrapOutcome? = null, downloading: ScrapMedia? = null) {
            report(
                ScrapCurrent(
                    index = index,
                    fileName = rom,
                    systemName = systemName,
                    step = step,
                    metadataWanted = metadataWanted,
                    metadata = state.metadataShown,
                    media = state.mediaShown.toMap(),
                    outcome = outcome,
                    downloading = downloading,
                ),
            )
        }
        publish("Preparando...")

        val plan = ScrapPlanner.plan(previous, present, session.wanted, session.force, now, session.overwrite)
        val useScreenScraper = plan.needsScreenScraper && ScrapProvider.SCREENSCRAPER !in session.disabled
        val useSteamGridDb = plan.needsSteamGridDb && ScrapProvider.STEAMGRIDDB !in session.disabled
        if (!useScreenScraper && !useSteamGridDb) {
            state.settleUnknowns()
            publish(null, ScrapOutcome.SKIPPED)
            return GameResult.Done(ScrapOutcome.SKIPPED)
        }

        val stopReason = try {
            runProviders(game, assets, plan, useScreenScraper, useSteamGridDb, session, state) { step, media -> publish(step, downloading = media) }
        } finally {
            // Aunque se cancele o se corte el lote, lo averiguado hasta aquí se guarda: así la próxima
            // corrida no vuelve a preguntar lo mismo.
            withContext(NonCancellable) { persistMetadata(rom, assets, state) }
        }
        state.settleUnknowns()
        if (stopReason != null) {
            publish(null)
            return GameResult.Stop(stopReason)
        }

        val outcome = when {
            state.gotSomething || state.screenScraperKnowsGame == true -> ScrapOutcome.SCRAPED
            state.hadError -> ScrapOutcome.FAILED
            state.screenScraperKnowsGame == false -> ScrapOutcome.NOT_FOUND
            else -> ScrapOutcome.SKIPPED
        }
        publish(null, outcome)
        return GameResult.Done(outcome)
    }

    /** Pide a los proveedores lo que el plan indica. Devuelve el motivo si hay que cortar el lote, o null. */
    private suspend fun runProviders(
        game: GameEntry,
        assets: SystemAssets,
        plan: ScrapPlanner.Plan,
        useScreenScraper: Boolean,
        useSteamGridDb: Boolean,
        session: Session,
        state: GameState,
        publish: suspend (step: String, downloading: ScrapMedia?) -> Unit,
    ): String? {
        val rom = game.romFileName

        if (useScreenScraper) {
            publish("Consultando ScreenScraper...", null)
            when (val found = screenScraper.findGame(lookupFor(game))) {
                is ScrapResult.Ok -> {
                    session.connectionWorked()
                    val match = found.value
                    state.screenScraperKnowsGame = true
                    if (ScrapMedia.METADATA in session.wanted) state.metadataShown = true
                    state.metadata = MetadataMerge.found(
                        state.previous, match, game.systemId, state.now, includeDetails = ScrapMedia.METADATA in session.wanted,
                    )
                    state.canonicalName = ScreenScraperSelector.canonicalName(match.game) ?: state.canonicalName
                    state.year = ScreenScraperSelector.releaseYear(match.game) ?: state.year

                    val selection = ScreenScraperSelector.select(match.game, plan.fetch.intersect(ScrapPlanner.SCREENSCRAPER_MEDIA))
                    for ((media, chosen) in selection) {
                        if (chosen == null) {
                            state.newlyMissing += media
                            state.mark(media, false)
                            continue
                        }
                        publish("Descargando ${media.label.lowercase()}...", media)
                        when (val saved = saveScreenScraperMedia(assets, rom, media, chosen, session.overwrite)) {
                            is ScrapResult.Ok -> {
                                state.obtained += media
                                state.gotSomething = true
                                state.mark(media, true)
                                regionKey(media)?.let { key -> chosen.region?.let { state.regions[key] = it } }
                            }
                            ScrapResult.NotFound -> {
                                state.newlyMissing += media
                                state.mark(media, false)
                            }
                            is ScrapResult.Failure -> {
                                state.hadError = true
                                state.mark(media, false)
                                failureStop(saved, ScrapProvider.SCREENSCRAPER, session)?.let { return it }
                            }
                        }
                    }
                }
                ScrapResult.NotFound -> {
                    session.connectionWorked()
                    state.screenScraperKnowsGame = false
                    state.metadata = MetadataMerge.notFound(state.previous, state.now)
                    val missing = plan.fetch.intersect(ScrapPlanner.SCREENSCRAPER_MEDIA)
                    state.newlyMissing += missing
                    missing.forEach { state.mark(it, false) }
                    if (ScrapMedia.METADATA in session.wanted) state.metadataShown = state.previous?.hasDetails == true
                }
                is ScrapResult.Failure -> {
                    state.hadError = true
                    failureStop(found, ScrapProvider.SCREENSCRAPER, session)?.let { return it }
                }
            }
        }

        if (useSteamGridDb) {
            // El nombre estándar de ScreenScraper busca mucho mejor que el del archivo.
            val name = state.canonicalName ?: game.displayName
            publish("Buscando el ícono en SteamGridDB...", null)
            when (val found = steamGridDb.findIcon(name, state.year)) {
                is ScrapResult.Ok -> {
                    session.connectionWorked()
                    val match = found.value
                    publish("Descargando ícono...", ScrapMedia.ICON)
                    when (val saved = saveIcon(assets, rom, match.image, session.overwrite)) {
                        is ScrapResult.Ok -> {
                            state.obtained += ScrapMedia.ICON
                            state.gotSomething = true
                            state.mark(ScrapMedia.ICON, true)
                            state.metadata = MetadataMerge.withIcon(
                                state.metadata ?: MetadataMerge.pending(state.previous, state.now), match.game.id, match.image.id,
                            )
                            gameIndex.updateIcon(game, saved.value)
                        }
                        ScrapResult.NotFound -> {
                            state.newlyMissing += ScrapMedia.ICON
                            state.mark(ScrapMedia.ICON, false)
                        }
                        is ScrapResult.Failure -> {
                            state.hadError = true
                            state.mark(ScrapMedia.ICON, false)
                            failureStop(saved, ScrapProvider.STEAMGRIDDB, session)?.let { return it }
                        }
                    }
                }
                ScrapResult.NotFound -> {
                    session.connectionWorked()
                    state.newlyMissing += ScrapMedia.ICON
                    state.mark(ScrapMedia.ICON, false)
                }
                is ScrapResult.Failure -> {
                    state.hadError = true
                    state.mark(ScrapMedia.ICON, false)
                    failureStop(found, ScrapProvider.STEAMGRIDDB, session)?.let { return it }
                }
            }
        }
        return null
    }

    /** Guarda lo averiguado de un juego en `metadata/`, si hay algo nuevo que guardar. */
    private suspend fun persistMetadata(rom: String, assets: SystemAssets, state: GameState) {
        if (state.metadata == null && state.obtained.isEmpty() && state.newlyMissing.isEmpty()) return
        val start = state.metadata ?: MetadataMerge.pending(state.previous, state.now)
        val final = MetadataMerge.withOutcome(start, state.obtained, state.newlyMissing, state.regions)
        if (final != state.previous) assets.writeMetadata(rom, final)
    }

    // ── Descargas ─────────────────────────────────────────────────────────────────────────────

    private suspend fun saveScreenScraperMedia(
        assets: SystemAssets,
        rom: String,
        media: ScrapMedia,
        chosen: ScreenScraperMedia,
        overwrite: Boolean,
    ): ScrapResult<Uri> =
        assets.write(AssetFolder.of(media), rom, chosen.format, overwrite) { out -> screenScraper.download(chosen, out) }

    /** Descarga el ícono, lo deja como PNG cuadrado de hasta 512 y lo guarda. */
    private suspend fun saveIcon(assets: SystemAssets, rom: String, image: SteamGridDbImage, overwrite: Boolean): ScrapResult<Uri> {
        val buffer = ByteArrayOutputStream()
        when (val downloaded = steamGridDb.download(image, buffer)) {
            is ScrapResult.Failure -> return downloaded
            is ScrapResult.Ok -> Unit
        }
        val png = when (val processed = withContext(Dispatchers.Default) { IconProcessor.toSquarePng(buffer.toByteArray()) }) {
            is ScrapResult.Failure -> return processed
            is ScrapResult.Ok -> processed.value
        }
        return assets.write(AssetFolder.ICONS, rom, "png", overwrite) { out ->
            out.write(png)
            ScrapResult.Ok(png.size.toLong())
        }
    }

    /** La clave de `GameMetadata.regions` para las imágenes que tienen región. */
    private fun regionKey(media: ScrapMedia): String? = when (media) {
        ScrapMedia.WHEEL -> "wheel"
        ScrapMedia.BOX_FRONT -> "boxFront"
        ScrapMedia.BOX_BACK -> "boxBack"
        ScrapMedia.BOX_SPINE -> "boxSpine"
        else -> null
    }

    // ── Identificar el archivo ────────────────────────────────────────────────────────────────

    /** Datos del archivo para ScreenScraper: tamaño y, si es chico y no comprimido, sus hashes. */
    private suspend fun lookupFor(game: GameEntry): RomLookup = withContext(Dispatchers.IO) {
        val isFolder = SystemCatalog.byId.getValue(game.systemId).romOrganization == RomOrganization.FOLDER_PER_GAME
        // En los sistemas de carpeta por juego, romUri es solo el archivo base: su tamaño no es el del juego.
        val size = if (isFolder) null else runCatching { DocumentFile.fromSingleUri(context, game.romUri)?.length() }
            .getOrNull()?.takeIf { it > 0 }

        val hashes = if (ScrapPlanner.shouldHash(game.systemId, game.romFileName, size, isFolder)) {
            runCatching { context.contentResolver.openInputStream(game.romUri)?.use { RomHasher.compute(it) } }.getOrNull()
        } else {
            null
        }
        RomLookup(
            systemId = game.systemId,
            romName = game.romFileName,
            displayName = game.displayName,
            sizeBytes = size,
            crc32 = hashes?.crc32,
            md5 = hashes?.md5,
            sha1 = hashes?.sha1,
        )
    }

    // ── Qué hacer ante un fallo ───────────────────────────────────────────────────────────────

    /**
     * Decide qué implica [failure] para el lote. Devuelve el motivo si hay que **cortarlo**, o null si
     * sigue. Credenciales rechazadas o cupo agotado: ese proveedor se deja de usar y el lote sigue con
     * el otro (corta solo si no queda ninguno).
     */
    private fun failureStop(failure: ScrapResult.Failure, provider: ScrapProvider, session: Session): String? =
        when (failure) {
            is ScrapResult.StorageFailed -> if (failure.fatal) failure.message else null
            is ScrapResult.NetworkBlocked -> failure.message
            is ScrapResult.AuthFailed, is ScrapResult.QuotaExhausted -> {
                session.disable(provider, failure.message)
                if (session.disabled.size >= ScrapProvider.entries.size) {
                    "Ya no queda ningún proveedor disponible. ${session.warnings.joinToString(" ")}"
                } else {
                    null
                }
            }
            is ScrapResult.Unavailable, is ScrapResult.RateLimited -> {
                session.consecutiveFailures++
                if (session.consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    "La conexión con ${provider.label} falla una y otra vez. Se detuvo el lote."
                } else {
                    null
                }
            }
            ScrapResult.NotFound -> null
        }

    // ── Estado interno ────────────────────────────────────────────────────────────────────────

    /** Estado de todo el lote. */
    private class Session(val wanted: Set<ScrapMedia>, val force: Boolean, val overwrite: Boolean) {
        val disabled = mutableSetOf<ScrapProvider>()
        val warnings = mutableListOf<String>()
        var consecutiveFailures = 0

        fun disable(provider: ScrapProvider, reason: String) {
            if (disabled.add(provider)) warnings += reason
        }

        fun connectionWorked() {
            consecutiveFailures = 0
        }
    }

    /** Lo que se va averiguando de un juego mientras se lo procesa. */
    private class GameState(val previous: GameMetadata?, val now: Long, val present: Set<ScrapMedia>) {
        // Lo que ve el usuario en el modal de avance (true = lo tiene, false = no, null = aún no se sabe).
        var metadataShown: Boolean? = null
        val mediaShown = LinkedHashMap<ScrapMedia, Boolean?>()

        /** Una media consultada: tiene "sí" si se consiguió o si ya había un archivo de antes. */
        fun mark(media: ScrapMedia, obtained: Boolean) {
            if (media in mediaShown) mediaShown[media] = obtained || media in present
        }

        /** Al terminar el juego, lo que no se llegó a consultar queda según lo que ya tenía guardado. */
        fun settleUnknowns() {
            for (media in mediaShown.keys.toList()) if (mediaShown[media] == null) mediaShown[media] = media in present
            if (metadataShown == null) metadataShown = previous?.hasDetails == true
        }

        var metadata: GameMetadata? = null
        val obtained = mutableSetOf<ScrapMedia>()
        val newlyMissing = mutableSetOf<ScrapMedia>()
        val regions = mutableMapOf<String, String>()
        var gotSomething = false
        var hadError = false

        /** null = no se consultó ScreenScraper para este juego. */
        var screenScraperKnowsGame: Boolean? = null
        var canonicalName: String? = previous?.name
        var year: Int? = previous?.releaseYear
    }

    private sealed interface GameResult {
        data class Done(val outcome: ScrapOutcome) : GameResult
        data class Stop(val reason: String) : GameResult
    }

    companion object {
        /** Fallos de conexión seguidos (en cualquier proveedor) tras los cuales se corta el lote. */
        const val MAX_CONSECUTIVE_FAILURES = 5

        /**
         * Duración mínima de cada juego con el modal de avance abierto ([paceForUi]): el fundido de salida y el de entrada
         * de la caja de datos (2 × `BOX_FADE_MS` = 360 ms, en ScrapProgressModal) más un rato con el resultado a la vista.
         */
        const val UI_MIN_GAME_MS = 700L
    }
}
