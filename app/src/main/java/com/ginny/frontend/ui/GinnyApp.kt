package com.ginny.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Raíz de la interfaz, reconstruida paso a paso (ver docs/plan-reconstruccion-ui.md).
 * La implementación anterior completa está archivada en docs/ui-legacy/ como referencia.
 *
 * Fondo: bg0 del tema Menta del diseño anterior — oklch(0.955, 0.022, 168) ≈ #E3F5ED.
 */
@Composable
fun GinnyApp() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE3F5ED)),
    )
}
