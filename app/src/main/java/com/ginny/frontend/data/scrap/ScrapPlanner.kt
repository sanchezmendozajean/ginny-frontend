package com.ginny.frontend.data.scrap

import com.ginny.frontend.data.model.SystemId

/**
 * Decide, para un juego, **qué falta por pedir** y a quién, mirando lo que ya se guardó. Código puro:
 * es lo que hace que el scrapper sea reanudable y no repita peticiones (cada una cuesta ~5 s y cupo).
 *
 * Reglas:
 * - **Nunca se vuelve a pedir lo que ya está** guardado, ni lo que el proveedor ya dijo que no tiene
 *   ([GameMetadata.missingMedia]), salvo al **forzar**.
 * - Un juego que ScreenScraper **no conoce** ([GameMetadata.Status.NOT_FOUND]) no se vuelve a consultar
 *   hasta pasados [NOT_FOUND_RETRY_MS] (o al forzar).
 * - El nombre estándar de ScreenScraper mejora la búsqueda en SteamGridDB, así que si todavía no se
 *   consultó y hace falta el ícono, se pregunta primero a ScreenScraper.
 * - Forzar **no sobrescribe** archivos existentes: solo ignora las cachés negativas.
 */
object ScrapPlanner {

    /** Cuánto dura la caché negativa de "ScreenScraper no conoce este juego": 30 días. */
    const val NOT_FOUND_RETRY_MS = 30L * 24 * 60 * 60 * 1000

    /** Las medias que salen de ScreenScraper (el ícono sale de SteamGridDB). */
    val SCREENSCRAPER_MEDIA = setOf(
        ScrapMedia.WHEEL, ScrapMedia.BOX_FRONT, ScrapMedia.BOX_BACK, ScrapMedia.BOX_SPINE, ScrapMedia.VIDEO,
    )

    data class Plan(
        /** Medias (sin [ScrapMedia.METADATA]) que hay que conseguir. */
        val fetch: Set<ScrapMedia>,
        val needsScreenScraper: Boolean,
        val needsSteamGridDb: Boolean,
    ) {
        val isEmpty: Boolean get() = !needsScreenScraper && !needsSteamGridDb
    }

    /**
     * @param previous metadatos guardados de este juego, o null si no hay.
     * @param present medias que ya están guardadas como archivo.
     * @param wanted lo que el usuario eligió descargar.
     */
    fun plan(
        previous: GameMetadata?,
        present: Set<ScrapMedia>,
        wanted: Set<ScrapMedia>,
        force: Boolean,
        now: Long,
    ): Plan {
        val neverAsked = previous == null || previous.status == GameMetadata.Status.PENDING
        val negativeIsFresh = previous?.status == GameMetadata.Status.NOT_FOUND &&
            !force && now - previous.scrapedAt < NOT_FOUND_RETRY_MS

        val missing = previous?.missingMedia.orEmpty()
        val toFetch = wanted
            .filter { it != ScrapMedia.METADATA && it !in present && (force || it !in missing) }
            .toSet()
            .let { all -> if (negativeIsFresh) all - SCREENSCRAPER_MEDIA else all }

        val needsSteamGridDb = ScrapMedia.ICON in toFetch
        val refreshDetails = ScrapMedia.METADATA in wanted && (
            neverAsked ||
                (previous?.status == GameMetadata.Status.NOT_FOUND && !negativeIsFresh) ||
                (previous?.status == GameMetadata.Status.FOUND && !previous.hasDetails)
            )
        val needsNameForSteamGridDb = needsSteamGridDb && neverAsked

        val needsScreenScraper = !negativeIsFresh &&
            (toFetch.any { it in SCREENSCRAPER_MEDIA } || refreshDetails || needsNameForSteamGridDb)

        return Plan(fetch = toFetch, needsScreenScraper = needsScreenScraper, needsSteamGridDb = needsSteamGridDb)
    }

    // ── Hash del archivo ──────────────────────────────────────────────────────────────────────

    /** Por encima de este tamaño no se calcula el hash: leer una ISO entera por SAF tardaría mucho. */
    const val MAX_HASH_BYTES = 32L * 1024 * 1024

    private val ARCADE_SYSTEMS = setOf(
        SystemId.MAME, SystemId.FBNEO, SystemId.NEOGEO, SystemId.NAOMI, SystemId.ATOMISWAVE,
    )
    private val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar")

    /**
     * Si conviene mandar el hash del archivo a ScreenScraper. No en los sistemas arcade ni en archivos
     * comprimidos (el hash del zip no es el de la ROM; ahí se identifica por nombre), ni en carpetas
     * (Switch), ni si no se conoce el tamaño o supera [MAX_HASH_BYTES].
     */
    fun shouldHash(systemId: SystemId, fileName: String, sizeBytes: Long?, isFolder: Boolean): Boolean {
        if (isFolder || sizeBytes == null || sizeBytes <= 0 || sizeBytes > MAX_HASH_BYTES) return false
        if (systemId in ARCADE_SYSTEMS) return false
        return fileName.substringAfterLast('.', "").lowercase() !in ARCHIVE_EXTENSIONS
    }
}
