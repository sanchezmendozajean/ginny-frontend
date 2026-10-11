package com.ginny.frontend.data.scrap.steamgriddb

import com.ginny.frontend.data.scrap.ScrapProvider
import com.ginny.frontend.data.scrap.ScrapResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGridDbSelectorTest {

    private fun image(
        id: Long,
        side: Int,
        language: String? = "en",
        style: String? = "alternate",
        score: Int = 0,
        mime: String = "image/png",
        height: Int = side,
    ) = SteamGridDbImage(id, "https://cdn2.steamgriddb.com/grid/$id", side, height, mime, style, language, score, isGrid = true)

    private fun game(id: Long, name: String, year: Int? = null, verified: Boolean = true) = SteamGridDbGame(id, name, verified, year)

    // ── Qué juego ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `solo se aceptan juegos que se llaman casi igual`() {
        val ranked = SteamGridDbSelector.rankGames(
            listOf(
                game(1, "Super Mario Bros. 3"), // otro juego: el número difiere
                game(2, "Super Mario Bros."),
                game(3, "Super Mario Bros Z: The Game"),
                game(4, "Metroid"),
            ),
            name = "Super Mario Bros.",
            expectedYear = null,
        )
        assertEquals(listOf(2L), ranked.map { it.id })
    }

    @Test
    fun `entre dos juegos con el mismo nombre gana el del ano esperado`() {
        val ranked = SteamGridDbSelector.rankGames(
            listOf(game(10, "Adventure Island", year = 2019), game(11, "Adventure Island", year = 1986)),
            name = "Adventure Island",
            expectedYear = 1988,
        )
        assertEquals(listOf(11L, 10L), ranked.map { it.id })
    }

    @Test
    fun `sin ano esperado desempata lo verificado y despues el id mas bajo`() {
        val ranked = SteamGridDbSelector.rankGames(
            listOf(game(30, "Metroid", verified = false), game(20, "Metroid"), game(10, "Metroid")),
            name = "Metroid",
            expectedYear = null,
        )
        assertEquals(listOf(10L, 20L, 30L), ranked.map { it.id })
    }

    @Test
    fun `ignora lo que va entre parentesis al comparar`() {
        val ranked = SteamGridDbSelector.rankGames(listOf(game(1, "Sonic The Hedgehog")), "Sonic The Hedgehog (USA, Europe) [!]", null)
        assertEquals(listOf(1L), ranked.map { it.id })
    }

    // ── Qué imagen ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `rankImages ofrece todas las utilizables, de la mejor a la peor`() {
        val ranked = SteamGridDbSelector.rankImages(
            listOf(
                image(1, 1024),
                image(2, 512),
                image(3, 512, mime = "image/x-icon"), // formato que Android no abre
                image(4, 512, height = 256),          // no cuadrada
                image(5, 256),
            ),
        )
        assertEquals(listOf(2L, 1L, 5L), ranked.map { it.id })
        // La primera es la que elige pickImage.
        assertEquals(ranked.first().id, SteamGridDbSelector.pickImage(ranked)!!.id)
    }

    @Test
    fun `prefiere 512 aunque haya mas de 1024`() {
        val picked = SteamGridDbSelector.pickImage(listOf(image(1, 1024), image(2, 512), image(3, 1024)))!!
        assertEquals(2L, picked.id)
        assertFalse(picked.needsDownscale)
    }

    @Test
    fun `si solo hay 1024 la usa y se reduce`() {
        val picked = SteamGridDbSelector.pickImage(listOf(image(1, 1024)))!!
        assertEquals(1024, picked.width)
        assertTrue(picked.needsDownscale)
    }

    @Test
    fun `una mas grande que 512 gana a una mas chica, y las chicas no se agrandan`() {
        // 1024 (se reduce sin perder calidad) antes que 256 (habría que agrandarla).
        assertEquals(1L, SteamGridDbSelector.pickImage(listOf(image(2, 256), image(1, 1024)))!!.id)
        // Solo chicas: la mayor, tal cual.
        val picked = SteamGridDbSelector.pickImage(listOf(image(1, 128), image(2, 256), image(3, 64)))!!
        assertEquals(256, picked.width)
        assertFalse(picked.needsDownscale)
    }

    @Test
    fun `entre las mas grandes que 512 toma la menor`() {
        assertEquals(1L, SteamGridDbSelector.pickImage(listOf(image(2, 1024), image(1, 768)))!!.id)
    }

    @Test
    fun `descarta lo que no es cuadrado ni un formato que Android abre`() {
        val onlyBad = listOf(
            image(1, 512, height = 515),
            image(2, 512, mime = "image/vnd.microsoft.icon"),
            image(3, 0), // un .ico con 0x0
            image(4, 512, mime = "image/gif"),
        )
        assertNull(SteamGridDbSelector.pickImage(onlyBad))
        assertEquals(5L, SteamGridDbSelector.pickImage(onlyBad + image(5, 512, mime = "image/webp"))!!.id)
    }

    @Test
    fun `a igual tamano prefiere espanol, luego ingles, luego otro idioma`() {
        assertEquals(1L, SteamGridDbSelector.pickImage(listOf(image(3, 512, language = "ja"), image(2, 512, language = "en"), image(1, 512, language = "es")))!!.id)
        assertEquals(2L, SteamGridDbSelector.pickImage(listOf(image(3, 512, language = "ja"), image(2, 512, language = "en")))!!.id)
        assertEquals(3L, SteamGridDbSelector.pickImage(listOf(image(3, 512, language = "ja")))!!.id)
    }

    @Test
    fun `el tamano manda sobre el idioma`() {
        // 512 en japonés frente a 1024 en inglés: se queda el 512.
        assertEquals(1L, SteamGridDbSelector.pickImage(listOf(image(1, 512, language = "ja"), image(2, 1024, language = "en")))!!.id)
    }

    @Test
    fun `evita el logo blanco y desempata por puntuacion`() {
        assertEquals(1L, SteamGridDbSelector.pickImage(listOf(image(2, 512, style = "white_logo", score = 9), image(1, 512, style = "alternate")))!!.id)
        assertEquals(2L, SteamGridDbSelector.pickImage(listOf(image(1, 512, score = 1), image(2, 512, score = 5)))!!.id)
    }

    @Test
    fun `sin imagenes no hay eleccion`() {
        assertNull(SteamGridDbSelector.pickImage(emptyList()))
    }

    // ── Errores ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `codigos HTTP de SteamGridDB`() {
        assertNull(SteamGridDbErrors.classify(200))
        assertEquals(ScrapResult.AuthFailed(ScrapProvider.STEAMGRIDDB), SteamGridDbErrors.classify(401))
        assertEquals(ScrapResult.NotFound, SteamGridDbErrors.classify(404))
        assertEquals(ScrapResult.RateLimited(ScrapProvider.STEAMGRIDDB), SteamGridDbErrors.classify(429))
        assertTrue(SteamGridDbErrors.classify(502) is ScrapResult.Unavailable)
        assertTrue(SteamGridDbErrors.classify(401)!!.abortsBatch)
        assertFalse(SteamGridDbErrors.classify(404)!!.abortsBatch)
    }
}
