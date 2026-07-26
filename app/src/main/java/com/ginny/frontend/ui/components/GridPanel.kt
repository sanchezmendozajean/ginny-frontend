package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.GinnyColors
import com.ginny.frontend.ui.theme.GinnyShapes

/**
 * The glass "panel" body that hosts the grid — same surface treatment as the top/bottom bars, with
 * its own pink glow, and enough external margin to read as a separate stacked piece (per spec:
 * visible gap between this panel and the bars above/below it, provided by the caller's Column
 * spacing rather than here).
 */
@Composable
fun GridPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .glassGlow(GinnyColors.GlowGridPanel, GinnyShapes.radiusLarge.dp)
            .then(glassBackgroundModifier(GinnyShapes.radiusLarge.dp))
            .padding(8.dp),
    ) {
        content()
    }
}
