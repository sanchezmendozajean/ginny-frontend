package com.ginny.frontend.data.scrap

import org.junit.Assert.assertEquals
import org.junit.Test

class IconSizingTest {

    @Test
    fun `lo grande se reduce a 512 y lo chico no se agranda`() {
        assertEquals(512, IconSizing.finalSide(1024, 512))
        assertEquals(512, IconSizing.finalSide(512, 512))
        assertEquals(256, IconSizing.finalSide(256, 512))
        assertEquals(16, IconSizing.finalSide(16, 512))
    }

    @Test
    fun `la decodificacion nunca baja del lado final`() {
        assertEquals(1, IconSizing.sampleSize(512, 512))
        assertEquals(1, IconSizing.sampleSize(1023, 512)) // 1023/2 = 511 < 512: no se puede dividir
        assertEquals(2, IconSizing.sampleSize(1024, 512))
        assertEquals(4, IconSizing.sampleSize(2048, 512))
        assertEquals(1, IconSizing.sampleSize(256, 512))
    }

    @Test
    fun `lo decodificado siempre alcanza para el lado final`() {
        for (side in listOf(513, 700, 1024, 1500, 2048, 4096)) {
            val decoded = side / IconSizing.sampleSize(side, 512)
            assert(decoded >= 512) { "$side → $decoded" }
        }
    }
}
