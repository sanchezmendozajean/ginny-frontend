package com.ginny.frontend.data.scrap.screenscraper

import com.ginny.frontend.data.scrap.ScrapProvider
import com.ginny.frontend.data.scrap.ScrapResult

/** Traduce el código HTTP de una respuesta de ScreenScraper al resultado tipado del scrapper. */
object ScreenScraperErrors {

    private val provider = ScrapProvider.SCREENSCRAPER

    /**
     * null si el código es un éxito (200); si no, el [ScrapResult.Failure] que le corresponde.
     *
     * Códigos (documentación de la API y pruebas reales): 403 credenciales rechazadas; 404 juego/ROM
     * no encontrado; 429 demasiadas peticiones por minuto; 430 cupo diario agotado; 431 demasiados
     * intentos sin coincidencia; 401/423 API cerrada; 5xx error del servidor.
     */
    fun classify(httpCode: Int): ScrapResult.Failure? = when (httpCode) {
        200 -> null
        404 -> ScrapResult.NotFound
        403 -> ScrapResult.AuthFailed(provider)
        429 -> ScrapResult.RateLimited(provider)
        430, 431 -> ScrapResult.QuotaExhausted(provider)
        401, 423 -> ScrapResult.Unavailable(provider, "API cerrada")
        in 500..599 -> ScrapResult.Unavailable(provider, "error del servidor ($httpCode)")
        else -> ScrapResult.Unavailable(provider, "respuesta inesperada ($httpCode)")
    }
}
