package com.ginny.frontend.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Every color the new visual language uses is derived from a single hue — a direct port of the
 * `renderVals()` `c` object in docs/diseño/Ginny Frontend.dc.html. Regenerating the whole palette
 * from one [Float] hue is what makes the 4 fixed themes AND the animated "Ciclo" mode (which just
 * varies the hue over time, see [ThemeMode]) fall out of the same code path.
 */
data class GinnyPalette(
    val hue: Float,
    val accent: Color,
    val accentInk: Color,
    val grad: Brush,
    val ink: Color,
    val muted: Color,
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val dot: Color,
    val shadow: Color,
    val glow: Color,
    val chip: Color,
    val line: Color,
    val track: Color,
    val rail: Color,
    val scrim: Color,
)

fun ginnyPalette(hueDegrees: Float): GinnyPalette {
    val h = hueDegrees
    return GinnyPalette(
        hue = h,
        accent = oklch(0.7f, 0.14f, h),
        accentInk = oklch(0.47f, 0.12f, h),
        grad = diagonalGradient(oklch(0.78f, 0.12f, h + 35f), oklch(0.68f, 0.15f, h)),
        ink = oklch(0.29f, 0.03f, h),
        muted = oklch(0.52f, 0.03f, h),
        bg0 = oklch(0.955f, 0.022f, h),
        bg1 = oklch(0.89f, 0.075f, h),
        bg2 = oklch(0.925f, 0.05f, h + 40f),
        dot = oklch(0.8f, 0.04f, h, alpha = 0.5f),
        shadow = oklch(0.45f, 0.08f, h, alpha = 0.16f),
        glow = oklch(0.7f, 0.16f, h, alpha = 0.5f),
        chip = oklch(0.965f, 0.015f, h),
        line = oklch(0.91f, 0.02f, h),
        track = oklch(0.92f, 0.02f, h),
        rail = oklch(0.97f, 0.015f, h),
        scrim = oklch(0.35f, 0.05f, h, alpha = 0.3f),
    )
}

/** Default before the real theme loads from DataStore — Menta's hue (see [ThemeMode]). */
val LocalGinnyPalette = staticCompositionLocalOf { ginnyPalette(168f) }
