package com.ginny.frontend.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.ginny.frontend.data.SettingsRepository

object GinnyShapes {
    val radiusLarge = 22
    val radiusMedium = 16
    val radiusSmall = 10
}

/**
 * App theme. Every color in the app derives from a single hue via [ginnyPalette] — see
 * [LocalGinnyPalette]. [ThemeMode.CICLO] drives that hue with [rememberCyclingHue] instead of a
 * fixed value; every other mode just reads its own fixed hue. The Material3 [lightColorScheme] is
 * kept in sync (derived from the same palette) purely so stock Material widgets (Slider, Switch,
 * etc.) that read [MaterialTheme.colorScheme] directly don't look out of place.
 */
@Composable
fun GinnyFrontendTheme(settingsRepository: SettingsRepository, content: @Composable () -> Unit) {
    val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.MENTA)
    // Only runs the per-frame animation clock while Ciclo is actually selected, so a fixed theme
    // never recomposes every frame for nothing (this app deliberately avoids that kind of always-on
    // animation cost — see docs/architecture.md's battery/perf notes for low-end retro-console hardware).
    val hue = if (themeMode == ThemeMode.CICLO) rememberCyclingHue().value else themeMode.hue!!
    val palette = remember(hue) { ginnyPalette(hue) }

    val colorScheme = remember(palette) {
        lightColorScheme(
            primary = palette.accent,
            secondary = palette.accent,
            error = oklch(0.6f, 0.19f, 25f),
            background = palette.bg0,
            surface = Color.White,
            onBackground = palette.ink,
            onSurface = palette.ink,
            onPrimary = Color.White,
        )
    }

    CompositionLocalProvider(LocalGinnyPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GinnyTypography,
            content = content,
        )
    }
}
