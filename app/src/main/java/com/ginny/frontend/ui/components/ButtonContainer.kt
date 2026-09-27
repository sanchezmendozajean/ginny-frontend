package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Contenedor horizontal para agrupar botones. No sabe qué botones lleva: quien lo usa los pasa
 * dentro de las llaves, en [content].
 */
@Composable
fun ButtonContainer(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            // Fondo provisional, solo para ver dónde está el contenedor.
            .background(Color.White.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), // espacio entre botones
        verticalAlignment = Alignment.CenterVertically,     // botones centrados en vertical
    ) {
        content()
    }
}
