package com.ginny.frontend.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ginny.frontend.data.SettingsRepository

/**
 * Lee el tema elegido en Configuración ([SettingsRepository.themeMode]) y lo entrega a todo
 * [content] vía [LocalGinnyPalette]. Dentro, cualquier composable lo obtiene con
 * `GinnyThemes.current`. Si el usuario cambia el tema, todo lo que lo lee se redibuja solo.
 */
@Composable
fun GinnyTheme(settingsRepository: SettingsRepository, content: @Composable () -> Unit) {
    // Menta mientras DataStore responde (una fracción de segundo al abrir la app).
    val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.MENTA)

    val palette = if (themeMode == ThemeMode.CICLO) {
        // El ciclo solo corre mientras está elegido: al cambiar de modo, esta rama sale de la
        // composición y su corrutina se cancela.
        rememberCyclingPalette().value
    } else {
        GinnyThemes.paletteFor(themeMode)
    }

    CompositionLocalProvider(LocalGinnyPalette provides palette, content = content)
}
