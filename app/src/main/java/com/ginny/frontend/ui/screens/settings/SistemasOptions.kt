package com.ginny.frontend.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.GinnyDropdown
import com.ginny.frontend.ui.components.settings.GinnyTextField
import com.ginny.frontend.ui.components.settings.SettingsDivider
import com.ginny.frontend.ui.components.settings.SettingsGroup
import com.ginny.frontend.ui.components.settings.SettingsRow
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import kotlinx.coroutines.flow.map

/** Ancho de los desplegables y campos de esta sección (igual que en Interfaz). */
private val SystemControlWidth = 272.dp

/**
 * Opciones de la sección Sistemas (antes "Emuladores"): un grupo por sistema del catálogo, en
 * orden alfabético, con
 * - Carpeta de ROMs: `<ROMS>/<carpeta>` (la subcarpeta de la carpeta de ROMs de Carpetas) o la
 *   que el usuario eligió para ese sistema. "Cambiar" elige otra; "Restablecer" vuelve a la de
 *   siempre. Al cambiarla, ese sistema se reescanea.
 * - Emulador (si el sistema ofrece más de uno; si no, se muestra cuál usa).
 * - Core, si el emulador es RetroArch y el sistema tiene cores.
 * - Package alternativo (PS2, Switch), si el emulador no es RetroArch.
 */
@Composable
fun SistemasOptions(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val romFolder by viewModel.settings.romFolderUri.collectAsState(initial = null)

    // Un solo selector de carpetas para todos los sistemas: se recuerda para cuál se abrió.
    // rememberSaveable: sobrevive si Android recrea la Activity mientras el selector está abierto.
    var pickingFor by rememberSaveable { mutableStateOf<SystemId?>(null) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val system = pickingFor
        if (uri != null && system != null) viewModel.onSystemRomFolderPicked(context, system, uri)
        pickingFor = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        viewModel.systems.forEach { system ->
            SystemGroup(
                system = system,
                viewModel = viewModel,
                romFolder = romFolder,
                onPickFolder = {
                    pickingFor = system.id
                    pickFolder.launch(null)
                },
                onResetFolder = { viewModel.resetSystemRomFolder(context, system.id) },
            )
        }
    }
}

@Composable
private fun SystemGroup(
    system: SystemDefinition,
    viewModel: SettingsViewModel,
    romFolder: String?,
    onPickFolder: () -> Unit,
    onResetFolder: () -> Unit,
) {
    val settings = viewModel.settings
    val override by settings.romFolderOverrideFor(system.id).collectAsState(initial = null)
    val emulator by settings.emulatorFor(system.id).collectAsState(initial = system.defaultEmulator)
    val coreId by settings.retroArchCoreFor(system.id).collectAsState(initial = system.defaultRetroArchCoreId)

    SettingsGroup(system.displayName) {
        SettingsRow(
            title = "Carpeta de ROMs",
            subtitle = systemFolderLabel(override, romFolder, system.id.folderName),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Restablecer: siempre a la vista; apagado si ya usa la carpeta por defecto.
                GinnyButton("Restablecer", onClick = onResetFolder, enabled = override != null)
                GinnyButton("Cambiar", onClick = onPickFolder)
            }
        }

        SettingsDivider()
        SettingsRow(title = "Emulador") {
            if (system.allowsEmulatorChoice) {
                GinnyDropdown(
                    selected = emulator,
                    options = system.availableEmulators.map { it.displayName to it },
                    onSelect = { viewModel.setEmulator(system.id, it) },
                    width = SystemControlWidth,
                )
            } else {
                // Un solo emulador posible: solo se informa cuál.
                Text(
                    text = emulator.displayName,
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GinnyThemes.current.muted,
                )
            }
        }

        val isRetroArch = emulator in EmulatorTarget.RETROARCH_VARIANTS
        if (isRetroArch && system.retroArchCores.isNotEmpty()) {
            SettingsDivider()
            SettingsRow(title = "Core") {
                GinnyDropdown(
                    // Si el core guardado ya no existe, se muestra el primero (el que se usaría).
                    selected = system.retroArchCores.firstOrNull { it.id == coreId }?.id ?: system.retroArchCores.first().id,
                    options = system.retroArchCores.map { it.label to it.id },
                    onSelect = { viewModel.setCore(system.id, it) },
                    width = SystemControlWidth,
                )
            }
        } else if (!isRetroArch && system.supportsPackageOverride) {
            // "" = sin package guardado; null = todavía leyendo. El campo toma su valor inicial una
            // sola vez, así que se muestra recién cuando se sabe cuál es.
            val packageFlow = remember(system.id) { settings.packageOverrideFor(system.id).map { it.orEmpty() } }
            val packageOverride by packageFlow.collectAsState(initial = null)
            SettingsDivider()
            SettingsRow(title = "Package alternativo", subtitle = "Para otra app o fork del emulador") {
                packageOverride?.let { stored ->
                    GinnyTextField(
                        value = stored,
                        onValueChange = { viewModel.setPackageOverride(system.id, it) },
                        placeholder = "com.ejemplo.emulador",
                        width = SystemControlWidth,
                    )
                }
            }
        }
    }
}
