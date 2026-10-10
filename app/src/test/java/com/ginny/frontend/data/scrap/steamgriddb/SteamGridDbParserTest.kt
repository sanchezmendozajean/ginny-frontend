package com.ginny.frontend.data.scrap.steamgriddb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGridDbParserTest {

    @Test
    fun `lee la busqueda con el ano de release_date`() {
        val games = SteamGridDbParser.parseSearch(
            """{"success":true,"data":[
                 {"id":37382,"name":"Super Mario Bros.","verified":true,"types":["eshop"],"release_date":495417600},
                 {"id":5258966,"name":"Super Mario Bros Z: The Game","verified":false,"types":[],"release_date":1497312000}
               ]}""",
        )!!
        assertEquals(2, games.size)
        assertEquals(37382L, games[0].id)
        assertEquals("Super Mario Bros.", games[0].name)
        assertTrue(games[0].verified)
        assertEquals(1985, games[0].releaseYear) // 495417600 = 1985-09-13 UTC
        assertEquals(2017, games[1].releaseYear)
        assertFalse(games[1].verified)
    }

    @Test
    fun `un juego sin fecha no tiene ano`() {
        val games = SteamGridDbParser.parseSearch("""{"success":true,"data":[{"id":1,"name":"Sin fecha","verified":true}]}""")!!
        assertNull(games.single().releaseYear)
        val nullDate = SteamGridDbParser.parseSearch("""{"success":true,"data":[{"id":1,"name":"X","release_date":null}]}""")!!
        assertNull(nullDate.single().releaseYear)
    }

    @Test
    fun `busqueda sin resultados es una lista vacia, no un error`() {
        assertEquals(emptyList<SteamGridDbGame>(), SteamGridDbParser.parseSearch("""{"success":true,"data":[]}"""))
    }

    @Test
    fun `respuestas de error o ilegibles dan null`() {
        assertNull(SteamGridDbParser.parseSearch("""{"success":false,"errors":["Invalid key format"]}"""))
        assertNull(SteamGridDbParser.parseSearch("<html>502 Bad Gateway</html>"))
        assertNull(SteamGridDbParser.parseImages("""{"success":false,"status":404,"errors":["Game not found"]}""", isGrid = true))
    }

    @Test
    fun `lee las imagenes y marca si vienen de grids o de icons`() {
        val text = """{"success":true,"page":0,"total":2,"limit":50,"data":[
            {"id":672112,"score":0,"style":"alternate","width":512,"height":512,"nsfw":false,"humor":false,
             "mime":"image/png","language":"en","url":"https://cdn2.steamgriddb.com/grid/abc.png","thumb":"https://cdn2.steamgriddb.com/thumb/abc.jpg"},
            {"id":999,"score":3,"style":"official","width":0,"height":0,"mime":"image/vnd.microsoft.icon","language":"en","url":"https://cdn2.steamgriddb.com/icon/x.ico"},
            {"id":1000,"width":512,"height":512,"mime":"image/png"}
        ]}"""
        val grids = SteamGridDbParser.parseImages(text, isGrid = true)!!
        assertEquals(2, grids.size) // el que no tiene url se descarta
        assertEquals(512, grids[0].width)
        assertEquals("image/png", grids[0].mime)
        assertEquals("alternate", grids[0].style)
        assertEquals("en", grids[0].language)
        assertTrue(grids[0].isGrid && grids[0].isSquare)
        assertFalse(SteamGridDbParser.parseImages(text, isGrid = false)!!.first().isGrid)
        assertFalse(grids[1].isSquare) // 0x0 no es cuadrada
    }
}
