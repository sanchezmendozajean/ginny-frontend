package com.ginny.frontend.data.scrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScrapRedactorTest {

    @Test
    fun `oculta las credenciales de una url de ScreenScraper`() {
        val url = "https://api.screenscraper.fr/api2/jeuInfos.php?devid=DEV&devpassword=SECRETO&softname=ginny" +
            "&output=json&ssid=usuario&sspassword=otraClave&systemeid=3"
        val clean = ScrapRedactor.redact(url)
        listOf("DEV&", "SECRETO", "usuario", "otraClave").forEach { assertFalse(it, clean.contains(it)) }
        assertEquals(
            "https://api.screenscraper.fr/api2/jeuInfos.php?devid=<oculto>&devpassword=<oculto>&softname=ginny" +
                "&output=json&ssid=<oculto>&sspassword=<oculto>&systemeid=3",
            clean,
        )
    }

    @Test
    fun `oculta un token Bearer`() {
        assertEquals("Authorization: Bearer <oculto>", ScrapRedactor.redact("Authorization: Bearer abc123XYZ"))
    }

    @Test
    fun `texto sin credenciales queda igual y null da vacio`() {
        assertEquals("Unable to resolve host", ScrapRedactor.redact("Unable to resolve host"))
        assertEquals("", ScrapRedactor.redact(null))
    }
}
