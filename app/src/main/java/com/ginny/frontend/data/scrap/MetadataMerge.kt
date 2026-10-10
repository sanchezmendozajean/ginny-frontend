package com.ginny.frontend.data.scrap

import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.data.scrap.screenscraper.ScreenScraperMatch
import com.ginny.frontend.data.scrap.screenscraper.ScreenScraperSelector

/**
 * Arma el [GameMetadata] nuevo a partir del anterior y de lo que se acaba de averiguar. Código puro.
 * Parte siempre de lo que ya había guardado: lo que no se vuelve a consultar no se pierde.
 */
object MetadataMerge {

    /**
     * ScreenScraper conoce el juego. [includeDetails] = el usuario eligió descargar los metadatos
     * (descripción, fechas, estudio...); si no, solo se guarda lo que identifica al juego.
     */
    fun found(
        previous: GameMetadata?,
        match: ScreenScraperMatch,
        systemId: SystemId,
        now: Long,
        includeDetails: Boolean,
    ): GameMetadata {
        val game = match.game
        val identified = (previous ?: GameMetadata(GameMetadata.Status.FOUND, now)).copy(
            status = GameMetadata.Status.FOUND,
            scrapedAt = now,
            matchedBy = match.matchedBy,
            screenScraperGameId = game.id,
            screenScraperSystemId = game.systemId,
            name = ScreenScraperSelector.canonicalName(game),
            system = systemId.name,
        )
        if (!includeDetails) return identified
        return identified.copy(
            synopsis = ScreenScraperSelector.pickSynopsis(game),
            releaseDate = ScreenScraperSelector.firstReleaseDate(game),
            releaseYear = ScreenScraperSelector.releaseYear(game),
            releaseDates = game.releaseDates,
            developer = game.developer,
            publisher = game.publisher,
            genres = ScreenScraperSelector.pickGenres(game),
            players = game.players,
            rating = game.rating,
        )
    }

    /** ScreenScraper no conoce el juego: caché negativa (conserva lo que hubiera de antes). */
    fun notFound(previous: GameMetadata?, now: Long): GameMetadata =
        (previous ?: GameMetadata(GameMetadata.Status.NOT_FOUND, now))
            .copy(status = GameMetadata.Status.NOT_FOUND, scrapedAt = now)

    /** Punto de partida cuando todavía no se consultó ScreenScraper (p. ej. solo se busca el ícono). */
    fun pending(previous: GameMetadata?, now: Long): GameMetadata =
        previous ?: GameMetadata(GameMetadata.Status.PENDING, now)

    /** Registra el resultado de las descargas: qué se consiguió, qué el proveedor no tiene y de qué región salió cada imagen. */
    fun withOutcome(
        base: GameMetadata,
        obtained: Set<ScrapMedia>,
        newlyMissing: Set<ScrapMedia>,
        regions: Map<String, String>,
    ): GameMetadata = base.copy(
        missingMedia = (base.missingMedia - obtained) + newlyMissing,
        regions = base.regions + regions,
    )

    fun withIcon(base: GameMetadata, steamGridDbGameId: Long, imageId: Long): GameMetadata =
        base.copy(steamGridDbGameId = steamGridDbGameId, steamGridDbImageId = imageId)
}
