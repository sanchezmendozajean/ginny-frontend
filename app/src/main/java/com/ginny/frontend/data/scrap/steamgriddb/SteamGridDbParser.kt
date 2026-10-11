package com.ginny.frontend.data.scrap.steamgriddb

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset

/**
 * Lee las respuestas JSON de SteamGridDB (`{"success": true, "data": [...]}`). Código puro, solo
 * `org.json`. Estructura verificada con respuestas reales (2026-10-10).
 */
object SteamGridDbParser {

    /** Los juegos de `search/autocomplete` (lista vacía si no hay resultados); null si no es la estructura esperada. */
    fun parseSearch(text: String): List<SteamGridDbGame>? = runCatching {
        val root = JSONObject(text)
        if (!root.optBoolean("success", false)) return null
        val data = root.optJSONArray("data") ?: return@runCatching emptyList()
        (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optLong("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            val name = item.optString("name", "").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SteamGridDbGame(
                id = id,
                name = name,
                verified = item.optBoolean("verified", false),
                releaseYear = item.takeIf { it.has("release_date") && !it.isNull("release_date") }
                    ?.optLong("release_date")
                    ?.takeIf { it > 0 }
                    ?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).year },
            )
        }
    }.getOrNull()

    /**
     * Las imágenes de `grids/game/{id}` o `icons/game/{id}` ([isGrid] indica cuál). Los elementos sin
     * `url`, o sin ancho y alto, se descartan; null si el texto no es la estructura esperada.
     */
    fun parseImages(text: String, isGrid: Boolean): List<SteamGridDbImage>? = runCatching {
        val root = JSONObject(text)
        if (!root.optBoolean("success", false)) return null
        val data = root.optJSONArray("data") ?: return@runCatching emptyList()
        (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url", "").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SteamGridDbImage(
                id = item.optLong("id", -1),
                url = url,
                width = item.optInt("width", 0),
                height = item.optInt("height", 0),
                mime = item.optString("mime", "").lowercase(),
                style = item.optString("style", "").takeIf { it.isNotEmpty() },
                language = item.optString("language", "").takeIf { it.isNotEmpty() },
                score = item.optInt("score", 0),
                isGrid = isGrid,
                thumb = item.optString("thumb", "").trim().takeIf { it.isNotEmpty() },
            )
        }
    }.getOrNull()
}
