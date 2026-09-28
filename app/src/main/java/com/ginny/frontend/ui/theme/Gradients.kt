package com.ginny.frontend.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Equivalente a `linear-gradient(<angleDeg>deg, colors...)` de CSS, con la misma convención:
 * 0° = hacia arriba, 90° = hacia la derecha, 180° = hacia abajo (sentido horario). El primer
 * color queda en el lado opuesto a la dirección y el último, en el lado hacia donde apunta.
 *
 * Uso: `.background(linearGradient(15f, colorA, colorB))`
 */
fun linearGradient(angleDeg: Float, from: Color, to: Color): Brush =
    AngledLinearGradient(angleDeg, listOf(from, to))

/** Igual, con 3 o más colores repartidos de forma pareja: `linearGradient(15f, listOf(a, b, c))`. */
fun linearGradient(angleDeg: Float, colors: List<Color>): Brush =
    AngledLinearGradient(angleDeg, colors)

/**
 * Brush que calcula inicio y fin recién cuando conoce su tamaño ([createShader]), igual que CSS:
 * la línea del degradado pasa por el centro y es lo bastante larga para que las esquinas
 * queden exactamente en el primer y último color.
 */
private data class AngledLinearGradient(val angleDeg: Float, val colors: List<Color>) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = sin(rad).toFloat()   // dirección en pantalla (y crece hacia abajo)
        val dy = -cos(rad).toFloat()
        val halfLength = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val c = size.center
        return LinearGradientShader(
            from = Offset(c.x - dx * halfLength, c.y - dy * halfLength),
            to = Offset(c.x + dx * halfLength, c.y + dy * halfLength),
            colors = colors,
        )
    }
}
