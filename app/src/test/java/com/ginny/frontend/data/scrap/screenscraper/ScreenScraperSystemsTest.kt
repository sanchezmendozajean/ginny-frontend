package com.ginny.frontend.data.scrap.screenscraper

import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.data.scrap.ScrapProvider
import com.ginny.frontend.data.scrap.ScrapResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScraperSystemsTest {

    @Test
    fun `todo sistema de Ginny tiene su identificador en ScreenScraper`() {
        // Si se agrega un SystemId nuevo y se olvida aquí, esta prueba lo avisa.
        val missing = SystemId.entries.filter { ScreenScraperSystems.idFor(it) == null }
        assertTrue("Sin systemeid: $missing", missing.isEmpty())
    }

    @Test
    fun `identificadores verificados contra la lista oficial`() {
        assertEquals(3, ScreenScraperSystems.idFor(SystemId.NES))
        assertEquals(1, ScreenScraperSystems.idFor(SystemId.GENESIS))
        assertEquals(225, ScreenScraperSystems.idFor(SystemId.SWITCH))
        assertEquals(17, ScreenScraperSystems.idFor(SystemId.N3DS))
        assertEquals(75, ScreenScraperSystems.idFor(SystemId.MAME))
        assertEquals(75, ScreenScraperSystems.idFor(SystemId.FBNEO))
    }

    @Test
    fun `romtype segun el archivo`() {
        assertEquals("rom", ScreenScraperSystems.romTypeFor(isFolder = false, fileName = "Super Mario Bros.nes"))
        assertEquals("rom", ScreenScraperSystems.romTypeFor(isFolder = false, fileName = "Sonic.bin")) // .bin de Genesis es ROM
        assertEquals("iso", ScreenScraperSystems.romTypeFor(isFolder = false, fileName = "Metroid Prime.RVZ"))
        assertEquals("iso", ScreenScraperSystems.romTypeFor(isFolder = false, fileName = "Final Fantasy VII.chd"))
        assertEquals("dossier", ScreenScraperSystems.romTypeFor(isFolder = true, fileName = "Zelda Breath of the Wild"))
    }

    @Test
    fun `codigos HTTP de ScreenScraper`() {
        assertNull(ScreenScraperErrors.classify(200))
        assertEquals(ScrapResult.NotFound, ScreenScraperErrors.classify(404))
        assertEquals(ScrapResult.AuthFailed(ScrapProvider.SCREENSCRAPER), ScreenScraperErrors.classify(403))
        assertEquals(ScrapResult.RateLimited(ScrapProvider.SCREENSCRAPER), ScreenScraperErrors.classify(429))
        assertEquals(ScrapResult.QuotaExhausted(ScrapProvider.SCREENSCRAPER), ScreenScraperErrors.classify(430))
        assertEquals(ScrapResult.QuotaExhausted(ScrapProvider.SCREENSCRAPER), ScreenScraperErrors.classify(431))
        assertTrue(ScreenScraperErrors.classify(503) is ScrapResult.Unavailable)
        assertTrue(ScreenScraperErrors.classify(418) is ScrapResult.Unavailable)
    }

    @Test
    fun `quien aborta el lote`() {
        assertTrue(ScreenScraperErrors.classify(403)!!.abortsBatch)
        assertTrue(ScreenScraperErrors.classify(430)!!.abortsBatch)
        assertEquals(false, ScreenScraperErrors.classify(404)!!.abortsBatch)
        assertEquals(false, ScreenScraperErrors.classify(429)!!.abortsBatch)
        assertNotNull(ScreenScraperErrors.classify(500)!!.message)
    }
}
