package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.GinnyColors

/**
 * The very slight drop shadow shared by the top bar, bottom bar, grid panel and the pill-shaped
 * buttons: a single soft shape offset straight down (X:0 Y:2dp), drawn behind the surface so only
 * the sliver that pokes past the bottom edge is visible.
 */
fun Modifier.glassShadow(
    cornerRadius: Dp,
    offsetY: Dp = 2.dp,
    color: Color = Color.Black.copy(alpha = 0.14f),
): Modifier = this.drawBehind {
    val cornerPx = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, offsetY.toPx()),
        size = size,
        cornerRadius = CornerRadius(cornerPx, cornerPx),
    )
}

@Composable
fun glassBackgroundModifier(cornerRadius: Dp): Modifier = Modifier
    .background(GinnyColors.BarBg, RoundedCornerShape(cornerRadius))
    .border(1.dp, GinnyColors.BarBorder, RoundedCornerShape(cornerRadius))
