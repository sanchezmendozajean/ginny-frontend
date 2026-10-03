package com.ginny.frontend.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.components.DeviceStatus
import com.ginny.frontend.ui.components.SystemLabel

/** Barra superior. Aquí irán el título contextual, la hora, la batería y el acceso a Configuración. */
@Composable
fun GinnyHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        SystemLabel(systemName = "Nintendo")
        DeviceStatus(modifier = Modifier.align(Alignment.CenterEnd)) // a la derecha
    }
}
