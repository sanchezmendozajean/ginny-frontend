package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.modifiers.innerShadow

/**
 * Contenedor horizontal para agrupar botones. No sabe qué botones lleva: quien lo usa los pasa
 * dentro de las llaves, en [content].
 */
@Composable
fun Container(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val roundedCorner = 16.dp;

    Row(
        modifier = modifier
            .fillMaxHeight()
            // Fondo provisional, solo para ver dónde está el contenedor.
            .background(
                color = Color.Transparent,
                shape = RoundedCornerShape(roundedCorner)
            )
            .border(
                width = 2.dp,
                color = Color.White,
                shape = RoundedCornerShape(roundedCorner)
            )
            .innerShadow(
                shape = RoundedCornerShape(roundedCorner),
                color = Color.White,
                blur = 5.dp,
                offsetX = 0.dp,
                offsetY = 0.dp,
                spread = 3.dp
            )
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), // espacio entre botones
        verticalAlignment = Alignment.CenterVertically,     // botones centrados en vertical
    ) {
        content()
    }
}
