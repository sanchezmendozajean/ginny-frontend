package com.ginny.frontend.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GinnyColorScheme = lightColorScheme(
    primary = GinnyColors.AccentBlue,
    secondary = GinnyColors.AccentCeleste,
    error = GinnyColors.AccentRed,
    background = GinnyColors.BgPage,
    surface = GinnyColors.CardBg,
    onBackground = GinnyColors.TextPrimary,
    onSurface = GinnyColors.TextPrimary,
    onPrimary = GinnyColors.TextInverse,
)

object GinnyShapes {
    val radiusLarge = 22
    val radiusMedium = 16
    val radiusSmall = 10
}

/**
 * App theme. Deliberately light-only for now: the aesthetic direction (3DS/Wii U inspired "soft
 * UI") is defined as a bright, luminous look — see docs/prompt-frontend-emuladores-3ds.md,
 * "Dirección estética". [isSystemInDarkTheme] is read but currently ignored; wire up a dark
 * palette here if/when the "Tema visual" Settings entry grows beyond a placeholder.
 */
@Composable
fun GinnyFrontendTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = GinnyColorScheme,
        typography = GinnyTypography,
        content = content,
    )
}
