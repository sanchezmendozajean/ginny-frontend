package com.ginny.frontend.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.components.Container

/** Panel central. Aquí irá la grilla de sistemas / juegos / estados guardados. */
@Composable
fun Content(modifier: Modifier = Modifier) {
    Container(
        modifier = modifier
            .fillMaxWidth()
            // Fondo provisional, solo para ver dónde está el contenedor.
    ) {
        Row(
            modifier = Modifier
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {

        }
    }
}
