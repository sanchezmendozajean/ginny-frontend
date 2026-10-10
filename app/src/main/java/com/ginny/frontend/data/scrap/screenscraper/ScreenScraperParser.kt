package com.ginny.frontend.data.scrap.screenscraper

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lee las respuestas JSON de ScreenScraper. Código puro: solo `org.json`, sin red ni Android.
 *
 * Estructura (verificada con respuestas reales, 2026-10-10): `response.jeu` (jeuInfos) o
 * `response.jeux[]` (jeuRecherche, hasta 3 juegos **con todas sus medias**), y `response.ssuser`.
 * Las respuestas pueden pesar más de 1 MB (cientos de hacks y traducciones en `roms`): se leen igual,
 * pero de todo eso solo se conserva lo que [ScreenScraperGame] declara.
 */
object ScreenScraperParser {

    /** El juego de una respuesta de `jeuInfos.php`; null si el texto no es la estructura esperada. */
    fun parseGameResponse(text: String): ScreenScraperGame? = runCatching {
        parseGame(JSONObject(text).getJSONObject("response").getJSONObject("jeu"))
    }.getOrNull()

    /**
     * Los juegos de una respuesta de `jeuRecherche.php` (puede ser una lista vacía si no hay
     * resultados); null si el texto no es la estructura esperada.
     */
    fun parseSearchResponse(text: String): List<ScreenScraperGame>? = runCatching {
        val jeux = JSONObject(text).getJSONObject("response").optJSONArray("jeux")
        jeux.objects().mapNotNull { parseGame(it) }
    }.getOrNull()

    fun parseUserResponse(text: String): ScreenScraperUser? = runCatching {
        val user = JSONObject(text).getJSONObject("response").getJSONObject("ssuser")
        ScreenScraperUser(
            userName = user.text("id"),
            maxThreads = user.int("maxthreads"),
            requestsToday = user.int("requeststoday"),
            maxRequestsPerDay = user.int("maxrequestsperday"),
            failedRequestsToday = user.int("requestskotoday"),
            maxFailedRequestsPerDay = user.int("maxrequestskoperday"),
        )
    }.getOrNull()

    /** Un objeto `jeu`; null si no trae `id` (la búsqueda a veces devuelve objetos vacíos). */
    fun parseGame(jeu: JSONObject): ScreenScraperGame? {
        val id = jeu.optString("id").toLongOrNull() ?: return null
        val system = jeu.optJSONObject("systeme")

        return ScreenScraperGame(
            id = id,
            systemId = system?.int("id"),
            systemName = system?.text("text"),
            names = jeu.optJSONArray("noms").objects().keyedBy("region", "text"),
            synopsis = jeu.optJSONArray("synopsis").objects().keyedBy("langue", "text"),
            releaseDates = jeu.optJSONArray("dates").objects().keyedBy("region", "text"),
            developer = jeu.optJSONObject("developpeur")?.text("text"),
            publisher = jeu.optJSONObject("editeur")?.text("text"),
            players = jeu.optJSONObject("joueurs")?.text("text"),
            rating = jeu.optJSONObject("note")?.text("text")?.toDoubleOrNull()?.let { (it / 20.0).coerceIn(0.0, 1.0) },
            genres = parseGenres(jeu.optJSONArray("genres")),
            medias = jeu.optJSONArray("medias").objects().mapNotNull { parseMedia(it) },
        )
    }

    private fun parseMedia(media: JSONObject): ScreenScraperMedia? {
        val type = media.text("type") ?: return null
        val url = media.text("url") ?: return null
        val format = media.text("format") ?: return null
        return ScreenScraperMedia(
            type = type,
            region = media.text("region"),
            format = format.lowercase(),
            sizeBytes = media.text("size")?.toLongOrNull(),
            url = url,
        )
    }

    /** Idioma → nombres. Cada género es `{id, noms: [{langue, text}]}`. */
    private fun parseGenres(genres: JSONArray?): Map<String, List<String>> {
        val byLanguage = LinkedHashMap<String, MutableList<String>>()
        for (genre in genres.objects()) {
            for (name in genre.optJSONArray("noms").objects()) {
                val language = name.text("langue") ?: continue
                val text = name.text("text") ?: continue
                val list = byLanguage.getOrPut(language) { mutableListOf() }
                if (text !in list) list += text
            }
        }
        return byLanguage
    }

    // ── Ayudas de org.json ────────────────────────────────────────────────────────────────────

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    /** `optString` que devuelve null si falta o está en blanco (org.json devuelve ""). */
    private fun JSONObject.text(name: String): String? = optString(name, "").trim().takeIf { it.isNotEmpty() }

    private fun JSONObject.int(name: String): Int? = text(name)?.toIntOrNull()

    /** Lista de objetos → mapa `clave → valor`, sin entradas vacías y conservando la primera repetida. */
    private fun List<JSONObject>.keyedBy(keyName: String, valueName: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (item in this) {
            val key = item.text(keyName) ?: continue
            val value = item.text(valueName) ?: continue
            result.putIfAbsent(key, value)
        }
        return result
    }
}
