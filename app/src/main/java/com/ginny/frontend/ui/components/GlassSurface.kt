package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.GinnyColors

/**
 * The "glass" body shared by the top bar, bottom bar and grid panel: a soft translucent white
 * surface with a faint white border, plus a diffuse outward glow in [glowColor] (approximating the
 * mockup's `box-shadow: 0 0 Npx color` with a handful of layered, expanding, fading strokes — real
 * blur-behind via RenderEffect would need API 31+, so this keeps the same look on minSdk 29).
 */
fun Modifier.glassGlow(glowColor: Color, cornerRadius: Dp, glowRadius: Dp = 22.dp): Modifier = this.drawBehind {
    val layers = 6
    val cornerPx = cornerRadius.toPx()
    val glowPx = glowRadius.toPx()
    for (i in layers downTo 1) {
        val t = i / layers.toFloat()
        val expand = glowPx * t
        val alpha = (1f - t) * 0.16f
        drawRoundRect(
            color = glowColor.copy(alpha = alpha),
            topLeft = Offset(-expand, -expand),
            size = Size(size.width + expand * 2, size.height + expand * 2),
            cornerRadius = CornerRadius(cornerPx + expand, cornerPx + expand),
        )
    }
}

@Composable
fun glassBackgroundModifier(cornerRadius: Dp): Modifier = Modifier
    .background(GinnyColors.BarBg, RoundedCornerShape(cornerRadius))
    .border(1.dp, GinnyColors.BarBorder, RoundedCornerShape(cornerRadius))
