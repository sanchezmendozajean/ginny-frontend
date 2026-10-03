package com.ginny.frontend.ui.components.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.R
import com.ginny.frontend.ui.components.Icon

/** Hacia dónde apunta una [PageArrow]. ic_arrow apunta a la derecha; la izquierda es girarla 180°. */
enum class ArrowDirection(val degrees: Float) { Left(180f), Right(0f) }

/** Opacidad de la flecha cuando no hay página hacia ese lado. */
const val PageArrowDisabledAlpha = 0.5f

/**
 * Flecha blanca a un costado de la grilla, que indica si hay otra página hacia ese lado.
 * Deshabilitada (primera / última página): la misma flecha, semitransparente.
 */
@Composable
fun PageArrow(
    direction: ArrowDirection,
    enabled: Boolean,
    size: Dp = 28.dp,
) {
    Icon(
        id = R.drawable.ic_arrow,
        degrees = direction.degrees,
        tint = Color.White.copy(alpha = if (enabled) 1f else PageArrowDisabledAlpha),
        size = size,
    )
}
