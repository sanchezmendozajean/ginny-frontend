package com.ginny.frontend.data.scrap

import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/** Los tres hashes que ScreenScraper acepta para identificar una ROM, en hexadecimal y mayúsculas. */
data class RomHashes(val crc32: String, val md5: String, val sha1: String)

object RomHasher {

    /** Calcula CRC32, MD5 y SHA-1 de [input] de una sola pasada (no lo cierra). */
    fun compute(input: InputStream): RomHashes {
        val crc = CRC32()
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            crc.update(buffer, 0, read)
            md5.update(buffer, 0, read)
            sha1.update(buffer, 0, read)
        }
        return RomHashes(
            crc32 = "%08X".format(crc.value),
            md5 = md5.digest().toHex(),
            sha1 = sha1.digest().toHex(),
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
