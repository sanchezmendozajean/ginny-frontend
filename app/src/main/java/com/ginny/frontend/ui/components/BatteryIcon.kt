package com.ginny.frontend.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ginny.frontend.R

@Composable
fun BatteryIcon(percent: Int, charging: Boolean, color: Color) {
    Box(modifier = Modifier.size(24.dp)) {
        // Capa 1: contorno
        Icon(
            painter = painterResource(R.drawable.ic_battery_empty),
            contentDescription = null,
            tint = color,
            modifier = Modifier.fillMaxSize(),
        )
        // Capa 2: relleno según el porcentaje
        Canvas(modifier = Modifier.fillMaxSize()) {
            val top = size.height * 0.185f      // borde interior superior del contorno
            val bottom = size.height * 0.88f    // borde interior inferior
            val fillTop = bottom - (bottom - top) * percent / 100f
            drawRect(
                color = color,
                topLeft = Offset(size.width * 0.28f, fillTop),
                size = Size(size.width * 0.44f, bottom - fillTop),
            )
        }
        // Capa 3: rayo, solo si está cargando
        if (charging) {
            Icon(
                painter = painterResource(R.drawable.ic_battery_light),
                contentDescription = null,
                tint = Color(0xFFFF9800),       // naranja
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
