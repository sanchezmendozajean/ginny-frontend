package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.LocalGinnyPalette

/** Round glass button fixed at a screen edge to jump a whole page in [PagedFocusGrid], with a
 * small "L"/"R" badge underneath — touch-only, mirrors the physical L/R shoulder buttons. */
@Composable
fun PageJumpButton(direction: PageJumpDirection, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalGinnyPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.35f)
            .focusProperties { canFocus = false },
    ) {
        GlassSurface(shape = CircleShape, modifier = Modifier.size(56.dp).clickable(enabled = enabled, onClick = onClick)) {
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (direction == PageJumpDirection.PREVIOUS) "‹" else "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.accentInk,
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(22.dp)
                .background(palette.ink, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (direction == PageJumpDirection.PREVIOUS) "L" else "R",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

enum class PageJumpDirection { PREVIOUS, NEXT }
