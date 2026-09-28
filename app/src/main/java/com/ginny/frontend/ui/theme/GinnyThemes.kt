package com.ginny.frontend.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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
 * La receta: todos los papeles salen de un único color [base], cambiando su luminosidad y
 * saturación (ver ColorModifiers.kt). Ajustar un número aquí cambia ese papel en los 4 temas a
 * la vez, y todos siguen igual de parejos entre sí.
 */
fun paletteFrom(base: Color) = GinnyPalette(
    background = base.adjust(lightness = 0.955f, chroma = 0.022f), // muy claro, apenas teñido
    accent = base,                                                 // el color base tal cual
    // Muy oscuro, con color. 0.06 = la máxima saturación que Menta admite con L=0.3; más alto
    // se recorta de forma distinta en cada tema (Cielo se vuelve azul puro, Durazno rojo).
    ink = base.adjust(lightness = 0.3f, chroma = 0.06f),
    muted = base.adjust(lightness = 0.52f, chroma = 0.03f),        // medio, grisáceo
)

/**
 * Los 4 temas fijos: solo se define el color base de cada uno. Son los acentos del diseño
 * anterior (docs/ui-legacy/ui/theme/GinnyPalette.kt), todos con L=0.70 y C=0.14 en OKLCH.
 */
object GinnyThemes {
    val Menta = paletteFrom(Color(0xFF0DB98E))
    val Cielo = paletteFrom(Color(0xFF37A8EC))
    val Durazno = paletteFrom(Color(0xFFE57F4F))
    val Lila = paletteFrom(Color(0xFFA58BEC))

    /** Traduce la opción guardada en Configuración a su paleta. */
    fun paletteFor(mode: ThemeMode): GinnyPalette = when (mode) {
        ThemeMode.MENTA -> Menta
        ThemeMode.CIELO -> Cielo
        ThemeMode.DURAZNO -> Durazno
        ThemeMode.LILA -> Lila
        // Ciclo todavía no está implementado: por ahora se queda en Menta.
        ThemeMode.CICLO -> Menta
    }

    /**
     * La paleta del tema actual, desde cualquier composable: `val palette = GinnyThemes.current`.
     * La define [GinnyApp][com.ginny.frontend.ui.GinnyApp] con `CompositionLocalProvider`.
     */
    val current: GinnyPalette
        @Composable @ReadOnlyComposable
        get() = LocalGinnyPalette.current
}

/**
 * "Canal" por el que el tema actual baja a todos los composables sin pasarlo como parámetro.
 * Menta es el valor si nadie lo definió (ej. en un @Preview).
 */
val LocalGinnyPalette = staticCompositionLocalOf { GinnyThemes.Menta }
