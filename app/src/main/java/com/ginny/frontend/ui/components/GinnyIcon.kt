package com.ginny.frontend.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ginny.frontend.R
import com.ginny.frontend.ui.theme.GinnyThemes
import com.ginny.frontend.ui.theme.lighten

@Composable
fun GinnyIcon() {
    // El tema se lee aquí, fuera del Canvas: dentro de Canvas no se pueden leer composables.
    val palette = GinnyThemes.current
    val gradientStart = palette.accent.lighten(0.1f) // arriba-izquierda: acento más claro
    val gradientEnd = palette.accent                 // abajo-derecha: acento

    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Capa 1: círculo con degradado lineal en diagonal
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(gradientStart, gradientEnd),
                    start = Offset.Zero,                         // esquina superior izquierda
                    end = Offset(size.width, size.height),       // esquina inferior derecha
                ),
            )
        }
        // Capa 2: logo
        Icon(
            painter = painterResource(R.drawable.ic_ginny),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}
