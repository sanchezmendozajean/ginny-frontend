package com.ginny.frontend.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.screens.settings.ScanProgressEntry

/**
 * Modal shown while "Escanear biblioteca" runs — grows one line at a time as each system finishes
 * (see [ScanProgressEntry]), each new line fading/expanding in rather than the whole list popping
 * in at once. Stays open after the scan finishes so the final tally remains visible until the user
 * closes it; the confirm button is disabled while [scanning] is still true.
 */
@Composable
fun ScanProgressDialog(
    scanning: Boolean,
    entries: List<ScanProgressEntry>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!scanning) onDismiss() },
        title = { Text(if (scanning) "Escaneando biblioteca…" else "Escaneo completo") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Sistemas encontrados: ${entries.size}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                entries.forEach { entry ->
                    AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                        Text(
                            "${entry.systemDisplayName}: ${entry.gameCount} juegos",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (scanning) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Buscando…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !scanning) { Text("Cerrar") }
        },
    )
}
