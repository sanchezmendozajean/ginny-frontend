package com.ginny.frontend.data.scrap

import com.ginny.frontend.data.model.SystemId

/** Qué juegos de los sistemas elegidos se procesan en un lote. */
enum class ScrapScope(val label: String) {
    /** Los que todavía tienen algo por pedir. Reanuda lo que quedó a medias. */
    PENDING("Sólo pendientes"),

    /** Los que ya se consultaron y el proveedor no tenía (el juego entero, o alguna imagen). */
    NOT_FOUND("Sólo los no encontrados"),

    /** Todos. */
    ALL("Todos");

    /** Si ignora las cachés de "no encontrado" (ver [ScrapPlanner.plan]). */
    val force: Boolean get() = this != PENDING
}

/**
 * Cuánto hay por scrapear en un sistema, calculado con lo que ya hay guardado en `Assets/`. Los
 * conjuntos guardan el [com.ginny.frontend.data.model.GameEntry.romFileName] de cada juego.
 */
data class SystemScrapStats(
    val systemId: SystemId,
    val total: Int,
    /** Juegos con algo por pedir. */
    val pendingGames: Set<String>,
    /** Juegos sin nada pendiente pero que el proveedor no tenía, y que forzar volvería a consultar. */
    val notFoundGames: Set<String>,
) {
    val pending: Int get() = pendingGames.size
    val notFound: Int get() = notFoundGames.size

    /** Cuántos juegos procesaría un lote de este alcance. */
    fun countFor(scope: ScrapScope): Int = when (scope) {
        ScrapScope.PENDING -> pending
        ScrapScope.NOT_FOUND -> notFound
        ScrapScope.ALL -> total
    }

    /** Si el juego [romFileName] entra en un lote de este alcance. */
    fun includes(scope: ScrapScope, romFileName: String): Boolean = when (scope) {
        ScrapScope.PENDING -> romFileName in pendingGames
        ScrapScope.NOT_FOUND -> romFileName in notFoundGames
        ScrapScope.ALL -> true
    }
}
