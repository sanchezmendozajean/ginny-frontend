package com.ginny.frontend.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.components.BatteryIcon
import com.ginny.frontend.ui.components.Container

/** Barra superior. Aquí irán el título contextual, la hora, la batería y el acceso a Configuración. */
@Composable
fun GinnyHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Container() {
            BatteryIcon(
                percent = 100,
                charging = true,
                color = Color.Black
            )
            Text(
                text = "Hola"
            )
        }
    }
}
