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

    CompositionLocalProvider(
        LocalGinnyPalette provides GinnyThemes.paletteFor(themeMode),
        content = content,
    )
}
