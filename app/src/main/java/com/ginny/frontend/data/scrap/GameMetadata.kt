package com.ginny.frontend.data.scrap

import com.ginny.frontend.data.scrap.screenscraper.MatchedBy
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lo que el scrapper averiguó de un juego, guardado en `Assets/<sistema>/metadata/<romFileName>.json`.
 * Es parte del contrato con Ginny Assets Manager. Código puro (solo `org.json`).
 *
 * [status] habla de **ScreenScraper**: [Status.NOT_FOUND] es una caché negativa que evita volver a
 * preguntar en cada corrida por un juego que no conoce; se reintenta pasado un tiempo o al forzar.
 * [Status.PENDING] significa que ScreenScraper todavía no se consultó (p. ej. solo se buscó el ícono
 * en SteamGridDB). Un juego puede ser [Status.NOT_FOUND] o [Status.PENDING] y tener ícono.
 *
 * Los campos de texto y fecha son los **elegidos** por la política de región/idioma, no todo lo que
 * ScreenScraper devolvió; [releaseDates] conserva las fechas de todas las regiones.
 */
data class GameMetadata(
    val status: Status,
    /** Cuándo se scrapeó (milisegundos desde 1970). */
    val scrapedAt: Long,
    val matchedBy: MatchedBy? = null,
    val screenScraperGameId: Long? = null,
    val screenScraperSystemId: Int? = null,
    val steamGridDbGameId: Long? = null,
    val steamGridDbImageId: Long? = null,
    /** Nombre estándar del juego (el que usa ScreenScraper). */
    val name: String? = null,
    val synopsis: String? = null,
    /** Nombre del [com.ginny.frontend.data.model.SystemId] de Ginny (`NES`, `SNES`…). */
    val system: String? = null,
    /** Fecha de primera publicación (`1985-09-13` o solo `1985`). */
    val releaseDate: String? = null,
    val releaseYear: Int? = null,
    /** Región → fecha. */
    val releaseDates: Map<String, String> = emptyMap(),
    val developer: String? = null,
    val publisher: String? = null,
    val genres: List<String> = emptyList(),
    val players: String? = null,
    /** De 0.0 a 1.0. */
    val rating: Double? = null,
    /** De qué región salió cada imagen: `wheel`, `boxFront`, `boxBack`, `boxSpine`. */
    val regions: Map<String, String> = emptyMap(),
    /**
     * Medias que el proveedor **no tiene** para este juego. Evita preguntar de nuevo en cada corrida;
     * si el usuario activa un tipo de media nuevo, ese no figura aquí y se vuelve a intentar.
     */
    val missingMedia: Set<ScrapMedia> = emptySet(),
) {
    enum class Status { FOUND, NOT_FOUND, PENDING }

    /** true si ya guarda algo de lo que se muestra del juego (descripción, año, estudio…). */
    val hasDetails: Boolean
        get() = synopsis != null || releaseYear != null || developer != null || publisher != null || genres.isNotEmpty()

    fun toJson(): String = JSONObject().apply {
        put("version", VERSION)
        put("status", status.name.lowercase())
        put("scrapedAt", scrapedAt)
        matchedBy?.let { put("matchedBy", it.name.lowercase()) }

        if (screenScraperGameId != null || screenScraperSystemId != null) {
            put("screenscraper", JSONObject().apply {
                screenScraperGameId?.let { put("gameId", it) }
                screenScraperSystemId?.let { put("systemId", it) }
            })
        }
        if (steamGridDbGameId != null || steamGridDbImageId != null) {
            put("steamgriddb", JSONObject().apply {
                steamGridDbGameId?.let { put("gameId", it) }
                steamGridDbImageId?.let { put("imageId", it) }
            })
        }

        name?.let { put("name", it) }
        synopsis?.let { put("synopsis", it) }
        system?.let { put("system", it) }
        releaseDate?.let { put("releaseDate", it) }
        releaseYear?.let { put("releaseYear", it) }
        if (releaseDates.isNotEmpty()) put("releaseDates", JSONObject(releaseDates))
        developer?.let { put("developer", it) }
        publisher?.let { put("publisher", it) }
        if (genres.isNotEmpty()) put("genres", JSONArray(genres))
        players?.let { put("players", it) }
        rating?.let { put("rating", it) }
        if (regions.isNotEmpty()) put("regions", JSONObject(regions))
        if (missingMedia.isNotEmpty()) put("missingMedia", JSONArray(missingMedia.map { it.name.lowercase() }))
    }.toString(2)

    companion object {
        const val VERSION = 1

        /** null si el texto no es un JSON de metadatos válido (p. ej. quedó truncado). */
        fun fromJson(text: String): GameMetadata? = runCatching {
            val json = JSONObject(text)
            val status = Status.entries.firstOrNull { it.name.equals(json.optString("status"), ignoreCase = true) }
                ?: return null
            val screenScraper = json.optJSONObject("screenscraper")
            val steamGridDb = json.optJSONObject("steamgriddb")

            GameMetadata(
                status = status,
                scrapedAt = json.optLong("scrapedAt", 0L),
                matchedBy = MatchedBy.entries.firstOrNull { it.name.equals(json.optString("matchedBy"), ignoreCase = true) },
                screenScraperGameId = screenScraper?.longOrNull("gameId"),
                screenScraperSystemId = screenScraper?.longOrNull("systemId")?.toInt(),
                steamGridDbGameId = steamGridDb?.longOrNull("gameId"),
                steamGridDbImageId = steamGridDb?.longOrNull("imageId"),
                name = json.stringOrNull("name"),
                synopsis = json.stringOrNull("synopsis"),
                system = json.stringOrNull("system"),
                releaseDate = json.stringOrNull("releaseDate"),
                releaseYear = json.longOrNull("releaseYear")?.toInt(),
                releaseDates = json.optJSONObject("releaseDates").toStringMap(),
                developer = json.stringOrNull("developer"),
                publisher = json.stringOrNull("publisher"),
                genres = json.optJSONArray("genres")?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty(),
                players = json.stringOrNull("players"),
                rating = json.takeIf { it.has("rating") && !it.isNull("rating") }?.optDouble("rating"),
                regions = json.optJSONObject("regions").toStringMap(),
                missingMedia = json.optJSONArray("missingMedia")
                    ?.let { array -> (0 until array.length()).mapNotNull { parseMedia(array.optString(it)) } }
                    .orEmpty().toSet(),
            )
        }.getOrNull()

        private fun parseMedia(name: String): ScrapMedia? = ScrapMedia.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

        private fun JSONObject.stringOrNull(name: String): String? =
            if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotEmpty() } else null

        private fun JSONObject.longOrNull(name: String): Long? =
            if (has(name) && !isNull(name)) optLong(name) else null

        private fun JSONObject?.toStringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            val result = LinkedHashMap<String, String>()
            for (key in keys()) optString(key).takeIf { it.isNotEmpty() }?.let { result[key] = it }
            return result
        }
    }
}
