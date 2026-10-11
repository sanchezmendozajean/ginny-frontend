package com.ginny.frontend.data.scrap.steamgriddb

import com.ginny.frontend.data.scrap.NameNormalizer
import com.ginny.frontend.data.scrap.ScrapHttp
import com.ginny.frontend.data.scrap.ScrapIo
import com.ginny.frontend.data.scrap.ScrapProvider
import com.ginny.frontend.data.scrap.ScrapRateLimiter
import com.ginny.frontend.data.scrap.ScrapResult
import com.ginny.frontend.data.scrap.ScrapperSettingsRepository
import com.ginny.frontend.data.scrap.getOrNull
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.OutputStream

/**
 * Cliente de la API v2 de SteamGridDB, para obtener el ícono cuadrado de un juego.
 *
 * Flujo de [findIcon]: busca el juego por nombre → se queda con los resultados que se llaman casi igual
 * ([SteamGridDbSelector.rankGames]) → para el mejor, pide sus *grids* cuadrados de 512×512 y 1024×1024 →
 * si no hay, sus *icons* → elige una imagen ([SteamGridDbSelector.pickImage]). Si el mejor candidato no
 * tiene ninguna imagen cuadrada, prueba con el siguiente (hasta [MAX_CANDIDATES]).
 *
 * Nada se lanza: todo es un [ScrapResult]. Sin probar por red dentro de la app todavía; el formato de
 * las respuestas y los códigos de error están verificados con peticiones reales.
 */
class SteamGridDbClient(
    private val settings: ScrapperSettingsRepository,
    private val limiter: ScrapRateLimiter = ScrapRateLimiter(minIntervalMs = 400),
) {
    private val provider = ScrapProvider.STEAMGRIDDB

    /**
     * Busca el ícono de [name] (el nombre del juego, mejor el estándar de ScreenScraper que el del
     * archivo). [expectedYear] ayuda a distinguir un juego de su remake con el mismo nombre.
     * [ScrapResult.NotFound] si nada se parece lo bastante o ningún candidato tiene imagen cuadrada.
     */
    suspend fun findIcon(name: String, expectedYear: Int? = null): ScrapResult<SteamGridDbMatch> {
        val key = apiKey() ?: return ScrapResult.AuthFailed(provider)
        val term = NameNormalizer.stripTags(name)
        if (term.isEmpty()) return ScrapResult.NotFound

        val games = when (val search = limiter.withBackoff { fetch(searchUrl(term), key) { SteamGridDbParser.parseSearch(it) } }) {
            is ScrapResult.Ok -> search.value
            is ScrapResult.Failure -> return search
        }

        for (game in SteamGridDbSelector.rankGames(games, term, expectedYear).take(MAX_CANDIDATES)) {
            when (val image = imageFor(game.id, key)) {
                is ScrapResult.Ok -> return ScrapResult.Ok(SteamGridDbMatch(game, image.value))
                ScrapResult.NotFound -> Unit // este candidato no tiene arte: se prueba con el siguiente
                is ScrapResult.Failure -> return image
            }
        }
        return ScrapResult.NotFound
    }

    /**
     * Las imágenes cuadradas disponibles de [name] (hasta [limit], la mejor primero), para que el usuario
     * elija una a mano. Mismo criterio de coincidencia que [findIcon]; usa el primer juego parecido que
     * tenga imágenes (los grids, y si faltan, los íconos). [ScrapResult.NotFound] si no hay ninguna.
     */
    suspend fun findIconCandidates(name: String, expectedYear: Int? = null, limit: Int = MAX_CHOICES): ScrapResult<List<SteamGridDbImage>> {
        val key = apiKey() ?: return ScrapResult.AuthFailed(provider)
        val term = NameNormalizer.stripTags(name)
        if (term.isEmpty()) return ScrapResult.NotFound

        val games = when (val search = limiter.withBackoff { fetch(searchUrl(term), key) { SteamGridDbParser.parseSearch(it) } }) {
            is ScrapResult.Ok -> search.value
            is ScrapResult.Failure -> return search
        }

        for (game in SteamGridDbSelector.rankGames(games, term, expectedYear).take(MAX_CANDIDATES)) {
            val images = ArrayList<SteamGridDbImage>()
            for (isGrid in listOf(true, false)) {
                val url = if (isGrid) gridsUrl(game.id) else iconsUrl(game.id)
                val result = limiter.withBackoff { fetch(url, key) { SteamGridDbParser.parseImages(it, isGrid) } }
                // Los íconos son el complemento de los grids: si fallan pero ya hay grids, se ofrecen esos.
                val tolerable = !isGrid && images.isNotEmpty()
                if (result is ScrapResult.Failure && result !is ScrapResult.NotFound && !tolerable) return result
                images += result.getOrNull().orEmpty()
            }
            val ranked = SteamGridDbSelector.rankImages(images).distinctBy { it.id }.take(limit)
            if (ranked.isNotEmpty()) return ScrapResult.Ok(ranked)
        }
        return ScrapResult.NotFound
    }

    /** Comprueba que la clave de API sirve (una búsqueda cualquiera). */
    suspend fun verifyKey(): ScrapResult<Unit> {
        val key = apiKey() ?: return ScrapResult.AuthFailed(provider)
        return when (val result = limiter.withBackoff { fetch(searchUrl("mario"), key) { SteamGridDbParser.parseSearch(it) } }) {
            is ScrapResult.Ok -> ScrapResult.Ok(Unit)
            is ScrapResult.Failure -> result
        }
    }

    /**
     * Descarga [image] a [out] (hasta [maxBytes]; si lo supera, se devuelve un fallo y [out] puede
     * tener datos parciales: descártalo). Solo acepta direcciones `https` de `*.steamgriddb.com` y
     * respuestas que de verdad sean una imagen.
     */
    suspend fun download(image: SteamGridDbImage, out: OutputStream, maxBytes: Long = MAX_IMAGE_BYTES): ScrapResult<Long> {
        val url = image.url.toHttpUrlOrNull()
        if (url == null || !url.isHttps || !(url.host == "steamgriddb.com" || url.host.endsWith(".steamgriddb.com"))) {
            return ScrapResult.Unavailable(provider, "dirección de descarga no válida")
        }
        return limiter.withBackoff {
            ScrapHttp.execute(provider, Request.Builder().url(url).build()) { response ->
                SteamGridDbErrors.classify(response.code)?.let { return@execute it }
                val body = response.body ?: return@execute ScrapResult.Unavailable(provider, "respuesta vacía")
                if (body.contentType()?.type != "image") return@execute ScrapResult.Unavailable(provider, "la respuesta no es una imagen")
                ScrapIo.copyBody(provider, body, out, maxBytes)
            }
        }
    }

    // ── Imágenes de un juego ──────────────────────────────────────────────────────────────────

    /** Grid cuadrado; si no hay, ícono. [ScrapResult.NotFound] si ninguno sirve. */
    private suspend fun imageFor(gameId: Long, key: String): ScrapResult<SteamGridDbImage> {
        for (isGrid in listOf(true, false)) {
            val url = if (isGrid) gridsUrl(gameId) else iconsUrl(gameId)
            val result = limiter.withBackoff { fetch(url, key) { SteamGridDbParser.parseImages(it, isGrid) } }
            if (result is ScrapResult.Failure && result !is ScrapResult.NotFound) return result
            SteamGridDbSelector.pickImage(result.getOrNull().orEmpty())?.let { return ScrapResult.Ok(it) }
        }
        return ScrapResult.NotFound
    }

    // ── Red ───────────────────────────────────────────────────────────────────────────────────

    private suspend fun <T> fetch(url: HttpUrl, key: String, parse: (String) -> T?): ScrapResult<T> =
        ScrapHttp.execute(provider, Request.Builder().url(url).header("Authorization", "Bearer $key").build()) { response ->
            readJson(response, parse)
        }

    private fun <T> readJson(response: Response, parse: (String) -> T?): ScrapResult<T> {
        SteamGridDbErrors.classify(response.code)?.let { return it }
        val text = response.body?.bytes()?.toString(Charsets.UTF_8)
            ?: return ScrapResult.Unavailable(provider, "respuesta vacía")
        return parse(text)?.let { ScrapResult.Ok(it) } ?: ScrapResult.Unavailable(provider, "respuesta ilegible")
    }

    private suspend fun apiKey(): String? = settings.steamGridDbApiKey.first().trim().takeIf { it.isNotEmpty() }

    // ── URLs ──────────────────────────────────────────────────────────────────────────────────

    private fun searchUrl(term: String): HttpUrl =
        API_BASE.newBuilder().addPathSegments("search/autocomplete").addPathSegment(term).build()

    /** Solo grids cuadrados (512 y 1024), estáticos, en un formato que Android abre, sin contenido adulto. */
    private fun gridsUrl(gameId: Long): HttpUrl =
        API_BASE.newBuilder().addPathSegments("grids/game").addPathSegment(gameId.toString())
            .addQueryParameter("dimensions", "512x512,1024x1024")
            .addQueryParameter("types", "static")
            .addQueryParameter("nsfw", "false")
            .addQueryParameter("humor", "false")
            .addQueryParameter("mimes", MIMES)
            .build()

    /**
     * Los iconos no se filtran por tamaño (algunos son `.ico` con 0×0 y se descartan al elegir) **ni por
     * formato**: este endpoint responde 400 "Invalid mime type" si se le pasa `image/jpeg` o `image/webp`
     * (verificado con la API real); el formato se filtra después, en [SteamGridDbSelector].
     */
    private fun iconsUrl(gameId: Long): HttpUrl =
        API_BASE.newBuilder().addPathSegments("icons/game").addPathSegment(gameId.toString())
            .addQueryParameter("nsfw", "false")
            .addQueryParameter("humor", "false")
            .build()

    companion object {
        /** Cuántos juegos parecidos se prueban, como máximo, hasta hallar uno con arte cuadrado. */
        const val MAX_CANDIDATES = 2

        /** Cuántas imágenes se ofrecen, como máximo, al elegir un ícono a mano. */
        const val MAX_CHOICES = 12

        /** Tope de una imagen descargada (un grid de 1024×1024 pesa bastante menos). */
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024

        private const val MIMES = "image/png,image/jpeg,image/webp"
        private val API_BASE: HttpUrl = "https://www.steamgriddb.com/api/v2/".toHttpUrl()
    }
}
