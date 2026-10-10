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
     * Scrapea [games] en orden y va emitiendo el avance. El último estado emitido trae
     * [ScrapProgress.finished] = true (y [ScrapProgress.stopReason] si se cortó antes de terminar).
     * [force]: ignora las cachés negativas (juegos "no encontrados" y medias ausentes); **no**
     * sobrescribe archivos que ya existen.
     */
    fun run(games: List<GameEntry>, force: Boolean = false): Flow<ScrapProgress> = flow {
        val session = Session(wanted = scrapperSettings.enabledMedia.first(), force = force)
        val onlyOnWifi = scrapperSettings.onlyOnWifi.first()
        var progress = ScrapProgress(total = games.size)
        emit(progress)

        var openSystem: SystemId? = null
        var assets: SystemAssets? = null

        for (game in games) {
            currentCoroutineContext().ensureActive()

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

            progress = progress.withCurrent(game.displayName)
            emit(progress)

            when (val result = processGame(game, assets, session)) {
                is GameResult.Done -> progress = progress.after(result.outcome).copy(warnings = session.warnings.toList())
                is GameResult.Stop -> {
                    emit(progress.copy(warnings = session.warnings.toList()).stopped(result.reason))
                    return@flow
                }
            }
            emit(progress)
        }
        emit(progress.completed())
    }

    // ── Un juego ──────────────────────────────────────────────────────────────────────────────

    private suspend fun processGame(game: GameEntry, assets: SystemAssets, session: Session): GameResult {
        val rom = game.romFileName
        val now = clock()
        val previous = assets.readMetadata(rom)
        val present = ScrapMedia.entries
            .filter { it != ScrapMedia.METADATA && assets.has(AssetFolder.of(it), rom) }
            .toSet()

        val plan = ScrapPlanner.plan(previous, present, session.wanted, session.force, now)
        val useScreenScraper = plan.needsScreenScraper && ScrapProvider.SCREENSCRAPER !in session.disabled
        val useSteamGridDb = plan.needsSteamGridDb && ScrapProvider.STEAMGRIDDB !in session.disabled
        if (!useScreenScraper && !useSteamGridDb) return GameResult.Done(ScrapOutcome.SKIPPED)

        val state = GameState(previous, now)
        val stopReason = try {
            runProviders(game, assets, plan, useScreenScraper, useSteamGridDb, session, state)
        } finally {
            // Aunque se cancele o se corte el lote, lo averiguado hasta aquí se guarda: así la próxima
            // corrida no vuelve a preguntar lo mismo.
            withContext(NonCancellable) { persistMetadata(rom, assets, state) }
        }
        if (stopReason != null) return GameResult.Stop(stopReason)

        val outcome = when {
            state.gotSomething || state.screenScraperKnowsGame == true -> ScrapOutcome.SCRAPED
            state.hadError -> ScrapOutcome.FAILED
            state.screenScraperKnowsGame == false -> ScrapOutcome.NOT_FOUND
            else -> ScrapOutcome.SKIPPED
        }
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
    ): String? {
        val rom = game.romFileName

        if (useScreenScraper) {
            when (val found = screenScraper.findGame(lookupFor(game))) {
                is ScrapResult.Ok -> {
                    session.connectionWorked()
                    val match = found.value
                    state.screenScraperKnowsGame = true
                    state.metadata = MetadataMerge.found(
                        state.previous, match, game.systemId, state.now, includeDetails = ScrapMedia.METADATA in session.wanted,
                    )
                    state.canonicalName = ScreenScraperSelector.canonicalName(match.game) ?: state.canonicalName
                    state.year = ScreenScraperSelector.releaseYear(match.game) ?: state.year

                    val selection = ScreenScraperSelector.select(match.game, plan.fetch.intersect(ScrapPlanner.SCREENSCRAPER_MEDIA))
                    for ((media, chosen) in selection) {
                        if (chosen == null) {
                            state.newlyMissing += media
                            continue
                        }
                        when (val saved = saveScreenScraperMedia(assets, rom, media, chosen)) {
                            is ScrapResult.Ok -> {
                                state.obtained += media
                                state.gotSomething = true
                                regionKey(media)?.let { key -> chosen.region?.let { state.regions[key] = it } }
                            }
                            ScrapResult.NotFound -> state.newlyMissing += media
                            is ScrapResult.Failure -> {
                                state.hadError = true
                                failureStop(saved, ScrapProvider.SCREENSCRAPER, session)?.let { return it }
                            }
                        }
                    }
                }
                ScrapResult.NotFound -> {
                    session.connectionWorked()
                    state.screenScraperKnowsGame = false
                    state.metadata = MetadataMerge.notFound(state.previous, state.now)
                    state.newlyMissing += plan.fetch.intersect(ScrapPlanner.SCREENSCRAPER_MEDIA)
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
            when (val found = steamGridDb.findIcon(name, state.year)) {
                is ScrapResult.Ok -> {
                    session.connectionWorked()
                    val match = found.value
                    when (val saved = saveIcon(assets, rom, match.image)) {
                        is ScrapResult.Ok -> {
                            state.obtained += ScrapMedia.ICON
                            state.gotSomething = true
                            state.metadata = MetadataMerge.withIcon(
                                state.metadata ?: MetadataMerge.pending(state.previous, state.now), match.game.id, match.image.id,
                            )
                            gameIndex.updateIcon(game, saved.value)
                        }
                        ScrapResult.NotFound -> state.newlyMissing += ScrapMedia.ICON
                        is ScrapResult.Failure -> {
                            state.hadError = true
                            failureStop(saved, ScrapProvider.STEAMGRIDDB, session)?.let { return it }
                        }
                    }
                }
                ScrapResult.NotFound -> {
                    session.connectionWorked()
                    state.newlyMissing += ScrapMedia.ICON
                }
                is ScrapResult.Failure -> {
                    state.hadError = true
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
    ): ScrapResult<Uri> =
        assets.write(AssetFolder.of(media), rom, chosen.format) { out -> screenScraper.download(chosen, out) }

    /** Descarga el ícono, lo deja como PNG cuadrado de hasta 512 y lo guarda. */
    private suspend fun saveIcon(assets: SystemAssets, rom: String, image: SteamGridDbImage): ScrapResult<Uri> {
        val buffer = ByteArrayOutputStream()
        when (val downloaded = steamGridDb.download(image, buffer)) {
            is ScrapResult.Failure -> return downloaded
            is ScrapResult.Ok -> Unit
        }
        val png = when (val processed = withContext(Dispatchers.Default) { IconProcessor.toSquarePng(buffer.toByteArray()) }) {
            is ScrapResult.Failure -> return processed
            is ScrapResult.Ok -> processed.value
        }
        return assets.write(AssetFolder.ICONS, rom, "png") { out ->
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
    private class Session(val wanted: Set<ScrapMedia>, val force: Boolean) {
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
    private class GameState(val previous: GameMetadata?, val now: Long) {
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
    }
}
