package com.ginny.frontend.data.scrap.screenscraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScraperSelectorTest {

    private fun sample(): ScreenScraperGame =
        ScreenScraperParser.parseGameResponse(
            javaClass.getResource("/screenscraper/jeu_sample.json")!!.readText(Charsets.UTF_8),
        )!!

    private fun media(type: String, region: String?, format: String = "png", url: String = "https://screenscraper.fr/$type/$region") =
        ScreenScraperMedia(type, region, format, null, url)

    private fun game(vararg medias: ScreenScraperMedia) = ScreenScraperGame(
        id = 1, systemId = 3, systemName = "NES", names = emptyMap(), synopsis = emptyMap(), releaseDates = emptyMap(),
        developer = null, publisher = null, players = null, rating = null, genres = emptyMap(), medias = medias.toList(),
    )

    // ── Textos ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nombre estandar de ScreenScraper`() {
        assertEquals("Super Mario Bros.", ScreenScraperSelector.canonicalName(sample()))
    }

    @Test
    fun `sinopsis y generos en espanol, con respaldo en ingles`() {
        assertTrue(ScreenScraperSelector.pickSynopsis(sample())!!.startsWith("Un día"))
        assertEquals(listOf("Plataformas", "Plataformas / Corre y Salta"), ScreenScraperSelector.pickGenres(sample()))

        val onlyEnglish = sample().copy(synopsis = mapOf("fr" to "Bonjour", "en" to "Hello"), genres = mapOf("fr" to listOf("Plateforme"), "en" to listOf("Platform")))
        assertEquals("Hello", ScreenScraperSelector.pickSynopsis(onlyEnglish))
        assertEquals(listOf("Platform"), ScreenScraperSelector.pickGenres(onlyEnglish))

        val onlyFrench = sample().copy(synopsis = mapOf("fr" to "Bonjour"))
        assertEquals("Bonjour", ScreenScraperSelector.pickSynopsis(onlyFrench)) // el primero que haya
    }

    @Test
    fun `la fecha es la de primera publicacion, no la de una region`() {
        assertEquals("1985-09-13", ScreenScraperSelector.firstReleaseDate(sample()))
        assertEquals(1985, ScreenScraperSelector.releaseYear(sample()))
        assertNull(ScreenScraperSelector.releaseYear(sample().copy(releaseDates = emptyMap())))
        // Una fecha solo con el año ("1992") también se interpreta.
        assertEquals(1992, ScreenScraperSelector.releaseYear(sample().copy(releaseDates = mapOf("eu" to "1992"))))
    }

    // ── Wheel y video ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `wheel de la region preferida`() {
        // En la muestra hay wheel de us y de eu (no de wor): gana eu por prioridad.
        assertEquals("eu", ScreenScraperSelector.pickWheel(sample())!!.region)
    }

    @Test
    fun `wheel sin ninguna region preferida toma el primero, e ignora formatos que Android no abre`() {
        assertEquals("fr", ScreenScraperSelector.pickWheel(game(media("wheel", "fr"), media("wheel", "de")))!!.region)
        assertNull(ScreenScraperSelector.pickWheel(game(media("wheel", "eu", format = "gif"))))
    }

    @Test
    fun `video normalizado primero, el original solo si es mp4`() {
        assertEquals("video-normalized", ScreenScraperSelector.pickVideo(sample())!!.type)

        val onlyOriginalMp4 = game(media("video", null, "mp4"))
        assertEquals("video", ScreenScraperSelector.pickVideo(onlyOriginalMp4)!!.type)

        val onlyOriginalWebm = game(media("video", null, "webm"))
        assertNull(ScreenScraperSelector.pickVideo(onlyOriginalWebm))
    }

    // ── Caja ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `la caja sale de la primera region que tiene las tres caras, aunque la preferida solo tenga el frente`() {
        // En la muestra: wor solo tiene frente; eu tiene frente, trasera y lomo.
        val box = ScreenScraperSelector.pickBox(sample())
        assertEquals("eu", box.front!!.region)
        assertEquals("eu", box.back!!.region)
        assertEquals("eu", box.spine!!.region)
    }

    @Test
    fun `entre varias regiones completas gana la de mayor prioridad`() {
        val box = ScreenScraperSelector.pickBox(
            game(
                media("box-2D", "jp"), media("box-2D-back", "jp"), media("box-2D-side", "jp"),
                media("box-2D", "us"), media("box-2D-back", "us"), media("box-2D-side", "us"),
            ),
        )
        assertEquals("us", box.front!!.region)
        assertEquals("us", box.back!!.region)
        assertEquals("us", box.spine!!.region)
    }

    @Test
    fun `si ninguna region esta completa, se parte del frente y se rellena con lo que haya`() {
        val box = ScreenScraperSelector.pickBox(
            game(media("box-2D", "eu"), media("box-2D-back", "jp")),
        )
        assertEquals("eu", box.front!!.region)
        assertEquals("jp", box.back!!.region)
        assertNull(box.spine)
    }

    @Test
    fun `sin cajas la seleccion esta vacia`() {
        assertTrue(ScreenScraperSelector.pickBox(game()).isEmpty)
    }

    @Test
    fun `una region repetida conserva la primera caja`() {
        val first = media("box-2D", "eu", url = "https://screenscraper.fr/primera")
        val repeated = media("box-2D", "eu", url = "https://screenscraper.fr/repetida")
        assertEquals("https://screenscraper.fr/primera", ScreenScraperSelector.pickBox(game(first, repeated)).front!!.url)
    }
}
