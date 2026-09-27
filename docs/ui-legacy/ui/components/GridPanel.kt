package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Outer margin every screen (`Systems`/`Games`/`SaveStates`) uses around its top bar / grid /
 * bottom bar column — kept equal on all four sides so the floating glass bars sit the same
 * distance from every screen edge (see docs/diseño). */
val GinnyScreenPadding = 18.dp

@Composable
fun GridPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        content()
    }
}
