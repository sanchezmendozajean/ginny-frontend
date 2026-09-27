package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.LocalGinnyPalette
import kotlin.math.ceil

private val DotSpacing = 18f // dp, matches the reference's 18px dot-grid tile.
private val DotRadius = 1.2f // dp

/**
 * The app's background everywhere: a base fill, two large soft radial blobs top-left/bottom-right
 * (approximating the reference's elliptical `radial-gradient`s with circular ones — close enough
 * at this scale), and a fine dot grid on top. Every color comes from [LocalGinnyPalette], so this
 * repaints automatically as the hue changes (fixed theme switch or live "Ciclo" animation).
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = LocalGinnyPalette.current
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = palette.bg0)

                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(0f to palette.bg1, 0.62f to Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.maxDimension,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(0f to palette.bg2, 0.6f to Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = size.maxDimension,
                    ),
                )

                val spacingPx = with(density) { DotSpacing.dp.toPx() }
                val radiusPx = with(density) { DotRadius.dp.toPx() }
                val cols = ceil(size.width / spacingPx).toInt() + 1
                val rows = ceil(size.height / spacingPx).toInt() + 1
                for (row in 0..rows) {
                    for (col in 0..cols) {
                        drawCircle(
                            color = palette.dot,
                            radius = radiusPx,
                            center = Offset(col * spacingPx, row * spacingPx),
                        )
                    }
                }
            },
    ) {
        content()
    }
}
