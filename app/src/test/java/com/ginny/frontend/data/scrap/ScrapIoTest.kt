package com.ginny.frontend.data.scrap

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ScrapIoTest {

    @Test
    fun `copia todo si cabe en el tope`() {
        val data = ByteArray(100_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        assertEquals(100_000L, ScrapIo.copyLimited(ByteArrayInputStream(data), out, maxBytes = 100_000))
        assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun `se detiene y avisa con null si supera el tope`() {
        val out = ByteArrayOutputStream()
        assertNull(ScrapIo.copyLimited(ByteArrayInputStream(ByteArray(100_000)), out, maxBytes = 50_000))
        // Nunca escribe de mas: a lo sumo lo que cabia antes de pasarse.
        assert(out.size() <= 50_000)
    }

    @Test
    fun `un origen vacio copia cero bytes`() {
        assertEquals(0L, ScrapIo.copyLimited(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), maxBytes = 10))
    }
}
