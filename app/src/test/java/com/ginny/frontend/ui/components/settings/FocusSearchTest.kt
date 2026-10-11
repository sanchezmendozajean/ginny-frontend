package com.ginny.frontend.ui.components.settings

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusSearchTest {

    private val tolerance = 10f

    /** Un control de [width] × 40 con su esquina superior izquierda en ([x], [y]). */
    private fun rect(x: Float, y: Float, width: Float = 100f) = Rect(x, y, x + width, y + 40f)

    // Fila 0: un control a la derecha (y=0). Fila 1: dos botones (y=60). Fila 2: un control ancho (y=120).
    private val rects = listOf(
        rect(300f, 0f),          // 0
        rect(200f, 60f),         // 1  Restablecer
        rect(320f, 60f),         // 2  Cambiar
        rect(150f, 120f, 270f),  // 3
    )

    @Test
    fun `abajo va a la fila siguiente y elige el control de centro horizontal mas cercano`() {
        // Desde el control 0 (centro x=350): en la fila de abajo, 2 (centro 370) queda más cerca que 1 (250).
        assertEquals(2, FocusSearch.next(rects, 0, FocusMove.Down, tolerance))
        // Desde 1 (centro 250) baja al único de la fila siguiente.
        assertEquals(3, FocusSearch.next(rects, 1, FocusMove.Down, tolerance))
    }

    @Test
    fun `arriba va a la fila anterior`() {
        assertEquals(0, FocusSearch.next(rects, 2, FocusMove.Up, tolerance))
    }

    @Test
    fun `arriba desde un control ancho elige el mas cercano en horizontal`() {
        // Centro del 3 = 150 + 135 = 285; el 1 está a 35 y el 2 a 85.
        assertEquals(1, FocusSearch.next(rects, 3, FocusMove.Up, tolerance))
    }

    @Test
    fun `izquierda y derecha se quedan en la fila`() {
        assertEquals(2, FocusSearch.next(rects, 1, FocusMove.Right, tolerance))
        assertEquals(1, FocusSearch.next(rects, 2, FocusMove.Left, tolerance))
        assertNull(FocusSearch.next(rects, 2, FocusMove.Right, tolerance))
        assertNull(FocusSearch.next(rects, 0, FocusMove.Left, tolerance)) // solo, en su fila
    }

    @Test
    fun `en los bordes no hay a donde ir`() {
        assertNull(FocusSearch.next(rects, 0, FocusMove.Up, tolerance))
        assertNull(FocusSearch.next(rects, 3, FocusMove.Down, tolerance))
    }

    @Test
    fun `controles con diferencias pequenas de altura son de la misma fila`() {
        val row = listOf(rect(0f, 0f), rect(200f, 4f)) // 4 px de diferencia < tolerancia
        assertEquals(1, FocusSearch.next(row, 0, FocusMove.Right, tolerance))
        assertNull(FocusSearch.next(row, 0, FocusMove.Down, tolerance))
    }
}
