package com.ginny.frontend.data.scrap.screenscraper

/**
 * Lo que ScreenScraper sabe de un juego, ya leído del JSON y sin elegir todavía nada: los textos y
 * las imágenes vienen **por región o idioma**, y [ScreenScraperSelector] decide cuál usar.
 *
 * Los mapas conservan el orden en que llegaron y, si una clave se repite, se queda con la primera.
 */
data class ScreenScraperGame(
    /** Identificador del juego en ScreenScraper (`jeu.id`). */
    val id: Long,
    /** `jeu.systeme.id`. */
    val systemId: Int?,
    val systemName: String?,
    /** Región → nombre (`ss` es el nombre estándar de ScreenScraper, sin región). */
    val names: Map<String, String>,
    /** Idioma → sinopsis. */
    val synopsis: Map<String, String>,
    /** Región → fecha (`1985-11-17`, a veces solo el año). */
    val releaseDates: Map<String, String>,
    val developer: String?,
    val publisher: String?,
    /** Texto tal cual lo da la API: `1`, `1-2`… */
    val players: String?,
    /** Nota de 0.0 a 1.0 (ScreenScraper la da sobre 20). */
    val rating: Double?,
    /** Idioma → nombres de género (principal y subgéneros). */
    val genres: Map<String, List<String>>,
    val medias: List<ScreenScraperMedia>,
)

/** Una imagen, un video, etc. de [ScreenScraperGame.medias]. */
data class ScreenScraperMedia(
    /** `wheel`, `box-2D`, `box-2D-back`, `box-2D-side`, `video-normalized`, `video`… */
    val type: String,
    /** `wor`, `eu`, `us`, `jp`…; null en los que no tienen región (los videos). */
    val region: String?,
    /** Extensión del archivo: `png`, `jpg`, `mp4`… */
    val format: String,
    val sizeBytes: Long?,
    /** **Lleva las credenciales incrustadas**: no se guarda ni se muestra (ver `ScrapRedactor`). */
    val url: String,
)

/** Cómo se llegó al juego: indica cuánta confianza merece la coincidencia. */
enum class MatchedBy {
    /** Por el hash (CRC/MD5/SHA1) del archivo: la más fiable. */
    HASH,

    /** Por el nombre exacto del archivo y su tamaño. */
    FILE_NAME,

    /** Búsqueda por el nombre del juego: la menos fiable (se exige un parecido alto). */
    SEARCH,
}

data class ScreenScraperMatch(val game: ScreenScraperGame, val matchedBy: MatchedBy)

/** Datos de la cuenta que devuelve `ssuserInfos.php`. */
data class ScreenScraperUser(
    val userName: String?,
    val maxThreads: Int?,
    val requestsToday: Int?,
    val maxRequestsPerDay: Int?,
    val failedRequestsToday: Int?,
    val maxFailedRequestsPerDay: Int?,
)
