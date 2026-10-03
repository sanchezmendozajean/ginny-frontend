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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.modifiers.LocalHazeState
import com.ginny.frontend.ui.modifiers.innerShadow
import dev.chrisbanes.haze.hazeChild

/**
 * Contenedor horizontal para agrupar botones. No sabe qué botones lleva: quien lo usa los pasa
 * dentro de las llaves, en [content].
 *
 * [blur] = false: sin desenfoque, fondo transparente (solo borde y sombra interior).
 * [content] va al final para poder escribir `Container(blur = false) { ... }`.
 */
@Composable
fun Container(
    modifier: Modifier = Modifier,
    blur: Boolean = true,
    roundedCorner: Dp = 16.dp,
    horizontalPadding: Dp = 20.dp,
    verticalPadding: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val hazeState = LocalHazeState.current

    Row(
        modifier = modifier
            .fillMaxHeight()
            // Desenfoca lo que está detrás (el fondo). Blur real en API 31+; en 29-30, solo el tinte.
            .then(
                if (blur && hazeState != null) {
                    Modifier.hazeChild(
                        state = hazeState,
                        shape = RoundedCornerShape(roundedCorner),
                        tint = Color.White.copy(alpha = 0.15f), // velo blanco sobre el desenfoque
                        //blurRadius = 16.dp,                     // intensidad del desenfoque
                    )
                } else {
                    Modifier
                },
            )
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
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp), // espacio entre elementos
        verticalAlignment = Alignment.CenterVertically,     // botones centrados en vertical
    ) {
        content()
    }
}
