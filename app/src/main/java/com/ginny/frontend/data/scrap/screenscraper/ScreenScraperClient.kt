package com.ginny.frontend.data.scrap.screenscraper

import com.ginny.frontend.data.model.RomOrganization
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.data.scrap.NameNormalizer
import com.ginny.frontend.data.scrap.ScrapHttp
import com.ginny.frontend.data.scrap.ScrapIo
import com.ginny.frontend.data.scrap.ScrapProvider
import com.ginny.frontend.data.scrap.ScrapRateLimiter
import com.ginny.frontend.data.scrap.ScrapRedactor
import com.ginny.frontend.data.scrap.ScrapResult
import com.ginny.frontend.data.scrap.ScrapperDevCredentials
import com.ginny.frontend.data.scrap.ScrapperSettingsRepository
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.OutputStream

/** Qué archivo se quiere identificar. */
data class RomLookup(
    val systemId: SystemId,
    /** Nombre del archivo con su extensión (o de la carpeta, en los sistemas de carpeta por juego). */
    val romName: String,
    /** Nombre a mostrar del juego, para la búsqueda de respaldo. */
    val displayName: String,
    val sizeBytes: Long? = null,
    /** Hashes en hexadecimal; solo si el archivo era lo bastante chico para calcularlos. */
    val crc32: String? = null,
    val md5: String? = null,
    val sha1: String? = null,
)

/**
 * Cliente de la API v2 de ScreenScraper (`api.screenscraper.fr/api2`).
 *
 * - **Una petición a la vez** (la cuenta permite 1 hilo) y con pausa mínima: todo pasa por [limiter],
 *   incluidas las descargas de media.
 * - Las credenciales de **desarrollador** vienen del APK ([ScrapperDevCredentials]); las del
 *   **usuario**, de Configuración. Van en la query string de cada petición, así que ningún mensaje
 *   de error las incluye (ver [ScrapRedactor]).
 * - Nada se lanza: todo es un [ScrapResult].
 *
 * Sin probar contra la API real dentro de la app todavía; el formato de las respuestas sí está
 * verificado con peticiones reales (ver docs/plan-scrapper.md).
 */
class ScreenScraperClient(
    private val settings: ScrapperSettingsRepository,
    private val limiter: ScrapRateLimiter = ScrapRateLimiter(minIntervalMs = 300),
) {
    private val provider = ScrapProvider.SCREENSCRAPER

    /**
     * Identifica el juego de [lookup]: primero por archivo (con hash si lo hay), y si no lo conoce,
     * por una búsqueda por nombre. Una coincidencia por búsqueda solo se acepta si el nombre se
     * parece mucho ([MIN_SEARCH_SIMILARITY]): es preferible no encontrar nada que asociar otro juego.
     */
    suspend fun findGame(lookup: RomLookup): ScrapResult<ScreenScraperMatch> {
        val systemId = ScreenScraperSystems.idFor(lookup.systemId)
            ?: return ScrapResult.NotFound

        val byFile = limiter.withBackoff { fetchGame(jeuInfosUrl(lookup, systemId) ?: return@withBackoff authFailed()) }
        when (byFile) {
            is ScrapResult.Ok -> {
                val usedHash = lookup.crc32 != null || lookup.md5 != null || lookup.sha1 != null
                return ScrapResult.Ok(ScreenScraperMatch(byFile.value, if (usedHash) MatchedBy.HASH else MatchedBy.FILE_NAME))
            }
            ScrapResult.NotFound -> Unit // se intenta por nombre
            is ScrapResult.Failure -> return byFile
        }

        val searchUrl = jeuRechercheUrl(lookup, systemId) ?: return authFailed()
        return when (val search = limiter.withBackoff { fetchSearch(searchUrl) }) {
            is ScrapResult.Ok -> bestByName(search.value, lookup)
                ?.let { ScrapResult.Ok(ScreenScraperMatch(it, MatchedBy.SEARCH)) }
                ?: ScrapResult.NotFound
            is ScrapResult.Failure -> search
        }
    }

    /** Comprueba las credenciales y lee el cupo de la cuenta (`ssuserInfos.php`): cuesta una petición. */
    suspend fun verifyAccount(): ScrapResult<ScreenScraperUser> {
        val url = baseUrl("ssuserInfos.php") ?: return authFailed()
        return limiter.withBackoff {
            ScrapHttp.execute(provider, Request.Builder().url(url).build()) { response ->
                readJson(response) { text ->
                    ScreenScraperParser.parseUserResponse(text)?.let { ScrapResult.Ok(it) }
                }
            }
        }
    }

    /**
     * Descarga una media a [out] (la `url` que dio la API; ya lleva credenciales). Devuelve los bytes
     * escritos. Pasa por el mismo limitador que las consultas: ScreenScraper cuenta cada descarga
     * como un hilo. Rechaza cualquier URL que no sea de ScreenScraper, para no enviar credenciales a
     * otro servidor. Si la media supera [maxBytes] devuelve un fallo y [out] puede tener datos
     * parciales: quien lo llama debe descartar el destino.
     */
    suspend fun download(media: ScreenScraperMedia, out: OutputStream, maxBytes: Long = MAX_MEDIA_BYTES): ScrapResult<Long> {
        val url = media.url.toHttpUrlOrNull()
        if (url == null || !url.isHttps || !(url.host == "screenscraper.fr" || url.host.endsWith(".screenscraper.fr"))) {
            return ScrapResult.Unavailable(provider, "dirección de descarga no válida")
        }
        return limiter.withBackoff {
            ScrapHttp.execute(provider, Request.Builder().url(url).build()) { response ->
                ScreenScraperErrors.classify(response.code)?.let { return@execute it }
                val body = response.body ?: return@execute ScrapResult.Unavailable(provider, "respuesta vacía")
                // Una media que falta puede llegar como texto ("NOMEDIA") con código 200.
                if (body.contentType()?.type == "text") {
                    val snippet = body.string().take(80)
                    return@execute if (snippet.contains("NOMEDIA", ignoreCase = true)) {
                        ScrapResult.NotFound
                    } else {
                        ScrapResult.Unavailable(provider, ScrapRedactor.redact(snippet))
                    }
                }
                ScrapIo.copyBody(provider, body, out, maxBytes)
            }
        }
    }

    // ── Peticiones ────────────────────────────────────────────────────────────────────────────

    private suspend fun fetchGame(url: HttpUrl): ScrapResult<ScreenScraperGame> =
        ScrapHttp.execute(provider, Request.Builder().url(url).build()) { response ->
            readJson(response) { text -> ScreenScraperParser.parseGameResponse(text)?.let { ScrapResult.Ok(it) } }
        }

    private suspend fun fetchSearch(url: HttpUrl): ScrapResult<List<ScreenScraperGame>> =
        ScrapHttp.execute(provider, Request.Builder().url(url).build()) { response ->
            readJson(response) { text -> ScreenScraperParser.parseSearchResponse(text)?.let { ScrapResult.Ok(it) } }
        }

    /**
     * Si el código es de éxito, lee el cuerpo como UTF-8 y lo entrega a [parse] (que devuelve null si
     * no reconoce la estructura); si no, el fallo que corresponde al código.
     */
    private inline fun <T> readJson(response: Response, parse: (String) -> ScrapResult<T>?): ScrapResult<T> {
        ScreenScraperErrors.classify(response.code)?.let { return it }
        val text = response.body?.bytes()?.toString(Charsets.UTF_8)
            ?: return ScrapResult.Unavailable(provider, "respuesta vacía")
        return parse(text) ?: ScrapResult.Unavailable(provider, "respuesta ilegible")
    }

    // ── Construcción de URLs ──────────────────────────────────────────────────────────────────

    /** La URL base con las credenciales; null si el APK se compiló sin las de desarrollador. */
    private suspend fun baseUrl(endpoint: String): HttpUrl? {
        if (!ScrapperDevCredentials.screenScraperAvailable) return null
        val user = settings.screenScraperUser.first().trim()
        val password = settings.screenScraperPassword.first().trim()
        return API_BASE.newBuilder()
            .addPathSegment(endpoint)
            .addQueryParameter("devid", ScrapperDevCredentials.screenScraperDevId)
            .addQueryParameter("devpassword", ScrapperDevCredentials.screenScraperDevPassword)
            .addQueryParameter("softname", SOFTNAME)
            .addQueryParameter("output", "json")
            .apply {
                if (user.isNotEmpty()) addQueryParameter("ssid", user)
                if (password.isNotEmpty()) addQueryParameter("sspassword", password)
            }
            .build()
    }

    private suspend fun jeuInfosUrl(lookup: RomLookup, systemeId: Int): HttpUrl? {
        val isFolder = SystemCatalog.byId.getValue(lookup.systemId).romOrganization == RomOrganization.FOLDER_PER_GAME
        return baseUrl("jeuInfos.php")?.newBuilder()?.apply {
            addQueryParameter("systemeid", systemeId.toString())
            addQueryParameter("romtype", ScreenScraperSystems.romTypeFor(isFolder, lookup.romName))
            addQueryParameter("romnom", lookup.romName)
            lookup.sizeBytes?.let { addQueryParameter("romtaille", it.toString()) }
            lookup.crc32?.let { addQueryParameter("crc", it) }
            lookup.md5?.let { addQueryParameter("md5", it) }
            lookup.sha1?.let { addQueryParameter("sha1", it) }
        }?.build()
    }

    private suspend fun jeuRechercheUrl(lookup: RomLookup, systemeId: Int): HttpUrl? =
        baseUrl("jeuRecherche.php")?.newBuilder()
            ?.addQueryParameter("systemeid", systemeId.toString())
            ?.addQueryParameter("recherche", NameNormalizer.stripTags(lookup.displayName))
            ?.build()

    // ── Coincidencia por nombre ───────────────────────────────────────────────────────────────

    /**
     * Entre los candidatos de una búsqueda, el que más se parece al nombre buscado (comparando con
     * todos sus nombres regionales), si llega al mínimo exigido.
     */
    private fun bestByName(candidates: List<ScreenScraperGame>, lookup: RomLookup): ScreenScraperGame? =
        candidates
            .map { game -> game to ScreenScraperSelector.allNames(game).maxOfOrNull { NameNormalizer.similarity(it, lookup.displayName) }.orZero() }
            .filter { (_, score) -> score >= MIN_SEARCH_SIMILARITY }
            .maxByOrNull { (_, score) -> score }
            ?.first

    private fun Double?.orZero() = this ?: 0.0

    private fun authFailed(): ScrapResult.Failure = ScrapResult.AuthFailed(provider)

    companion object {
        /** Nombre del software, que ScreenScraper exige en cada petición (`softname`). */
        const val SOFTNAME = "GinnyFrontend"

        /** Parecido mínimo (0.0-1.0) para aceptar un juego hallado por búsqueda y no por archivo. */
        const val MIN_SEARCH_SIMILARITY = 0.92

        /** Tope de una media descargada: la mayor (un video normalizado) ronda los 2 MB. */
        const val MAX_MEDIA_BYTES = 50L * 1024 * 1024

        private val API_BASE: HttpUrl = "https://api.screenscraper.fr/api2/".toHttpUrlOrNull()!!
    }
}
