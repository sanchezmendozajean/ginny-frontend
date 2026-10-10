package com.ginny.frontend.data.scrap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ginny.frontend.data.scrap.steamgriddb.SteamGridDbSelector
import java.io.ByteArrayOutputStream

/** Cuentas de tamaño del ícono, sin Android (para poder probarlas). */
object IconSizing {
    /** Lado final: el del original si es menor que [target] (**nunca se agranda**), si no [target]. */
    fun finalSide(side: Int, target: Int): Int = minOf(side, target)

    /**
     * Potencia de 2 con la que decodificar para no cargar en memoria mucho más de lo necesario, sin
     * bajar nunca del lado [target] (la reducción final se hace con filtro, más fina).
     */
    fun sampleSize(side: Int, target: Int): Int {
        var sample = 1
        while (side / (sample * 2) >= target) sample *= 2
        return sample
    }
}

/** Deja el ícono como lo pide la convención de `Assets/<sistema>/icons/`: un PNG cuadrado de hasta [TARGET_SIDE]. */
object IconProcessor {

    const val TARGET_SIDE = SteamGridDbSelector.TARGET_SIZE

    /**
     * Decodifica [source] (PNG, JPEG o WebP), lo **reduce** a [target] si es más grande (nunca lo
     * agranda) y lo devuelve como PNG. Falla si no se puede leer o no es cuadrado.
     */
    fun toSquarePng(source: ByteArray, target: Int = TARGET_SIDE): ScrapResult<ByteArray> {
        val provider = ScrapProvider.STEAMGRIDDB

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return ScrapResult.Unavailable(provider, "imagen ilegible")
        if (width != height) return ScrapResult.Unavailable(provider, "la imagen no es cuadrada (${width}×$height)")

        val options = BitmapFactory.Options().apply { inSampleSize = IconSizing.sampleSize(width, target) }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, options)
            ?: return ScrapResult.Unavailable(provider, "imagen ilegible")

        val side = IconSizing.finalSide(decoded.width, target)
        val bitmap = if (decoded.width > side) {
            Bitmap.createScaledBitmap(decoded, side, side, true).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        return try {
            val png = ByteArrayOutputStream()
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)) {
                ScrapResult.Ok(png.toByteArray())
            } else {
                ScrapResult.Unavailable(provider, "no se pudo convertir a PNG")
            }
        } finally {
            bitmap.recycle()
        }
    }
}
