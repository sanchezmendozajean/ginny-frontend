package com.ginny.frontend.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.GinnyColors

/**
 * Square tile shared by systems/games/save-state grids. Unfocused: thick plain white border,
 * content filling to the edge. Focused: same-thickness border becomes a celeste->blue gradient
 * covering the full outline, plus an outward blue glow and a slight scale bump.
 */
@Composable
fun FocusableTile(
    focused: Boolean,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 8.dp,
    cornerRadius: Dp = 16.dp,
    contentBox: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, animationSpec = tween(150), label = "tileScale")
    val glowAlpha by animateFloatAsState(if (focused) 1f else 0f, animationSpec = tween(150), label = "tileGlow")
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .scale(scale)
            .then(
                if (glowAlpha > 0f) {
                    Modifier.shadow(
                        elevation = 14.dp * glowAlpha,
                        shape = shape,
                        ambientColor = GinnyColors.AccentBlue.copy(alpha = 0.45f * glowAlpha),
                        spotColor = GinnyColors.AccentBlue.copy(alpha = 0.45f * glowAlpha),
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(GinnyColors.CardBg, shape)
            .border(
                width = borderWidth,
                brush = if (focused) {
                    Brush.linearGradient(listOf(GinnyColors.AccentCeleste, GinnyColors.AccentBlue))
                } else {
                    Brush.linearGradient(listOf(GinnyColors.UnfocusedBorder, GinnyColors.UnfocusedBorder))
                },
                shape = shape,
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        contentBox()
    }
}

@Composable
fun TilePlaceholderLabel(text: String, modifier: Modifier = Modifier, color: Color = GinnyColors.AccentBlue) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(
            text = text.take(4).uppercase(),
            color = color,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
    }
}
