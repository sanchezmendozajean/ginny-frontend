package com.ginny.frontend.data.scrap

import okhttp3.ResponseBody
import java.io.InputStream
import java.io.OutputStream

/** Copia de descargas a un destino, con tope de tamaño (protege el almacenamiento de una respuesta enorme o corrupta). */
object ScrapIo {

    /**
     * Copia [input] a [out] hasta [maxBytes]. Devuelve los bytes copiados, o null si el origen tiene
     * más: en ese caso **ya se escribió una parte** en [out], y quien lo llamó debe descartar el destino.
     */
    fun copyLimited(input: InputStream, out: OutputStream, maxBytes: Long): Long? {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
    }

    /**
     * Copia el cuerpo de una respuesta a [out]. Si ya se sabe por `Content-Length` que supera
     * [maxBytes], ni empieza (no se escribe nada); si lo descubre a mitad, devuelve el mismo fallo.
     */
    fun copyBody(provider: ScrapProvider, body: ResponseBody, out: OutputStream, maxBytes: Long): ScrapResult<Long> {
        if (body.contentLength() > maxBytes) return tooLarge(provider)
        val copied = body.byteStream().use { copyLimited(it, out, maxBytes) } ?: return tooLarge(provider)
        return ScrapResult.Ok(copied)
    }

    private fun tooLarge(provider: ScrapProvider) = ScrapResult.Unavailable(provider, "el archivo supera el tamaño máximo permitido")
}
