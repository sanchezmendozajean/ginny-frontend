package com.ginny.frontend.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.GinnyThemes

/**
 * Ícono animado que avisa que el scrapper está descargando: un aro con un arco que gira y, en el
 * centro, una flecha hacia abajo que sube y baja. Al tocarlo se abre el modal de avance.
 */
@Composable
fun ScrapActivityIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = GinnyThemes.current
    val currentOnClick by rememberUpdatedState(onClick)
    val transition = rememberInfiniteTransition(label = "scrapActivity")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "scrapSpin",
    )
    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "scrapBob",
    )

    Canvas(
        modifier = modifier
            .size(20.dp)
            .pointerInput(Unit) { detectTapGestures { currentOnClick() } },
    ) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val ring = Size(size.width - stroke, size.height - stroke)
        // Aro tenue + arco que gira.
        drawCircle(color = palette.ink.copy(alpha = 0.15f), style = Stroke(stroke))
        rotate(spin) {
            drawArc(
                color = palette.accent,
                startAngle = 0f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = ring,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        // Flecha hacia abajo, que sube y baja un poco.
        val cx = size.width / 2
        val cy = size.height / 2 + bob * 0.8.dp.toPx()
        val half = 3.dp.toPx()
        val arrow = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(palette.ink, Offset(cx, cy - half), Offset(cx, cy + half), arrow.width, StrokeCap.Round)
        drawLine(palette.ink, Offset(cx - half * 0.8f, cy + half * 0.1f), Offset(cx, cy + half), arrow.width, StrokeCap.Round)
        drawLine(palette.ink, Offset(cx + half * 0.8f, cy + half * 0.1f), Offset(cx, cy + half), arrow.width, StrokeCap.Round)
    }
}
