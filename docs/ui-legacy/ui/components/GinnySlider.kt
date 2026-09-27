package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.LocalGinnyPalette
import kotlin.math.roundToInt

/** Palette-tinted [Slider] with a live "N%" readout — used for the two audio volume controls. */
@Composable
fun GinnySlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalGinnyPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.track,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = palette.muted,
            modifier = Modifier.widthIn(min = 44.dp),
        )
    }
}
