package com.ginny.frontend.data.scrap

import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.data.scrap.GameMetadata.Status
import com.ginny.frontend.data.scrap.screenscraper.MatchedBy
import com.ginny.frontend.data.scrap.screenscraper.ScreenScraperMatch
import com.ginny.frontend.data.scrap.screenscraper.ScreenScraperParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMergeTest {

    private val match = ScreenScraperMatch(
        ScreenScraperParser.parseGameResponse(
            javaClass.getResource("/screenscraper/jeu_sample.json")!!.readText(Charsets.UTF_8),
        )!!,
        MatchedBy.HASH,
    )

    @Test
    fun `un juego encontrado guarda los datos elegidos por la politica`() {
        val metadata = MetadataMerge.found(previous = null, match, SystemId.NES, now = 100, includeDetails = true)
        assertEquals(Status.FOUND, metadata.status)
        assertEquals(MatchedBy.HASH, metadata.matchedBy)
        assertEquals(1245L, metadata.screenScraperGameId)
        assertEquals(3, metadata.screenScraperSystemId)
        assertEquals("Super Mario Bros.", metadata.name)
        assertEquals("NES", metadata.system)
        assertEquals("1985-09-13", metadata.releaseDate) // la primera publicación
        assertEquals(1985, metadata.releaseYear)
        assertEquals(3, metadata.releaseDates.size) // y todas las regiones
        assertEquals("Nintendo", metadata.developer)
        assertEquals("1-2", metadata.players)
        assertEquals(0.9, metadata.rating!!, 1e-9)
        assertTrue(metadata.synopsis!!.startsWith("Un día"))
        assertTrue(metadata.hasDetails)
    }

    @Test
    fun `sin metadatos activados solo se guarda lo que identifica al juego`() {
        val metadata = MetadataMerge.found(previous = null, match, SystemId.NES, now = 100, includeDetails = false)
        assertEquals("Super Mario Bros.", metadata.name)
        assertEquals(1245L, metadata.screenScraperGameId)
        assertNull(metadata.synopsis)
        assertNull(metadata.releaseYear)
        assertNull(metadata.developer)
        assertFalse(metadata.hasDetails)
    }

    @Test
    fun `conserva lo que habia de antes`() {
        val previous = GameMetadata(
            Status.PENDING, scrapedAt = 1, steamGridDbGameId = 77, steamGridDbImageId = 88,
            missingMedia = setOf(ScrapMedia.VIDEO), regions = mapOf("wheel" to "us"),
        )
        val metadata = MetadataMerge.found(previous, match, SystemId.NES, now = 100, includeDetails = false)
        assertEquals(77L, metadata.steamGridDbGameId)
        assertEquals(setOf(ScrapMedia.VIDEO), metadata.missingMedia)
        assertEquals(mapOf("wheel" to "us"), metadata.regions)
        assertEquals(100L, metadata.scrapedAt)
    }

    @Test
    fun `no encontrado deja la cache negativa sin perder el icono ya conseguido`() {
        val previous = GameMetadata(Status.PENDING, scrapedAt = 1, steamGridDbGameId = 77)
        val metadata = MetadataMerge.notFound(previous, now = 500)
        assertEquals(Status.NOT_FOUND, metadata.status)
        assertEquals(500L, metadata.scrapedAt)
        assertEquals(77L, metadata.steamGridDbGameId)
        assertEquals(Status.NOT_FOUND, MetadataMerge.notFound(null, now = 5).status)
    }

    @Test
    fun `pending no cambia lo que ya hay`() {
        val previous = GameMetadata(Status.FOUND, scrapedAt = 9, name = "X")
        assertEquals(previous, MetadataMerge.pending(previous, now = 100))
        assertEquals(Status.PENDING, MetadataMerge.pending(null, now = 100).status)
    }

    @Test
    fun `el resultado de las descargas actualiza lo ausente y las regiones`() {
        val base = GameMetadata(
            Status.FOUND, scrapedAt = 1,
            missingMedia = setOf(ScrapMedia.VIDEO, ScrapMedia.WHEEL), regions = mapOf("boxFront" to "eu"),
        )
        val updated = MetadataMerge.withOutcome(
            base,
            obtained = setOf(ScrapMedia.WHEEL),           // antes ausente, ahora conseguido (p. ej. al forzar)
            newlyMissing = setOf(ScrapMedia.BOX_SPINE),
            regions = mapOf("wheel" to "us"),
        )
        assertEquals(setOf(ScrapMedia.VIDEO, ScrapMedia.BOX_SPINE), updated.missingMedia)
        assertEquals(mapOf("boxFront" to "eu", "wheel" to "us"), updated.regions)
    }

    @Test
    fun `guardar el icono registra de donde salio`() {
        val updated = MetadataMerge.withIcon(GameMetadata(Status.FOUND, 1), steamGridDbGameId = 37382, imageId = 672112)
        assertEquals(37382L, updated.steamGridDbGameId)
        assertEquals(672112L, updated.steamGridDbImageId)
    }
}
