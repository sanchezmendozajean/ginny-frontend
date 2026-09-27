package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.LocalGinnyPalette

/**
 * A small fixed set of mutually-exclusive choices as pill chips inside a light track — the
 * "Filas visibles" picker's visual pattern from docs/diseño, reused wherever else the same shape
 * of control fits (e.g. the theme picker).
 */
@Composable
fun <T> SegmentedPillPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGinnyPalette.current
    Row(
        modifier = modifier.background(palette.chip, PillShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .let { if (active) it.shadow(2.dp, PillShape) else it }
                    .background(if (active) Color.White else Color.Transparent, PillShape)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) palette.accentInk else palette.muted,
                )
            }
        }
    }
}
