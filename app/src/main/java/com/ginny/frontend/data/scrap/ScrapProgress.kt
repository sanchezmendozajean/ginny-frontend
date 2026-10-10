package com.ginny.frontend.data.scrap

/** Cómo terminó el scrapeo de un juego. */
enum class ScrapOutcome {
    /** Se guardó algo nuevo (o ScreenScraper reconoció el juego). */
    SCRAPED,

    /** ScreenScraper no conoce el juego y no se consiguió nada más. */
    NOT_FOUND,

    /** No había nada que pedir: ya estaba todo, o se sabe que el proveedor no lo tiene. */
    SKIPPED,

    /** Falló algo transitorio (red, límite de ritmo, escritura) y no se consiguió nada. */
    FAILED,
}

/**
 * Estado de un lote de scrapeo, para mostrárselo al usuario. [processed] cuenta los juegos ya vistos
 * (incluidos los saltados), así que `processed / total` es el avance real.
 */
data class ScrapProgress(
    val total: Int,
    val processed: Int = 0,
    /** Nombre del juego que se está procesando. */
    val current: String? = null,
    val scraped: Int = 0,
    val notFound: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    /** Proveedores que se dejaron de usar durante el lote (credenciales o cupo), con su motivo. */
    val warnings: List<String> = emptyList(),
    /** Por qué se cortó el lote antes de terminar; null si no se cortó. */
    val stopReason: String? = null,
    val finished: Boolean = false,
) {
    fun withCurrent(name: String) = copy(current = name)

    fun after(outcome: ScrapOutcome) = copy(
        processed = processed + 1,
        scraped = scraped + if (outcome == ScrapOutcome.SCRAPED) 1 else 0,
        notFound = notFound + if (outcome == ScrapOutcome.NOT_FOUND) 1 else 0,
        skipped = skipped + if (outcome == ScrapOutcome.SKIPPED) 1 else 0,
        failed = failed + if (outcome == ScrapOutcome.FAILED) 1 else 0,
    )

    fun stopped(reason: String) = copy(current = null, stopReason = reason, finished = true)

    fun completed() = copy(current = null, finished = true)
}
