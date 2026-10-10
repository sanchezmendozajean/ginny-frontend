package com.ginny.frontend.data.scrap.steamgriddb

import com.ginny.frontend.data.scrap.ScrapProvider
import com.ginny.frontend.data.scrap.ScrapResult

/** Traduce el código HTTP de una respuesta de SteamGridDB al resultado tipado del scrapper. */
object SteamGridDbErrors {

    private val provider = ScrapProvider.STEAMGRIDDB

    /**
     * null si el código es de éxito (200). Verificado con la API real: 401 clave inválida o ausente
     * (`Invalid key format`, `Authentication Required`) y 404 juego inexistente (`Game not found`).
     */
    fun classify(httpCode: Int): ScrapResult.Failure? = when (httpCode) {
        200 -> null
        401, 403 -> ScrapResult.AuthFailed(provider)
        404 -> ScrapResult.NotFound
        429 -> ScrapResult.RateLimited(provider)
        in 500..599 -> ScrapResult.Unavailable(provider, "error del servidor ($httpCode)")
        else -> ScrapResult.Unavailable(provider, "respuesta inesperada ($httpCode)")
    }
}
