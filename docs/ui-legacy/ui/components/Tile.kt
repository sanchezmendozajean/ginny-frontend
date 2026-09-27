package com.ginny.frontend.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.ginny.frontend.ui.theme.LocalGinnyPalette

/**
 * Square tile shared by systems/games/save-state grids: an outer hue-gradient (or plain white,
 * for games) [frame] with a thin padding, then a white-ish inner content area. Unfocused: soft
 * ambient shadow only. Focused: the frame border becomes a solid accent ring plus an outward
 * colored glow, and the whole tile lifts and scales up slightly — see docs/diseño.
 */
@Composable
fun FocusableTile(
    focused: Boolean,
    tileSize: Dp,
    frame: Brush,
    modifier: Modifier = Modifier,
    innerPadding: Dp = 5.dp,
    contentBox: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalGinnyPalette.current
    // Scale alone (no translateY "lift") — graphicsLayer's default pivot is the box's own center,
    // so this grows a focused tile symmetrically in place instead of drifting it upward.
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, animationSpec = tween(150), label = "tileScale")
    val cornerRadius = tileSize * 0.1f
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(tileSize)
            .scale(scale)
            .shadow(
                elevation = if (focused) 20.dp else 4.dp,
                shape = shape,
                ambientColor = if (focused) palette.glow else palette.shadow,
                spotColor = if (focused) palette.glow else palette.shadow,
            )
            .clip(shape)
            .background(frame, shape)
            .then(
                if (focused) Modifier.border(4.dp, palette.accent, shape) else Modifier,
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        // Painted inward from the tile's outer edge so it overlaps whatever sits directly
        // underneath — without this inset, icon artwork gets visibly cropped by the frame itself.
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .clip(RoundedCornerShape((cornerRadius - innerPadding).coerceAtLeast(0.dp))),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            contentBox()
        }
    }
}

/** Diagonal repeating-stripe placeholder for a game tile with no cover art (icon/embedded-icon),
 * hue-tinted per [com.ginny.frontend.ui.theme.SystemId.gameTileHue] — matches docs/diseño's fallback look. */
@Composable
fun TileStripedPlaceholder(hue: Float, modifier: Modifier = Modifier) {
    val stripeA = com.ginny.frontend.ui.theme.oklch(0.86f, 0.06f, hue)
    val stripeB = com.ginny.frontend.ui.theme.oklch(0.83f, 0.07f, hue)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(stripeA, stripeB, stripeA, stripeB))),
    )
}

/** [text] should already be a short, display-ready code (e.g. [com.ginny.frontend.ui.theme.shortCode])
 * — this no longer truncates its input, since naively cutting to N characters is what made
 * same-family systems ("Game Boy"/"Game Boy Advance"/"Game Boy Color") show identical labels. */
@Composable
fun TilePlaceholderLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalGinnyPalette.current.accentInk) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(
            text = text.uppercase(),
            color = color,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
    }
}
