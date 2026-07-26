package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ginny.frontend.ui.theme.GinnyColors

/**
 * Soft multicolor "aurora" wash behind the whole app — subtle pastel blobs over a near-white base,
 * matching --shell-bg in the reference mockup. Deliberately understated so it never competes with
 * tile artwork or text legibility.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFF2F4F8), Color(0xFFEAEEF4))))
                val blobs = listOf(
                    Triple(0.12f, 0.18f, GinnyColors.AuroraLilac),
                    Triple(0.50f, 0.06f, GinnyColors.AuroraGreen),
                    Triple(0.88f, 0.20f, GinnyColors.AuroraCream),
                    Triple(0.20f, 0.88f, GinnyColors.AuroraBlue),
                    Triple(0.82f, 0.85f, GinnyColors.AuroraPink),
                )
                val radius = size.maxDimension * 0.46f
                blobs.forEach { (fx, fy, color) ->
                    val center = Offset(size.width * fx, size.height * fy)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color, color.copy(alpha = 0f)),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            },
    ) {
        content()
    }
}
