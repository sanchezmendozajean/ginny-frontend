package com.ginny.frontend.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ginny.frontend.data.model.EmulatorTarget

@Composable
fun EmulatorMissingDialog(
    emulator: EmulatorTarget,
    storeUrl: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${emulator.displayName} no está instalado") },
        text = {
            Text(
                "Este sistema necesita ${emulator.displayName} para ejecutar el juego, pero no se " +
                    "encontró instalado en este dispositivo." +
                    if (storeUrl != null) " Puedes descargarlo desde su página oficial." else "",
            )
        },
        confirmButton = {
            if (storeUrl != null) {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl))) }
                    onDismiss()
                }) { Text("Abrir página de descarga") }
            } else {
                TextButton(onClick = onDismiss) { Text("Entendido") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
