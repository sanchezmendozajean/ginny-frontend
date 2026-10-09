package com.ginny.frontend.data.scrap

/** Los servicios de los que el scrapper obtiene datos. */
enum class ScrapProvider(val label: String) {
    SCREENSCRAPER("ScreenScraper"),
    STEAMGRIDDB("SteamGridDB"),
}

/**
 * Resultado de una operación del scrapper: o salió bien ([Ok]) o es uno de los [Failure] tipados.
 * Los clientes no lanzan excepciones por lo que es normal que pase (no hay coincidencia, se acabó el
 * cupo, no hay red…): lo devuelven, y quien orquesta decide qué hacer (seguir con el juego
 * siguiente, esperar o cortar el lote).
 */
sealed interface ScrapResult<out T> {

    data class Ok<out T>(val value: T) : ScrapResult<T>

    sealed interface Failure : ScrapResult<Nothing> {
        /** Texto para mostrar al usuario. Nunca incluye URLs ni credenciales. */
        val message: String

        /** true = no tiene sentido seguir con el resto del lote (credenciales o cupo). */
        val abortsBatch: Boolean get() = false
    }

    /** El servicio respondió bien pero no conoce el juego (o la coincidencia no es fiable). */
    data object NotFound : Failure {
        override val message = "No se encontró el juego."
    }

    /** Credenciales rechazadas o ausentes. */
    data class AuthFailed(val provider: ScrapProvider) : Failure {
        override val message = "${provider.label} rechazó las credenciales. Revísalas en Configuración > Scrapper."
        override val abortsBatch get() = true
    }

    /** Se agotó el cupo diario (ScreenScraper, HTTP 430 / 431). */
    data class QuotaExhausted(val provider: ScrapProvider) : Failure {
        override val message = "Se agotó el cupo diario de ${provider.label}. Inténtalo mañana."
        override val abortsBatch get() = true
    }

    /** Demasiadas peticiones por minuto (HTTP 429): conviene esperar y reintentar. */
    data class RateLimited(val provider: ScrapProvider) : Failure {
        override val message = "${provider.label} pide ir más despacio."
    }

    /** Sin red, tiempo de espera agotado, error del servidor o respuesta ilegible. */
    data class Unavailable(val provider: ScrapProvider, val detail: String) : Failure {
        override val message = "${provider.label} no está disponible ($detail)."
    }

    /** El usuario no tiene permitido descargar ahora (sin red, o datos móviles con "Solo con Wi-Fi"). */
    data class NetworkBlocked(val reason: String) : Failure {
        override val message = reason
        override val abortsBatch get() = true
    }
}

inline fun <T, R> ScrapResult<T>.map(transform: (T) -> R): ScrapResult<R> = when (this) {
    is ScrapResult.Ok -> ScrapResult.Ok(transform(value))
    is ScrapResult.Failure -> this
}

fun <T> ScrapResult<T>.getOrNull(): T? = (this as? ScrapResult.Ok)?.value
