package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.LocalGinnyPalette

/** Rounded pill "select" — used for emulator/core/track pickers throughout Configuración and the
 * game options modal (matches docs/diseño's `<select>` styling). */
@Composable
fun <T> PillDropdown(
    selectedLabel: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    background: Color? = null,
    bordered: Boolean = false,
) {
    val palette = LocalGinnyPalette.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(background ?: palette.chip, RoundedCornerShape(23.dp))
                .let { if (bordered) it.border(1.dp, palette.line, RoundedCornerShape(23.dp)) else it }
                .clickable { expanded = true }
                .padding(horizontal = 18.dp),
        ) {
            Text(
                selectedLabel,
                style = MaterialTheme.typography.labelLarge,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▾", color = palette.muted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}
