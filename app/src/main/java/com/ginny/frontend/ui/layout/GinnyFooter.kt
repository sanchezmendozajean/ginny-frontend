package com.ginny.frontend.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Barra inferior. Aquí irán el nombre del elemento enfocado y los botones A / B. */
@Composable
fun GinnyFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            // Fondo provisional, solo para ver dónde está el contenedor.
            .background(Color.White.copy(alpha = 0.6f)),
    ) {
        // Elementos hijos
    }
}
