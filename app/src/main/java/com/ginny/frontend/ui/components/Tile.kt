package com.ginny.frontend.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    borderWidth: Dp = 4.dp,
    cornerRadius: Dp = 8.dp,
    contentBox: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, animationSpec = tween(150), label = "tileScale")
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = if (focused) {
        rememberAnimatedFocusBorderBrush()
    } else {
        remember { Brush.linearGradient(listOf(GinnyColors.UnfocusedBorder, GinnyColors.UnfocusedBorder)) }
    }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(elevation = 4.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(GinnyColors.CardBg, shape)
            .border(width = borderWidth, brush = borderBrush, shape = shape),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        // The border above is painted inward from the tile's outer edge, overlapping whatever
        // sits directly underneath — without this inset, icon artwork gets visibly cropped by the
        // border itself rather than just bordered.
        Box(modifier = Modifier.padding(borderWidth), contentAlignment = androidx.compose.ui.Alignment.Center) {
            contentBox()
        }
    }
}

/** Slow, smooth, endlessly looping sweep between the two focus-gradient stops. */
@Composable
private fun rememberAnimatedFocusBorderBrush(): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "tileBorderGradient")
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tileBorderGradientShift",
    )
    return Brush.linearGradient(
        listOf(
            lerp(GinnyColors.AccentCeleste, GinnyColors.AccentBlue, shift),
            lerp(GinnyColors.AccentBlue, GinnyColors.AccentCeleste, shift),
        ),
    )
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
