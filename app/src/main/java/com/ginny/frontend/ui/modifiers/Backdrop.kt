package com.ginny.frontend.ui.modifiers

import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * Conecta el fondo que se desenfoca (`Modifier.haze(state)`, en GinnyApp) con los elementos que
 * lo desenfocan detrás de sí (`Modifier.hazeChild(state)`, en Container). Mismo mecanismo que
 * [com.ginny.frontend.ui.theme.LocalGinnyPalette]. null = no hay fondo desenfocable (ej. @Preview).
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
