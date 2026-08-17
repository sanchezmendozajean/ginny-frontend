package com.ginny.frontend.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.ui.theme.GinnyColors

/**
 * Confirmation modal for "Eliminar juego" — the deletion this leads to is irreversible (no backup,
 * physical removal of the ROM and its icon), so this is the only gate before it happens.
 */
@Composable
fun DeleteGameDialog(
    game: GameEntry,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Eliminar juego") },
        text = {
            Text(
                "\"${game.displayName}\" se eliminará físicamente del dispositivo, junto con sus " +
                    "assets (ícono). Esta acción es irreversible: no se realizará ningún backup.",
                style = MaterialTheme.typography.bodyMedium,
                color = GinnyColors.TextSecondary,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
}
