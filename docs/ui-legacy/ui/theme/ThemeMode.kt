package com.ginny.frontend.ui.theme

/**
 * The 4 fixed hues match the mockup's Menta/Cielo/Durazno/Lila themes 1:1. [CICLO] has no fixed
 * [hue] — [com.ginny.frontend.ui.theme.cycleHue] animates through the other 4 over time instead.
 */
enum class ThemeMode(val displayName: String, val hue: Float?) {
    MENTA("Menta", 168f),
    CIELO("Cielo", 240f),
    DURAZNO("Durazno", 45f),
    LILA("Lila", 295f),
    CICLO("Ciclo", null),
}
