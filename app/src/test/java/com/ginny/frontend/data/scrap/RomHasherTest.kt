package com.ginny.frontend.data.scrap

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RomHasherTest {

    @Test
    fun `hashes del texto abc (vectores de referencia)`() {
        val hashes = RomHasher.compute(ByteArrayInputStream("abc".toByteArray()))
        assertEquals("352441C2", hashes.crc32)
        assertEquals("900150983CD24FB0D6963F7D28E17F72", hashes.md5)
        assertEquals("A9993E364706816ABA3E25717850C26C9CD0D89D", hashes.sha1)
    }

    @Test
    fun `un archivo vacio`() {
        val hashes = RomHasher.compute(ByteArrayInputStream(ByteArray(0)))
        assertEquals("00000000", hashes.crc32) // con ceros a la izquierda
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", hashes.md5)
        assertEquals("DA39A3EE5E6B4B0D3255BFEF95601890AFD80709", hashes.sha1)
    }

    @Test
    fun `da lo mismo leer de una vez que en trozos mas grandes que el buffer`() {
        val data = ByteArray(300_000) { (it * 31 % 256).toByte() }
        val whole = RomHasher.compute(ByteArrayInputStream(data))
        // Un InputStream que entrega de a 7 bytes obliga a varias lecturas parciales.
        val chunked = RomHasher.compute(object : java.io.InputStream() {
            private val source = ByteArrayInputStream(data)
            override fun read(): Int = source.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, minOf(len, 7))
        })
        assertEquals(whole, chunked)
    }
}
