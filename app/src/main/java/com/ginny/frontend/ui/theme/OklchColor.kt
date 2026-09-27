package com.ginny.frontend.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * OKLCH -> sRGB conversion, ported from Björn Ottosson's OKLab reference matrices
 * (https://bottosson.github.io/posts/oklab/). Pure/side-effect-free so it can be reasoned about
 * and unit-tested independently of Compose, same spirit as [com.ginny.frontend.ui.components.FocusGridMath].
 * [l] is 0..1, [c] is chroma (unbounded but practically ~0..0.4), [hDeg] is hue in degrees.
 * Mirrors the CSS `oklch(l c h)` function used throughout the visual reference (docs/diseño).
 */
fun oklch(l: Float, c: Float, hDeg: Float, alpha: Float = 1f): Color {
    val hRad = hDeg * (PI / 180.0)
    val a = c * cos(hRad).toFloat()
    val b = c * sin(hRad).toFloat()

    val l_ = l + 0.3963377774f * a + 0.2158037573f * b
    val m_ = l - 0.1055613458f * a - 0.0638541728f * b
    val s_ = l - 0.0894841775f * a - 1.2914855480f * b

    val lCube = l_ * l_ * l_
    val mCube = m_ * m_ * m_
    val sCube = s_ * s_ * s_

    val rLin = +4.0767416621f * lCube - 3.3077115913f * mCube + 0.2309699292f * sCube
    val gLin = -1.2684380046f * lCube + 2.6097574011f * mCube - 0.3413193965f * sCube
    val bLin = -0.0041960863f * lCube - 0.7034186147f * mCube + 1.7076147010f * sCube

    return Color(
        red = linearToSrgb(rLin),
        green = linearToSrgb(gLin),
        blue = linearToSrgb(bLin),
        alpha = alpha,
    )
}

private fun linearToSrgb(value: Float): Float {
    val v = value.coerceIn(0f, 1f)
    return if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
}

/**
 * Approximates the reference's `linear-gradient(135deg, stopA, stopB)` — Compose has no
 * angle-based gradient primitive, so a corner-to-corner (top-left -> bottom-right) diagonal via
 * [Offset.Zero]/[Offset.Infinite] is the closest built-in equivalent and reads the same at the
 * tile/badge sizes this is used at.
 */
fun diagonalGradient(colors: List<Color>): Brush =
    Brush.linearGradient(colors, start = Offset.Zero, end = Offset.Infinite)

fun diagonalGradient(colorA: Color, colorB: Color): Brush = diagonalGradient(listOf(colorA, colorB))
