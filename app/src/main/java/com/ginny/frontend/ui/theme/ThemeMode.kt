package com.ginny.frontend.ui.theme

/**
 * Opción de tema elegida por el usuario (persistida en SettingsRepository por su nombre: no
 * renombrar las constantes). Sus colores viven en [GinnyThemes.paletteFor]. [CICLO] no tiene
 * paleta propia: pasa por todos los demás temas, uno tras otro (ver ThemeCycle.kt).
 */
enum class ThemeMode(val displayName: String) {
    MENTA("Menta"),
    CIELO("Cielo"),
    LILA("Lila"),
    FUCSIA("Fucsia"),
    ROSA("Rosa"),
    LAVA("Lava"),
    DURAZNO("Durazno"),
    SMOG("Smog"),
    CICLO("Cambio cíclico"),
}
