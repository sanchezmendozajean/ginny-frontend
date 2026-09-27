package com.ginny.frontend.ui.theme

/**
 * Opción de tema elegida por el usuario (persistida en SettingsRepository). Sus colores viven en
 * [GinnyThemes.paletteFor]. [CICLO] no tiene [hue] fijo: rotaba entre los otros 4 en el diseño
 * anterior (docs/ui-legacy/ui/theme/CycleHue.kt) y todavía no está reimplementado.
 */
enum class ThemeMode(val displayName: String, val hue: Float?) {
    MENTA("Menta", 168f),
    CIELO("Cielo", 240f),
    DURAZNO("Durazno", 45f),
    LILA("Lila", 295f),
    CICLO("Ciclo", null),
}
