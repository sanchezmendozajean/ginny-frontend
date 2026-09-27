package com.ginny.frontend.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Panel central. Aquí irá la grilla de sistemas / juegos / estados guardados. */
@Composable
fun GinnyContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Fondo provisional, solo para ver dónde está el contenedor.
            .background(Color.White.copy(alpha = 0.6f)),
    ) {
        // Elementos hijos
    }
}
