package com.ginny.frontend.data.scrap.screenscraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScraperParserTest {

    private fun sample(): String =
        javaClass.getResource("/screenscraper/jeu_sample.json")!!.readText(Charsets.UTF_8)

    @Test
    fun `lee los datos del juego`() {
        val game = ScreenScraperParser.parseGameResponse(sample())!!
        assertEquals(1245L, game.id)
        assertEquals(3, game.systemId)
        assertEquals("NES", game.systemName)
        assertEquals("Super Mario Bros.", game.names["ss"])
        assertEquals("Nintendo", game.developer)
        assertEquals("Nintendo", game.publisher)
        assertEquals("1-2", game.players)
        assertEquals(0.9, game.rating!!, 1e-9) // 18 sobre 20
        assertEquals("1985-11-17", game.releaseDates["us"])
        assertTrue(game.synopsis.getValue("es").contains("champiñón")) // UTF-8 intacto
        assertEquals(listOf("Plataformas", "Plataformas / Corre y Salta"), game.genres["es"])
    }

    @Test
    fun `lee las medias sin perder la region ni el tamano`() {
        val game = ScreenScraperParser.parseGameResponse(sample())!!
        val wheelEu = game.medias.first { it.type == "wheel" && it.region == "eu" }
        assertEquals("png", wheelEu.format)
        assertEquals(43811L, wheelEu.sizeBytes)
        // Los videos no tienen región.
        assertNull(game.medias.first { it.type == "video-normalized" }.region)
    }

    @Test
    fun `una region repetida conserva la primera`() {
        val names = ScreenScraperParser.parseGameResponse(
            """{"response":{"jeu":{"id":"1","noms":[{"region":"us","text":"Primero"},{"region":"us","text":"Segundo"}]}}}""",
        )!!.names
        assertEquals("Primero", names["us"])
    }

    @Test
    fun `lee la busqueda y salta los objetos vacios`() {
        val text = """
            {"response":{"jeux":[
              {"id":"1436","systeme":{"id":"3","text":"NES"},"noms":[{"region":"ss","text":"Adventure Island 2"}]},
              {},
              {"id":"1521","noms":[{"region":"ss","text":"Adventure Island 3"}]}
            ]}}
        """.trimIndent()
        val games = ScreenScraperParser.parseSearchResponse(text)!!
        assertEquals(listOf(1436L, 1521L), games.map { it.id })
    }

    @Test
    fun `una busqueda sin resultados es una lista vacia, no un error`() {
        assertEquals(emptyList<ScreenScraperGame>(), ScreenScraperParser.parseSearchResponse("""{"response":{}}"""))
    }

    @Test
    fun `texto que no es la estructura esperada da null`() {
        assertNull(ScreenScraperParser.parseGameResponse("Erreur de login : Vérifier vos identifiants développeur !"))
        assertNull(ScreenScraperParser.parseGameResponse("""{"response":{}}"""))
        assertNull(ScreenScraperParser.parseSearchResponse("<html>"))
        assertNull(ScreenScraperParser.parseUserResponse("{}"))
    }

    @Test
    fun `lee el cupo de la cuenta`() {
        val user = ScreenScraperParser.parseUserResponse(sample())
        assertNotNull(user)
        assertEquals("usuario-de-prueba", user!!.userName)
        assertEquals(1, user.maxThreads)
        assertEquals(12, user.requestsToday)
        assertEquals(50000, user.maxRequestsPerDay)
        assertEquals(3, user.failedRequestsToday)
        assertEquals(5000, user.maxFailedRequestsPerDay)
    }
}
