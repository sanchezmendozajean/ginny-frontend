package com.ginny.frontend.data.scrap

import com.ginny.frontend.data.model.SystemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapScopeTest {

    private val stats = SystemScrapStats(
        systemId = SystemId.NES,
        total = 5,
        pendingGames = setOf("a.nes", "b.nes"),
        notFoundGames = setOf("c.nes"),
    )

    @Test
    fun `cuenta los juegos segun el alcance`() {
        assertEquals(2, stats.countFor(ScrapScope.PENDING))
        assertEquals(1, stats.countFor(ScrapScope.NOT_FOUND))
        assertEquals(5, stats.countFor(ScrapScope.ALL))
    }

    @Test
    fun `decide si un juego entra en el lote`() {
        assertTrue(stats.includes(ScrapScope.PENDING, "a.nes"))
        assertFalse(stats.includes(ScrapScope.PENDING, "c.nes"))
        assertTrue(stats.includes(ScrapScope.NOT_FOUND, "c.nes"))
        assertTrue(stats.includes(ScrapScope.ALL, "cualquiera.nes"))
    }

    @Test
    fun `solo pendientes respeta las caches y los otros alcances las ignoran`() {
        assertFalse(ScrapScope.PENDING.force)
        assertTrue(ScrapScope.NOT_FOUND.force)
        assertTrue(ScrapScope.ALL.force)
    }

    @Test
    fun `el resumen de imagenes ignora el video`() {
        val base = ScrapCurrent(1, "a.nes", "NES")
        assertNull(base.images)
        assertEquals(true, base.copy(media = mapOf(ScrapMedia.ICON to true, ScrapMedia.VIDEO to false)).images)
        assertEquals(false, base.copy(media = mapOf(ScrapMedia.ICON to true, ScrapMedia.WHEEL to false)).images)
        assertNull(base.copy(media = mapOf(ScrapMedia.ICON to true, ScrapMedia.WHEEL to null)).images)
    }
}
