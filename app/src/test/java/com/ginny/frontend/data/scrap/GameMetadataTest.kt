package com.ginny.frontend.data.scrap

import com.ginny.frontend.data.scrap.screenscraper.MatchedBy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMetadataTest {

    private val full = GameMetadata(
        status = GameMetadata.Status.FOUND,
        scrapedAt = 1_760_000_000_000,
        matchedBy = MatchedBy.FILE_NAME,
        screenScraperGameId = 1245,
        screenScraperSystemId = 3,
        steamGridDbGameId = 37382,
        steamGridDbImageId = 672112,
        name = "Super Mario Bros.",
        synopsis = "Un día, el reino de los pacíficos hombres champiñón fue invadido.",
        system = "NES",
        releaseDate = "1985-09-13",
        releaseYear = 1985,
        releaseDates = mapOf("us" to "1985-11-17", "jp" to "1985-09-13", "eu" to "1987-05-15"),
        developer = "Nintendo",
        publisher = "Nintendo",
        genres = listOf("Plataformas", "Plataformas / Corre y Salta"),
        players = "1-2",
        rating = 0.9,
        regions = mapOf("wheel" to "eu", "boxFront" to "eu", "boxBack" to "eu", "boxSpine" to "eu"),
        missingMedia = setOf(ScrapMedia.VIDEO),
    )

    @Test
    fun `ida y vuelta conserva todo`() {
        assertEquals(full, GameMetadata.fromJson(full.toJson()))
    }

    @Test
    fun `el JSON tiene la forma del contrato`() {
        val json = JSONObject(full.toJson())
        assertEquals(1, json.getInt("version"))
        assertEquals("found", json.getString("status"))
        assertEquals("file_name", json.getString("matchedBy"))
        assertEquals(1245L, json.getJSONObject("screenscraper").getLong("gameId"))
        assertEquals(672112L, json.getJSONObject("steamgriddb").getLong("imageId"))
        assertEquals(1985, json.getInt("releaseYear"))
        assertEquals("eu", json.getJSONObject("regions").getString("boxFront"))
        assertEquals("video", json.getJSONArray("missingMedia").getString(0))
        assertTrue(json.getString("synopsis").contains("champiñón")) // texto en claro, no escapado
    }

    @Test
    fun `una cache negativa guarda solo lo necesario`() {
        val notFound = GameMetadata(status = GameMetadata.Status.NOT_FOUND, scrapedAt = 5, missingMedia = setOf(ScrapMedia.ICON))
        val json = JSONObject(notFound.toJson())
        assertEquals("not_found", json.getString("status"))
        listOf("name", "synopsis", "screenscraper", "steamgriddb", "genres", "regions").forEach { assertFalse(it, json.has(it)) }
        assertEquals(notFound, GameMetadata.fromJson(notFound.toJson()))
    }

    @Test
    fun `un juego sin fecha ni nota no inventa valores`() {
        val parsed = GameMetadata.fromJson(GameMetadata(GameMetadata.Status.FOUND, 1, name = "X").toJson())!!
        assertNull(parsed.releaseYear)
        assertNull(parsed.rating)
        assertNull(parsed.developer)
        assertEquals(emptyList<String>(), parsed.genres)
        assertEquals(emptySet<ScrapMedia>(), parsed.missingMedia)
    }

    @Test
    fun `un archivo truncado o ajeno no es un metadato valido`() {
        val json = full.toJson()
        assertNull(GameMetadata.fromJson(json.take(json.length / 2))) // se cortó a mitad
        assertNull(GameMetadata.fromJson(""))
        assertNull(GameMetadata.fromJson("""{"status":"quien-sabe"}"""))
        assertNull(GameMetadata.fromJson("[1,2,3]"))
    }

    @Test
    fun `ignora medias y valores que no conoce, sin fallar`() {
        val parsed = GameMetadata.fromJson(
            """{"version":9,"status":"found","scrapedAt":1,"matchedBy":"magia","missingMedia":["video","holograma"],"campoNuevo":123}""",
        )
        assertNotNull(parsed)
        assertNull(parsed!!.matchedBy)
        assertEquals(setOf(ScrapMedia.VIDEO), parsed.missingMedia)
    }

    @Test
    fun `cada tipo de media tiene su carpeta y los nombres de carpeta no se repiten`() {
        assertEquals(AssetFolder.ICONS, AssetFolder.of(ScrapMedia.ICON))
        assertEquals(AssetFolder.BOX_SPINE, AssetFolder.of(ScrapMedia.BOX_SPINE))
        assertEquals(ScrapMedia.entries.size, ScrapMedia.entries.map { AssetFolder.of(it) }.toSet().size)
        assertEquals(AssetFolder.entries.size, AssetFolder.entries.map { it.dirName }.toSet().size)
        assertEquals(setOf("png"), AssetFolder.ICONS.extensions) // convención de siempre
    }
}
