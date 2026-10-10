package com.ginny.frontend.data.scrap

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializa las peticiones a un proveedor y deja al menos [minIntervalMs] entre el final de una y el
 * comienzo de la siguiente.
 *
 * Serializar no es opcional con ScreenScraper: la cuenta del usuario permite 1 hilo (`maxthreads`),
 * y pedir en paralelo devuelve error. Un limitador por proveedor, compartido por todo el lote.
 */
class ScrapRateLimiter(private val minIntervalMs: Long) {
    private val lock = Mutex()
    private var lastFinishedAtNanos = 0L

    /** Espera su turno, corre [block] y registra cuándo terminó. Las llamadas se atienden en orden. */
    suspend fun <T> throttled(block: suspend () -> T): T = lock.withLock {
        if (lastFinishedAtNanos != 0L) {
            val waitMs = minIntervalMs - (System.nanoTime() - lastFinishedAtNanos) / 1_000_000
            if (waitMs > 0) delay(waitMs)
        }
        try {
            block()
        } finally {
            lastFinishedAtNanos = System.nanoTime()
        }
    }

    /**
     * Ejecuta [block] y, si devuelve [ScrapResult.RateLimited] (HTTP 429), espera y reintenta hasta
     * [maxRetries] veces con espera creciente (2 s, 4 s, 8 s...). Si sigue limitado, devuelve ese
     * mismo resultado.
     */
    suspend fun <T> withBackoff(maxRetries: Int = 3, block: suspend () -> ScrapResult<T>): ScrapResult<T> {
        var attempt = 0
        while (true) {
            val result = throttled(block)
            if (result !is ScrapResult.RateLimited || attempt >= maxRetries) return result
            delay(2_000L shl attempt)
            attempt++
        }
    }
}
