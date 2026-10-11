package com.ginny.frontend.ui.components.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.GinnyThemes
import com.ginny.frontend.ui.theme.lighten

/** Grosor del trazo del cursor (sin contar el borde). */
val CursorStrokeWidth = 4.dp

/** Grosor del borde claro que rodea al trazo, por cada lado. */
val CursorBorderWidth = 2.dp

/** Cuánto más claro es el borde que el trazo (luminosidad OKLCH, 0…1). */
const val CursorBorderLighten = 0.2f

/** Largo de cada brazo de la esquina, como fracción del lado (0.25 = un cuarto). */
const val CursorArmFraction = 0.25f

/**
 * Cursor de selección: cuatro esquinas en forma de "L" con la curva redondeada, que enmarcan el
 * elemento seleccionado sin taparlo. Por ahora de color plano (el acento del tema), con un borde
 * del mismo color pero más claro.
 *
 * Por defecto usa un radio proporcional al de [Element] ([ElementCornerRadius]); si cambia ese
 * radio, el cursor lo sigue.
 */
@Composable
fun Cursor(
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = GinnyThemes.current.accent,
    borderColor: Color = color.lighten(CursorBorderLighten), // puede usar "color": va antes
    strokeWidth: Dp = CursorStrokeWidth,
    borderWidth: Dp = CursorBorderWidth,
    cornerRadius: Dp = ElementCornerRadius * 1.5f,
) = Cursor(
    width = size,
    height = size,
    modifier = modifier,
    color = color,
    borderColor = borderColor,
    strokeWidth = strokeWidth,
    borderWidth = borderWidth,
    cornerRadius = cornerRadius,
)

/**
 * El mismo cursor sobre un rectángulo de [width] × [height] (los controles de Configuración no son
 * cuadrados). Los brazos de las esquinas miden [armLength]; si no se indica, un cuarto del ancho
 * (como en el cursor cuadrado). El radio de la curva se limita a la mitad del lado menor.
 */
@Composable
fun Cursor(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    color: Color = GinnyThemes.current.accent,
    borderColor: Color = color.lighten(CursorBorderLighten),
    strokeWidth: Dp = CursorStrokeWidth,
    borderWidth: Dp = CursorBorderWidth,
    cornerRadius: Dp = ElementCornerRadius * 1.5f,
    armLength: Dp? = null,
) {
    Canvas(modifier = modifier.size(width, height)) {
        val stroke = strokeWidth.toPx()
        val border = borderWidth.toPx()
        val outer = stroke + 2 * border            // ancho total: trazo + borde a cada lado

        // El trazo crece hacia ambos lados de la línea: se mete medio ancho total hacia adentro
        // para que el borde no se corte en el borde del lienzo.
        val inset = outer / 2
        val left = inset
        val top = inset
        val right = this.size.width - inset
        val bottom = this.size.height - inset
        val r = cornerRadius.toPx().coerceAtMost(minOf(right - left, bottom - top) / 2)
        val arm = armLength?.toPx() ?: ((right - left) * CursorArmFraction) // largo de cada brazo, contando la curva

        // Una "L" por esquina: brazo vertical → curva de 90° → brazo horizontal.
        // arcTo dibuja la curva dentro del cuadrado [rect], desde startAngle girando sweep grados
        // (0° = derecha, 90° = abajo, 180° = izquierda, 270° = arriba).
        val corners = Path().apply {
            // Arriba a la izquierda
            moveTo(left, top + arm)
            arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
            lineTo(left + arm, top)

            // Arriba a la derecha
            moveTo(right - arm, top)
            arcTo(Rect(right - 2 * r, top, right, top + 2 * r), 270f, 90f, false)
            lineTo(right, top + arm)

            // Abajo a la derecha
            moveTo(right, bottom - arm)
            arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
            lineTo(right - arm, bottom)

            // Abajo a la izquierda
            moveTo(left + arm, bottom)
            arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)
            lineTo(left, bottom - arm)
        }

        // Puntas redondeadas (Round). El mismo trazado se dibuja dos veces:
        // 1) Borde: más ancho y en el color claro. Su punta redonda es un semicírculo más grande
        //    que el del trazo de arriba, así que el borde también envuelve las puntas.
        drawPath(corners, borderColor, style = Stroke(width = outer, cap = StrokeCap.Round))
        // 2) Trazo principal encima.
        drawPath(corners, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}
