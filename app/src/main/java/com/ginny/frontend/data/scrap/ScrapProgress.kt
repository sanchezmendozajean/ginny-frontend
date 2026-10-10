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
 * El juego que se está procesando (o el último, ya terminado), tal como lo muestra el modal de avance.
 *
 * [metadata] y cada valor de [media] son tres estados: `true` = el juego lo tiene (ya estaba o se
 * acaba de conseguir), `false` = no se consiguió, `null` = todavía no se sabe. Solo aparecen las medias
 * que el usuario eligió descargar.
 */
data class ScrapCurrent(
    /** Posición en el lote, desde 1. */
    val index: Int,
    val fileName: String,
    val systemName: String,
    /** Lo que se está haciendo ahora ("Consultando ScreenScraper…"); null cuando el juego ya terminó. */
    val step: String? = null,
    /** Si el usuario eligió descargar metadatos (si no, esa línea no se muestra). */
    val metadataWanted: Boolean = true,
    val metadata: Boolean? = null,
    val media: Map<ScrapMedia, Boolean?> = emptyMap(),
    /** null mientras se procesa. */
    val outcome: ScrapOutcome? = null,
) {
    /** Resumen de todas las imágenes (ícono, wheel, cajas): sí si las tiene todas, no si falta alguna. */
    val images: Boolean?
        get() {
            val values = media.filterKeys { it != ScrapMedia.VIDEO }.values
            return when {
                values.isEmpty() -> null
                values.any { it == false } -> false
                values.any { it == null } -> null
                else -> true
            }
        }
}

/**
 * Estado de un lote de scrapeo, para mostrárselo al usuario. [processed] cuenta los juegos ya vistos
 * (incluidos los saltados), así que `processed / total` es el avance real.
 */
data class ScrapProgress(
    val total: Int,
    val processed: Int = 0,
    /** El juego en curso (o el último procesado). */
    val current: ScrapCurrent? = null,
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
    fun withCurrent(current: ScrapCurrent) = copy(current = current)

    fun after(outcome: ScrapOutcome) = copy(
        processed = processed + 1,
        scraped = scraped + if (outcome == ScrapOutcome.SCRAPED) 1 else 0,
        notFound = notFound + if (outcome == ScrapOutcome.NOT_FOUND) 1 else 0,
        skipped = skipped + if (outcome == ScrapOutcome.SKIPPED) 1 else 0,
        failed = failed + if (outcome == ScrapOutcome.FAILED) 1 else 0,
    )

    fun stopped(reason: String) = copy(current = current?.copy(step = null), stopReason = reason, finished = true)

    fun completed() = copy(current = current?.copy(step = null), finished = true)
}
