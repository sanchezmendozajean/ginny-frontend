package com.ginny.frontend.ui.modifiers

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Equivalente a `box-shadow: inset offsetX offsetY blur spread color` de CSS.
 *
 * Compose 1.6 (el del proyecto) no trae sombra interior, así que se dibuja a mano: se recorta al
 * [shape] y se pinta, difuminado, todo lo que queda *fuera* de una copia del shape desplazada
 * — ese "marco" difuminado es lo que se ve como sombra hacia adentro.
 *
 * Colocarlo **después** de `.background(...)` en la cadena: así la sombra queda por encima del
 * fondo pero por debajo del contenido (los hijos), igual que en CSS.
 */
fun Modifier.innerShadow(
    shape: Shape,
    color: Color = Color.Black.copy(alpha = 0.25f),
    blur: Dp = 8.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 2.dp,
    spread: Dp = 0.dp,
): Modifier = drawWithContent {
    val spreadPx = spread.toPx()

    // Forma del elemento: todo lo que se dibuje queda recortado a ella.
    val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }

    // "Agujero": la misma forma, achicada por spread y movida por el offset.
    val holeSize = Size(
        (size.width - spreadPx * 2).coerceAtLeast(0f),
        (size.height - spreadPx * 2).coerceAtLeast(0f),
    )
    val hole = Path().apply {
        addOutline(shape.createOutline(holeSize, layoutDirection, this@drawWithContent))
        translate(Offset(spreadPx + offsetX.toPx(), spreadPx + offsetY.toPx()))
    }

    // Marco = rectángulo enorme menos el agujero. Es lo que se pinta difuminado.
    val frame = Path().apply {
        op(
            Path().apply { addRect(Rect(-size.width, -size.height, size.width * 2, size.height * 2)) },
            hole,
            PathOperation.Difference,
        )
    }

    val paint = Paint().apply {
        asFrameworkPaint().apply {
            this.color = color.toArgb()
            if (blur > 0.dp) maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
    }

    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawPath(frame, paint)
        canvas.restore()
    }

    // Después el contenido (hijos), para que quede por encima de la sombra.
    drawContent()
}
