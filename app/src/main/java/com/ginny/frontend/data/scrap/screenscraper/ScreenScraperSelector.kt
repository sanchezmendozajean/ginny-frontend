package com.ginny.frontend.data.scrap.screenscraper

import com.ginny.frontend.data.scrap.NameNormalizer
import com.ginny.frontend.data.scrap.ScrapMedia

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

    /**
     * La media de ScreenScraper que corresponde a cada tipo de [wanted] (null = ScreenScraper no la
     * tiene para este juego). Solo considera los tipos que salen de ScreenScraper; el ícono y los
     * metadatos no figuran. La caja se elige una sola vez para que sus tres caras sean de la misma edición.
     */
    fun select(game: ScreenScraperGame, wanted: Set<ScrapMedia>): Map<ScrapMedia, ScreenScraperMedia?> {
        val box = if (wanted.any { it in BOX_MEDIA }) pickBox(game) else null
        val result = LinkedHashMap<ScrapMedia, ScreenScraperMedia?>()
        for (media in ScrapMedia.entries) {
            if (media !in wanted) continue
            when (media) {
                ScrapMedia.WHEEL -> result[media] = pickWheel(game)
                ScrapMedia.BOX_FRONT -> result[media] = box?.front
                ScrapMedia.BOX_BACK -> result[media] = box?.back
                ScrapMedia.BOX_SPINE -> result[media] = box?.spine
                ScrapMedia.VIDEO -> result[media] = pickVideo(game)
                ScrapMedia.METADATA, ScrapMedia.ICON -> Unit
            }
        }
        return result
    }

    private val BOX_MEDIA = setOf(ScrapMedia.BOX_FRONT, ScrapMedia.BOX_BACK, ScrapMedia.BOX_SPINE)

    // ── Textos ────────────────────────────────────────────────────────────────────────────────

    /**
     * Nombre estándar del juego: el de ScreenScraper (`ss`), si no el de las regiones por prioridad.
     * Es el que se usa para buscar el juego en SteamGridDB.
     */
    fun canonicalName(game: ScreenScraperGame): String? =
        game.names["ss"] ?: pickByPriority(REGION_PRIORITY, game.names)

    /** Parecido mínimo entre el nombre del archivo y el del juego para fiarse de una coincidencia solo por nombre de archivo. */
    const val MIN_FILE_MATCH_SIMILARITY = 0.65

    /**
     * ¿Tiene sentido que ScreenScraper haya asociado el archivo [romName] (con su [displayName]) al juego
     * [game]? Una coincidencia por **hash** es segura, pero por **nombre de archivo** puede estar mal en su
     * base de datos (p. ej. "Twilight Princess HD.wua" asociado a "The Wind Waker HD"). Los nombres con
     * espacios son títulos de verdad y deben parecerse a alguno de los del juego; los cortos (arcade:
     * `sf2ce.zip`) no se parecen a nada y se aceptan.
     */
    fun fileMatchPlausible(game: ScreenScraperGame, romName: String, displayName: String): Boolean {
        val stem = romName.substringBeforeLast('.', romName)
        if (!stem.trim().contains(' ')) return true
        val names = allNames(game)
        if (names.isEmpty()) return true
        return names.any { name ->
            maxOf(NameNormalizer.similarity(name, stem), NameNormalizer.similarity(name, displayName)) >= MIN_FILE_MATCH_SIMILARITY
        }
    }

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
