package com.ginny.frontend.ui.common

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ginny.frontend.data.AssetRepository
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.ui.components.TilePlaceholderLabel
import com.ginny.frontend.ui.theme.GinnyColors

private val IconBoxSize = 64.dp

/**
 * Shown in place of a plain "not installed" warning when a launch fails because the configured
 * emulator/core for [system] isn't installed — lets the user fix the assignment right there
 * instead of bouncing to Configuración. Emulator/core/package changes are staged locally and only
 * committed (via [onSaveAndLaunch]/[onSaveAndClose]) on explicit confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorConfigDialog(
    system: SystemDefinition,
    assetRepository: AssetRepository,
    userFolderUri: String?,
    initialEmulator: EmulatorTarget,
    initialCoreId: String?,
    initialPackageOverride: String?,
    onSaveAndLaunch: (EmulatorTarget, String?, String?) -> Unit,
    onSaveAndClose: (EmulatorTarget, String?, String?) -> Unit,
    onCancel: () -> Unit,
) {
    var emulator by remember { mutableStateOf(initialEmulator) }
    var coreId by remember { mutableStateOf(initialCoreId) }
    var packageOverride by remember { mutableStateOf(initialPackageOverride.orEmpty()) }

    val logoUri by produceState<Uri?>(initialValue = null, userFolderUri, system.id) {
        value = userFolderUri?.let { uriString ->
            runCatching { assetRepository.systemLogoUri(Uri.parse(uriString), system.id) }.getOrNull()
        }
    }

    val showsCoreDropdown = emulator in EmulatorTarget.RETROARCH_VARIANTS && system.retroArchCores.isNotEmpty()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Emulador no encontrado") },
        text = {
            Column {
                Text(
                    "El emulador seleccionado no se encuentra instalado en el dispositivo. " +
                        "Selecciona un emulador diferente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GinnyColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(IconBoxSize)
                            .background(GinnyColors.CardBg, RoundedCornerShape(10.dp))
                            .border(1.dp, GinnyColors.TextSecondary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (logoUri != null) {
                            AsyncImage(
                                model = logoUri,
                                contentDescription = system.displayName,
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                            )
                        } else {
                            val colorIndex = system.id.ordinal % GinnyColors.SystemColors.size
                            TilePlaceholderLabel(text = system.displayName, color = GinnyColors.SystemColors[colorIndex])
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        EmulatorPickerField(
                            label = "Emulador",
                            selectedLabel = emulator.displayName,
                            options = system.availableEmulators.map { it.displayName to it },
                            onSelect = { emulator = it },
                        )

                        if (showsCoreDropdown) {
                            val selectedCore = system.retroArchCores.firstOrNull { it.id == coreId }
                                ?: system.retroArchCores.first()
                            EmulatorPickerField(
                                label = "Core",
                                selectedLabel = selectedCore.label,
                                options = system.retroArchCores.map { it.label to it.id },
                                onSelect = { coreId = it },
                            )
                        } else if (system.supportsPackageOverride) {
                            OutlinedTextField(
                                value = packageOverride,
                                onValueChange = { packageOverride = it },
                                label = { Text("Package (override)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = {
                    onSaveAndLaunch(emulator, coreId, packageOverride.takeIf { it.isNotBlank() })
                }) { Text("Guardar y Lanzar la ROM") }
                TextButton(onClick = {
                    onSaveAndClose(emulator, coreId, packageOverride.takeIf { it.isNotBlank() })
                }) { Text("Guardar y Cerrar") }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EmulatorPickerField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optionLabel, value) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}
