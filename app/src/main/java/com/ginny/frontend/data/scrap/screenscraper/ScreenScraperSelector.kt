package com.ginny.frontend.data.scrap.screenscraper

/**
 * Elige, de lo que ScreenScraper sabe de un juego, lo que Ginny va a usar. Código puro.
 *
 * Política (decidida con el usuario, 2026-10-07):
 * - **Región** de las imágenes: `wor → eu → us → jp → la primera que haya`.
 * - **Idioma** de la sinopsis y los géneros: `es → en → el primero que haya`.
 * - **Formato**: imágenes en PNG/JPEG/WebP (lo que Android decodifica sin problema) y video en MP4.
 * - Las **tres caras de la caja** deben ser de la misma región/edición (van a ser la textura de una
 *   misma caja 3D): ver [pickBox].
 */
object ScreenScraperSelector {

    val REGION_PRIORITY = listOf("wor", "eu", "us", "jp")
    val LANGUAGE_PRIORITY = listOf("es", "en")

    private val IMAGE_FORMATS = setOf("png", "jpg", "jpeg", "webp")
    private const val VIDEO_FORMAT = "mp4"

    // ── Medias ────────────────────────────────────────────────────────────────────────────────

    /** Tipos de media de ScreenScraper que usa Ginny. */
    object Type {
        const val WHEEL = "wheel"
        const val BOX_FRONT = "box-2D"
        const val BOX_BACK = "box-2D-back"
        const val BOX_SPINE = "box-2D-side"
        const val VIDEO_NORMALIZED = "video-normalized"
        const val VIDEO = "video"
    }

    /** El wheel (logo del juego) de la región preferida. */
    fun pickWheel(game: ScreenScraperGame): ScreenScraperMedia? =
        pickByRegion(images(game, Type.WHEEL))

    /**
     * El video: `video-normalized` (ScreenScraper lo recodifica a un MP4 estándar, de decodificación
     * por hardware, para que se reproduzca fluido); el `video` original solo como respaldo y solo si
     * ya es MP4. Los videos no tienen región.
     */
    fun pickVideo(game: ScreenScraperGame): ScreenScraperMedia? =
        game.medias.firstOrNull { it.type == Type.VIDEO_NORMALIZED && it.format == VIDEO_FORMAT }
            ?: game.medias.firstOrNull { it.type == Type.VIDEO && it.format == VIDEO_FORMAT }

    /** Las tres caras de una caja; cualquiera puede faltar. */
    data class BoxSelection(
        val front: ScreenScraperMedia?,
        val back: ScreenScraperMedia?,
        val spine: ScreenScraperMedia?,
    ) {
        val isEmpty: Boolean get() = front == null && back == null && spine == null
    }

    /**
     * Elige la caja: la **primera región, por orden de prioridad, que tenga las tres caras**; si
     * ninguna las tiene, la primera que tenga el frente, y las caras que falten se buscan en las
     * demás regiones por orden de prioridad. Así, siempre que exista una edición completa, frente,
     * trasera y lomo salen de la misma, aunque la región preferida solo tenga el frente.
     */
    fun pickBox(game: ScreenScraperGame): BoxSelection {
        val fronts = images(game, Type.BOX_FRONT)
        val backs = images(game, Type.BOX_BACK)
        val spines = images(game, Type.BOX_SPINE)

        val regions = orderedRegions((fronts + backs + spines).map { it.region })
        val region = regions.firstOrNull { r -> fronts.hasRegion(r) && backs.hasRegion(r) && spines.hasRegion(r) }
            ?: regions.firstOrNull { r -> fronts.hasRegion(r) }
            ?: regions.firstOrNull()

        fun face(options: List<ScreenScraperMedia>): ScreenScraperMedia? =
            options.firstOrNull { it.region == region } ?: pickByRegion(options)

        return BoxSelection(front = face(fronts), back = face(backs), spine = face(spines))
    }

    // ── Textos ────────────────────────────────────────────────────────────────────────────────

    /**
     * Nombre estándar del juego: el de ScreenScraper (`ss`), si no el de las regiones por prioridad.
     * Es el que se usa para buscar el juego en SteamGridDB.
     */
    fun canonicalName(game: ScreenScraperGame): String? =
        game.names["ss"] ?: pickByPriority(REGION_PRIORITY, game.names)

    /** Todos los nombres del juego (para comparar con lo que devuelve una búsqueda). */
    fun allNames(game: ScreenScraperGame): List<String> = game.names.values.distinct()

    fun pickSynopsis(game: ScreenScraperGame): String? = pickByPriority(LANGUAGE_PRIORITY, game.synopsis)

    fun pickGenres(game: ScreenScraperGame): List<String> =
        pickByPriority(LANGUAGE_PRIORITY, game.genres).orEmpty()

    /**
     * Fecha de **primera publicación**: la más antigua de todas las regiones (Super Mario Bros. es de
     * 1985 aunque en Europa saliera en 1987). Las fechas son `AAAA-MM-DD` o solo `AAAA`, así que el
     * orden alfabético es el cronológico.
     */
    fun firstReleaseDate(game: ScreenScraperGame): String? = game.releaseDates.values.minOrNull()

    fun releaseYear(game: ScreenScraperGame): Int? = firstReleaseDate(game)?.take(4)?.toIntOrNull()

    // ── Mecánica común ────────────────────────────────────────────────────────────────────────

    private fun images(game: ScreenScraperGame, type: String): List<ScreenScraperMedia> =
        game.medias.filter { it.type == type && it.format in IMAGE_FORMATS }

    private fun List<ScreenScraperMedia>.hasRegion(region: String?) = any { it.region == region }

    /** La imagen de la primera región por prioridad; si ninguna coincide, la primera de la lista. */
    private fun pickByRegion(options: List<ScreenScraperMedia>): ScreenScraperMedia? =
        REGION_PRIORITY.firstNotNullOfOrNull { region -> options.firstOrNull { it.region == region } }
            ?: options.firstOrNull()

    /** Regiones presentes, primero las de [REGION_PRIORITY] en orden y luego el resto como llegaron. */
    private fun orderedRegions(regions: List<String?>): List<String?> {
        val present = regions.distinct()
        return REGION_PRIORITY.filter { it in present } + present.filter { it !in REGION_PRIORITY }
    }

    /** El valor de la primera clave de [priority] que exista en [map]; si no, el primero del mapa. */
    private fun <T> pickByPriority(priority: List<String>, map: Map<String, T>): T? =
        priority.firstNotNullOfOrNull { map[it] } ?: map.values.firstOrNull()
}
