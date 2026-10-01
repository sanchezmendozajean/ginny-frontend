package com.ginny.frontend.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Modificadores de color en el espacio OKLCH (el mismo del `oklch()` de CSS):
 *   L = luminosidad (0 negro … 1 blanco)
 *   C = saturación  (0 gris … ~0.37 lo más vivo)
 *   H = tono en grados (0…360)
 * A diferencia de HSL, en OKLCH dos colores con la misma L se perciben igual de claros aunque
 * tengan distinto tono — por eso los 4 temas se ven "parejos" usando la misma receta.
 * Conversión según Björn Ottosson, https://bottosson.github.io/posts/oklab/
 */

/** Un color descompuesto en sus tres componentes OKLCH. */
data class Oklch(val l: Float, val c: Float, val h: Float, val alpha: Float = 1f) {
    fun toColor(): Color {
        val hRad = h * (PI / 180.0)
        val a = c * cos(hRad).toFloat()
        val b = c * sin(hRad).toFloat()

        val l_ = l + 0.3963377774f * a + 0.2158037573f * b
        val m_ = l - 0.1055613458f * a - 0.0638541728f * b
        val s_ = l - 0.0894841775f * a - 1.2914855480f * b

        val lc = l_ * l_ * l_
        val mc = m_ * m_ * m_
        val sc = s_ * s_ * s_

        return Color(
            red = linearToSrgb(+4.0767416621f * lc - 3.3077115913f * mc + 0.2309699292f * sc),
            green = linearToSrgb(-1.2684380046f * lc + 2.6097574011f * mc - 0.3413193965f * sc),
            blue = linearToSrgb(-0.0041960863f * lc - 0.7034186147f * mc + 1.7076147010f * sc),
            alpha = alpha,
        )
    }
}

fun Color.toOklch(): Oklch {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)

    val l_ = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m_ = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s_ = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)

    val lightness = 0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_
    val labA = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_
    val labB = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_

    val hue = (atan2(labB, labA) * (180.0 / PI)).toFloat().let { if (it < 0f) it + 360f else it }
    return Oklch(lightness, sqrt(labA * labA + labB * labB), hue, alpha)
}

// ── Modificadores ─────────────────────────────────────────────────────────────────────────

/**
 * Cambia luminosidad y/o saturación **en un solo paso**, manteniendo el tono. Preferir esto a
 * encadenar `withLightness(..).withChroma(..)`: el paso intermedio puede caer fuera de lo que la
 * pantalla puede mostrar, se recorta, y el tono se desvía.
 */
fun Color.adjust(lightness: Float? = null, chroma: Float? = null): Color {
    val o = toOklch()
    return o.copy(
        l = (lightness ?: o.l).coerceIn(0f, 1f),
        c = (chroma ?: o.c).coerceAtLeast(0f),
    ).toColor()
}

/** Mismo tono y saturación, con luminosidad [l] (0 negro … 1 blanco). */
fun Color.withLightness(l: Float): Color = toOklch().copy(l = l.coerceIn(0f, 1f)).toColor()

/** Mismo tono y luminosidad, con saturación [c] (0 = gris). */
fun Color.withChroma(c: Float): Color = toOklch().copy(c = c.coerceAtLeast(0f)).toColor()

/** Oscurece restando [amount] a la luminosidad (ej. 0.1f = un 10% más oscuro). */
fun Color.darken(amount: Float): Color = toOklch().let { withLightness(it.l - amount) }

/** Aclara sumando [amount] a la luminosidad. */
fun Color.lighten(amount: Float): Color = toOklch().let { withLightness(it.l + amount) }

/** Gira el tono [degrees] grados en la rueda de color, sin tocar luminosidad ni saturación. */
fun Color.rotateHue(degrees: Float): Color = toOklch().let { it.copy(h = (it.h + degrees).mod(360f)) }.toColor()

/** El color opuesto en la rueda (tono + 180°): Menta → fucsia, Cielo → durazno, etc. */
fun Color.complementary(): Color = rotateHue(180f)

// ── Conversión sRGB ↔ lineal ──────────────────────────────────────────────────────────────

private fun srgbToLinear(v: Float): Float =
    if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(value: Float): Float {
    val v = value.coerceIn(0f, 1f)
    return if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
}
