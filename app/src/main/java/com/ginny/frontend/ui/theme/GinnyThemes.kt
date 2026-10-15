package com.ginny.frontend.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
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
 * Los temas fijos: solo se define el color base de cada uno.
 * - Menta, Cielo, Durazno, Lila: acentos del diseño original, todos con L=0.70 y C=0.14 en OKLCH.
 * - Fucsia, Rosa, Lava, Smog: nuevos, con su propio carácter (entre paréntesis, su OKLCH):
 *   Fucsia vivo (0.64 0.22 345), Rosa suave y claro (0.76 0.11 5), Lava rojo anaranjado intenso
 *   (0.64 0.19 35, más rojo y saturado que Durazno) y Smog gris apenas azulado (0.66 0.012 255).
 */
object GinnyThemes {
    val Menta = paletteFrom(Color(0xFF0DB98E))
    val Cielo = paletteFrom(Color(0xFF37A8EC))
    val Durazno = paletteFrom(Color(0xFFE57F4F))
    val Lila = paletteFrom(Color(0xFFA58BEC))
    val Fucsia = paletteFrom(Color(0xFFE141AA))
    val Rosa = paletteFrom(Color(0xFFEC93A8))
    val Lava = paletteFrom(Color(0xFFE8542F))
    val Smog = paletteFrom(Color(0xFF8D939A))

    /** Traduce un tema fijo a su paleta. [ThemeMode.CICLO] no tiene una fija: ver ThemeCycle.kt. */
    fun paletteFor(mode: ThemeMode): GinnyPalette = when (mode) {
        ThemeMode.MENTA -> Menta
        ThemeMode.CIELO -> Cielo
        ThemeMode.LILA -> Lila
        ThemeMode.FUCSIA -> Fucsia
        ThemeMode.ROSA -> Rosa
        ThemeMode.LAVA -> Lava
        ThemeMode.DURAZNO -> Durazno
        ThemeMode.SMOG -> Smog
        ThemeMode.CICLO -> Menta // punto de partida del ciclo
    }

    /** Los temas fijos, en el orden del desplegable y del ciclo (todos menos [ThemeMode.CICLO]). */
    val fixed: List<ThemeMode> = ThemeMode.entries.filter { it != ThemeMode.CICLO }

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
 *
 * compositionLocalOf (y no static…): en el cambio cíclico la paleta cambia en cada cuadro durante
 * la transición; así solo se recomponen los composables que leen el color, no toda la app.
 */
val LocalGinnyPalette = compositionLocalOf { GinnyThemes.Menta }
