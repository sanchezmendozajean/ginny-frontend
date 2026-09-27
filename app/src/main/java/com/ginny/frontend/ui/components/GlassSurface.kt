package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.LocalGinnyPalette

/** Fully rounded regardless of measured size — `percent = 50` clamps the corner radius to half the shorter side. */
val PillShape: Shape = RoundedCornerShape(percent = 50)

/**
 * The "glass pill" surface used everywhere in the new chrome (top bar, bottom bar, page-jump
 * buttons, hint pills): translucent white fill, a near-opaque hairline border, and a soft shadow
 * tinted by the current [LocalGinnyPalette]. Matches docs/diseño's floating-pill look 1:1.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PillShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalGinnyPalette.current
    Box(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = shape, ambientColor = palette.shadow, spotColor = palette.shadow)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.8f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.95f), shape),
        // Every caller puts a Row/Icon/Text that's shorter than this pill's own (often explicit)
        // height — without centering here, that content sticks to the box's default top-start
        // corner instead of sitting centered inside the pill.
        contentAlignment = Alignment.Center,
        content = content,
    )
}
