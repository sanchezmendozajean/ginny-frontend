package com.ginny.frontend.data.scrap

import org.junit.Assert.assertEquals
import org.junit.Test

class IconSlotsTest {

    @Test
    fun `usa el primer numero libre`() {
        assertEquals(1, IconSlots.slotForNew(emptyList(), activeIndex = null))
        assertEquals(3, IconSlots.slotForNew(listOf(1 to 10L, 2 to 20L), activeIndex = 1))
        // Un hueco en medio se reutiliza antes que crecer.
        assertEquals(2, IconSlots.slotForNew(listOf(1 to 10L, 3 to 30L), activeIndex = null))
    }

    @Test
    fun `lleno reemplaza el mas antiguo que no es el que se muestra`() {
        val full = listOf(1 to 50L, 2 to 10L, 3 to 40L, 4 to 30L, 5 to 20L)
        assertEquals(2, IconSlots.slotForNew(full, activeIndex = null))
        // El más antiguo es el que se muestra: se salta y toma el siguiente más antiguo.
        assertEquals(5, IconSlots.slotForNew(full, activeIndex = 2))
    }

    @Test
    fun `el limite por juego es 5`() {
        assertEquals(5, MAX_ICON_ALTERNATIVES)
        assertEquals(5, IconSlots.slotForNew(listOf(1 to 1L, 2 to 2L, 3 to 3L, 4 to 4L), activeIndex = null))
    }
}
