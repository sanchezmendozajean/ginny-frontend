package com.ginny.frontend.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.screens.settings.ScanProgressEntry
import com.ginny.frontend.ui.theme.GinnyColors

/** Fixed height for the scrollable entries area — keeps the dialog's own size stable regardless
 * of how many systems have reported in, instead of growing past the screen as scanning runs. */
private val EntriesBoxHeight = 260.dp

/**
 * Modal shown while "Escanear biblioteca" runs — grows one line at a time as each system finishes
 * (see [ScanProgressEntry]), each new line fading/expanding in inside a fixed-height scrollable
 * box. Stays open after the scan finishes so the final tally remains visible until the user closes
 * it; while scanning, the action button cancels the run instead of dismissing.
 */
@Composable
fun ScanProgressDialog(
    scanning: Boolean,
    entries: List<ScanProgressEntry>,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!scanning) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (scanning) "Escaneando Bibliotecas" else "Escaneo completo")
            }
        },
        text = {
            Column {
                Text("Sistemas encontrados: ${entries.size}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EntriesBoxHeight)
                        .dashedBorder(GinnyColors.TextSecondary.copy(alpha = 0.5f))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    entries.forEach { entry ->
                        AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                            Text(
                                "${entry.systemDisplayName}: ${entry.gameCount} juegos",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (scanning) {
                        Text(
                            "Buscando…",
                            style = MaterialTheme.typography.bodySmall,
                            color = GinnyColors.TextSecondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (scanning) onStop else onDismiss) {
                Text(if (scanning) "Detener" else "Cerrar")
            }
        },
    )
}

private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp = 8.dp,
    strokeWidth: Dp = 1.dp,
) = drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
        ),
    )
}
