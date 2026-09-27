package com.ginny.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ginny.frontend.ui.theme.GinnyThemes

/**
 * Raíz de la interfaz, reconstruida paso a paso (ver docs/plan-reconstruccion-ui.md).
 * La implementación anterior completa está archivada en docs/ui-legacy/ como referencia.
 */
@Composable
fun GinnyApp() {
    val palette = GinnyThemes.Menta

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    )
}
