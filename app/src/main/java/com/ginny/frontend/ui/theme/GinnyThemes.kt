package com.ginny.frontend.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Los colores de un tema, cada uno con su papel en la interfaz. Los componentes piden un papel
 * (`palette.background`), nunca un tema concreto, así cambiar de tema no obliga a tocarlos.
 */
data class GinnyPalette(
    /** Fondo general de la pantalla. */
    val background: Color,
    /** Color de acento: foco, botones principales, resaltados. */
    val accent: Color,
    /** Texto principal. */
    val ink: Color,
    /** Texto secundario / menos importante. */
    val muted: Color,
)

/**
 * Los 4 temas fijos. Valores calculados con las mismas fórmulas OKLCH del diseño anterior
 * (docs/ui-legacy/ui/theme/GinnyPalette.kt), con el hue de cada [ThemeMode].
 */
object GinnyThemes {
    val Menta = GinnyPalette(
        background = Color(0xFFE3F5ED),
        accent = Color(0xFF0DB98E),
        ink = Color(0xFF1C3028),
        muted = Color(0xFF596F66),
    )
    val Cielo = GinnyPalette(
        background = Color(0xFFE4F3FE),
        accent = Color(0xFF37A8EC),
        ink = Color(0xFF1D2D39),
        muted = Color(0xFF5A6C79),
    )
    val Durazno = GinnyPalette(
        background = Color(0xFFFEECE4),
        accent = Color(0xFFE57F4F),
        ink = Color(0xFF38271F),
        muted = Color(0xFF78645B),
    )
    val Lila = GinnyPalette(
        background = Color(0xFFF1EEFE),
        accent = Color(0xFFA58BEC),
        ink = Color(0xFF2C2839),
        muted = Color(0xFF6A6679),
    )

    /** Traduce la opción guardada en Configuración a su paleta. */
    fun paletteFor(mode: ThemeMode): GinnyPalette = when (mode) {
        ThemeMode.MENTA -> Menta
        ThemeMode.CIELO -> Cielo
        ThemeMode.DURAZNO -> Durazno
        ThemeMode.LILA -> Lila
        // Ciclo todavía no está implementado: por ahora se queda en Menta.
        ThemeMode.CICLO -> Menta
    }
}
