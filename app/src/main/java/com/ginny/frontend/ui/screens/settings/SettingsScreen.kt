package com.ginny.frontend.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.data.model.BundledMusicTrack
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.MusicSelection
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.ui.components.AuroraBackground
import com.ginny.frontend.ui.components.GridPanel
import com.ginny.frontend.ui.theme.GinnyColors

private enum class SettingsTab(val label: String) {
    INTERFAZ("Interfaz"),
    CARPETAS("Carpetas"),
    EMULADORES("Emuladores"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val listeningForButton = viewModel.listeningForButton
    var selectedTab by remember { mutableStateOf(SettingsTab.INTERFAZ) }

    AuroraBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .onKeyEvent { event ->
                    if (listeningForButton != null && event.type == KeyEventType.KeyDown) {
                        viewModel.onKeyCaptured(event.nativeKeyEvent.keyCode)
                        true
                    } else false
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = GinnyColors.TextPrimary)
                }
                Text("Configuración", style = MaterialTheme.typography.titleLarge, color = GinnyColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                SettingsTabSelector(selected = selectedTab, onSelect = { selectedTab = it })
            }
            Spacer(Modifier.height(12.dp))

            GridPanel(modifier = Modifier.weight(1f)) {
                // GridPanel's own padding is kept tight for the icon grids (systems/games) — the
                // settings tabs are text-heavy, so they need extra breathing room on top of that
                // so content doesn't sit flush against the panel's rounded corners.
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    when (selectedTab) {
                        SettingsTab.INTERFAZ -> InterfazTabContent(viewModel, listeningForButton)
                        SettingsTab.CARPETAS -> CarpetasTabContent(viewModel)
                        SettingsTab.EMULADORES -> EmuladoresTabContent(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabSelector(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTab.values().forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isSelected) {
                            Brush.linearGradient(listOf(GinnyColors.AccentCeleste, GinnyColors.AccentBlue))
                        } else {
                            Brush.linearGradient(listOf(Color.White, Color.White))
                        },
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) GinnyColors.TextInverse else GinnyColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun InterfazTabContent(viewModel: SettingsViewModel, listeningForButton: LogicalButton?) {
    val gridRows by viewModel.settings.gridRows.collectAsState(initial = 2)
    val musicVolume by viewModel.settings.musicVolume.collectAsState(initial = 0.5f)
    val uiVolume by viewModel.settings.uiSoundVolume.collectAsState(initial = 0.7f)
    val buttonMapping by viewModel.settings.buttonMapping.collectAsState(
        initial = com.ginny.frontend.data.ButtonMapping.DEFAULT,
    )
    val userFolder by viewModel.settings.userFolderUri.collectAsState(initial = null)
    val storedMusicTrack by viewModel.settings.backgroundMusicTrack.collectAsState(initial = null)
    val customMusicTracks by produceState(initialValue = emptyList<MusicSelection.Custom>(), userFolder) {
        value = userFolder?.let { uri ->
            viewModel.musicRepository.listCustomTracks(android.net.Uri.parse(uri))
        }.orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("Grilla")
        Text("Filas visibles: $gridRows", color = GinnyColors.TextPrimary)
        Slider(
            value = gridRows.toFloat(),
            onValueChange = { viewModel.setGridRows(it.toInt()) },
            valueRange = 1f..4f,
            steps = 2,
        )

        SectionDivider()
        SectionTitle("Audio")
        Text("Volumen de música de fondo", color = GinnyColors.TextPrimary)
        Slider(value = musicVolume, onValueChange = { viewModel.setMusicVolume(it) })
        Text("Volumen de sonidos de interfaz", color = GinnyColors.TextPrimary)
        Slider(value = uiVolume, onValueChange = { viewModel.setUiSoundVolume(it) })

        Spacer(Modifier.height(8.dp))
        Text("Pista de música de fondo", color = GinnyColors.TextPrimary)
        MusicTrackDropdown(
            selected = MusicSelection.decode(storedMusicTrack, customMusicTracks),
            customTracks = customMusicTracks,
            onSelect = { viewModel.setBackgroundMusicTrack(it) },
        )
        Text(
            if (userFolder != null) {
                "Para agregar más pistas, colocá archivos de audio en Interface/Background Music dentro de la Carpeta de Usuario."
            } else {
                "Configurá la Carpeta de Usuario (pestaña Carpetas) para poder agregar tus propias pistas."
            },
            style = MaterialTheme.typography.bodySmall,
            color = GinnyColors.TextSecondary,
        )

        SectionDivider()
        SectionTitle("Mapeo de botones físicos")
        LogicalButton.values().forEach { button ->
            ButtonMappingRow(
                button = button,
                currentKeyCode = buttonMapping.keyCodeFor(button),
                listening = listeningForButton == button,
                onClick = {
                    if (listeningForButton == button) viewModel.cancelListening()
                    else viewModel.startListeningForButton(button)
                },
            )
        }
        OutlinedButton(onClick = { viewModel.resetButtonMapping() }) {
            Text("Restablecer mapeo por defecto")
        }

        SectionDivider()
        SectionTitle("Tema visual")
        Text(
            "Solo hay un tema disponible por ahora (claro, estilo 3DS/Wii U). Selector " +
                "de temas reservado para una futura versión.",
            style = MaterialTheme.typography.bodyMedium,
            color = GinnyColors.TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CarpetasTabContent(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val userFolder by viewModel.settings.userFolderUri.collectAsState(initial = null)
    val romFolder by viewModel.settings.romFolderUri.collectAsState(initial = null)
    val savesFolder by viewModel.settings.retroArchSavesUri.collectAsState(initial = null)

    val pickUserFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onUserFolderPicked(context, it) }
    }
    val pickRomFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onRomFolderPicked(context, it) }
    }
    val pickSavesFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onSavesFolderPicked(context, it) }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("Carpetas")
        FolderRow("Carpeta de Usuario (Assets)", userFolder) { pickUserFolder.launch(null) }
        FolderRow("Carpeta de ROMs", romFolder) { pickRomFolder.launch(null) }
        FolderRow("Estados guardados de RetroArch (opcional)", savesFolder) { pickSavesFolder.launch(null) }
        Text(
            "Activa \"savestate_thumbnail_enable\" en RetroArch para que se generen las miniaturas que esta app necesita.",
            style = MaterialTheme.typography.bodyMedium,
            color = GinnyColors.TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmuladoresTabContent(viewModel: SettingsViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("Emuladores y cores por sistema")
        viewModel.systems.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                pair.forEach { system ->
                    SystemEmulatorRow(system = system, viewModel = viewModel, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = GinnyColors.AccentBlue)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = GinnyColors.TextSecondary.copy(alpha = 0.2f))
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun FolderRow(label: String, currentUri: String?, onPick: () -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = GinnyColors.TextPrimary)
        Text(
            currentUri?.let { android.net.Uri.parse(it).path ?: it } ?: "No configurada",
            style = MaterialTheme.typography.bodyMedium,
            color = GinnyColors.TextSecondary,
        )
        OutlinedButton(onClick = onPick) { Text("Elegir carpeta") }
    }
}

private fun MusicSelection.label(): String = when (this) {
    is MusicSelection.Bundled -> track.displayName
    is MusicSelection.Custom -> displayName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicTrackDropdown(
    selected: MusicSelection,
    customTracks: List<MusicSelection.Custom>,
    onSelect: (MusicSelection) -> Unit,
) {
    val options: List<MusicSelection> = BundledMusicTrack.entries.map { MusicSelection.Bundled(it) } + customTracks
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemEmulatorRow(system: SystemDefinition, viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val emulator by viewModel.settings.emulatorFor(system.id).collectAsState(initial = system.defaultEmulator)
    val coreId by viewModel.settings.retroArchCoreFor(system.id).collectAsState(initial = system.defaultRetroArchCoreId)

    Column(modifier.padding(vertical = 8.dp)) {
        Text(system.displayName, style = MaterialTheme.typography.bodyLarge, color = GinnyColors.TextPrimary)
        Spacer(Modifier.height(6.dp))

        // Always shown, even when there's only one possible emulator (PS2, PSP, GameCube...) —
        // it's still useful to see at a glance which one a system is wired to.
        var emulatorExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = emulatorExpanded, onExpandedChange = { emulatorExpanded = it }) {
            OutlinedTextField(
                value = emulator.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Emulador") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = emulatorExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = emulatorExpanded, onDismissRequest = { emulatorExpanded = false }) {
                system.availableEmulators.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = { viewModel.setEmulator(system.id, option); emulatorExpanded = false },
                    )
                }
            }
        }

        if (emulator in EmulatorTarget.RETROARCH_VARIANTS && system.retroArchCores.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            var coreExpanded by remember { mutableStateOf(false) }
            val selectedCore = system.retroArchCores.firstOrNull { it.id == coreId } ?: system.retroArchCores.first()
            ExposedDropdownMenuBox(expanded = coreExpanded, onExpandedChange = { coreExpanded = it }) {
                OutlinedTextField(
                    value = selectedCore.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Core") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = coreExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = coreExpanded, onDismissRequest = { coreExpanded = false }) {
                    system.retroArchCores.forEach { core ->
                        DropdownMenuItem(
                            text = { Text(core.label) },
                            onClick = { viewModel.setCore(system.id, core.id); coreExpanded = false },
                        )
                    }
                }
            }
        }

        if (system.supportsPackageOverride) {
            Spacer(Modifier.height(8.dp))
            val packageOverride by viewModel.settings.packageOverrideFor(system.id).collectAsState(initial = null)
            OutlinedTextField(
                value = packageOverride.orEmpty(),
                onValueChange = { viewModel.setPackageOverride(system.id, it) },
                label = { Text("Package override" + if (emulator.packageName.isBlank()) "" else " (opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (emulator.packageName.isBlank()) {
                    "No hay un package por defecto conocido para ${emulator.displayName} — completalo para poder abrirlo."
                } else {
                    "Solo hace falta si tu instalación de ${emulator.displayName} usa un package distinto al de por defecto."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (emulator.packageName.isBlank()) GinnyColors.AccentRed else GinnyColors.TextSecondary,
            )
        }
    }
}

private fun LogicalButton.description(): String = when (this) {
    LogicalButton.A -> "Abrir / seleccionar"
    LogicalButton.B -> "Retroceder"
    LogicalButton.MENU -> "Abrir configuración"
    LogicalButton.L -> "Saltar filas hacia atrás"
    LogicalButton.R -> "Saltar filas hacia adelante"
    LogicalButton.DPAD_UP -> "Mover foco arriba"
    LogicalButton.DPAD_DOWN -> "Mover foco abajo"
    LogicalButton.DPAD_LEFT -> "Mover foco a la izquierda"
    LogicalButton.DPAD_RIGHT -> "Mover foco a la derecha"
}

@Composable
private fun ButtonMappingRow(button: LogicalButton, currentKeyCode: Int, listening: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text("${button.name}: ${button.description()}", color = GinnyColors.TextPrimary)
        Button(onClick = onClick) {
            Text(
                if (listening) "Presiona un botón…" else android.view.KeyEvent.keyCodeToString(currentKeyCode),
            )
        }
    }
}
