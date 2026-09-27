package com.ginny.frontend.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.data.model.BundledMusicTrack
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.MusicSelection
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.ui.common.GinnyModalSurface
import com.ginny.frontend.ui.common.ScanProgressDialog
import com.ginny.frontend.ui.components.GinnySlider
import com.ginny.frontend.ui.components.PillDropdown
import com.ginny.frontend.ui.components.SegmentedPillPicker
import com.ginny.frontend.ui.components.SystemLogoBox
import com.ginny.frontend.ui.theme.LocalGinnyPalette
import com.ginny.frontend.ui.theme.ThemeMode

// Explicit height shared by the icon box and the emulator/core/override controls in the
// Emuladores tab, matching PillDropdown's own fixed height so the row lines up.
private val EmulatorRowControlHeight = 46.dp
private val EmulatorRowIconSize = 52.dp
private val SidebarWidth = 250.dp
private val TransparentBrush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))

private enum class SettingsTab(val label: String, val description: String) {
    INTERFAZ("Interfaz", "Grilla, tema, nombres y audio"),
    CARPETAS("Carpetas", "Dónde vive tu biblioteca"),
    EMULADORES("Emuladores", "Aplicación y core por sistema"),
}

@Composable
fun SettingsScreen(onClose: () -> Unit, viewModel: SettingsViewModel) {
    val palette = LocalGinnyPalette.current
    val context = LocalContext.current
    val listeningForButton = viewModel.listeningForButton
    var selectedTab by remember { mutableStateOf(SettingsTab.INTERFAZ) }
    val keyCaptureFocusRequester = remember { FocusRequester() }

    // Tapping "Presiona un botón…" doesn't grant this subtree Compose focus (clickable() only
    // becomes focusable in non-touch/D-pad navigation mode), so without an explicit focus
    // request the onKeyEvent below never has a focused node to bubble the key press from.
    LaunchedEffect(listeningForButton) {
        if (listeningForButton != null) keyCaptureFocusRequester.requestFocus()
    }

    GinnyModalSurface(
        onDismissRequest = onClose,
        cardModifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(keyCaptureFocusRequester)
                .onKeyEvent { event ->
                    if (listeningForButton != null && event.type == KeyEventType.KeyDown) {
                        viewModel.onKeyCaptured(event.nativeKeyEvent.keyCode)
                        true
                    } else false
                }
                .focusable(),
        ) {
            Column(
                modifier = Modifier
                    .width(SidebarWidth)
                    .fillMaxHeight()
                    .background(palette.rail)
                    .padding(vertical = 30.dp, horizontal = 22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 26.dp)) {
                    Box(
                        modifier = Modifier.size(32.dp).background(palette.grad, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("G", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Configuración", style = MaterialTheme.typography.titleLarge, color = palette.ink)
                }

                SettingsTab.entries.forEach { tab ->
                    val active = tab == selectedTab
                    val tabBackground = if (active) palette.grad else TransparentBrush
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(tabBackground, RoundedCornerShape(percent = 50))
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) Color.White else palette.ink,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                SidebarFooterAction(label = "Cerrar", badge = "B", onClick = onClose)
                Spacer(Modifier.height(4.dp))
                SidebarFooterAction(label = "Salir de Ginny", badge = "✕") {
                    (context as? android.app.Activity)?.finish()
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 28.dp, bottom = 18.dp, start = 40.dp, end = 36.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedTab.label, style = MaterialTheme.typography.displaySmall, color = palette.ink)
                        Text(selectedTab.description, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(palette.chip, CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", style = MaterialTheme.typography.titleMedium, color = palette.ink)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 40.dp, end = 36.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
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
private fun SidebarFooterAction(label: String, badge: String, onClick: () -> Unit) {
    val palette = LocalGinnyPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(palette.ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(badge, style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = palette.muted)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val palette = LocalGinnyPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .border(1.dp, palette.line, RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    val palette = LocalGinnyPalette.current
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = palette.accentInk,
        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    val palette = LocalGinnyPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = palette.ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
            }
        }
        trailing()
    }
}

@Composable
private fun InterfazTabContent(viewModel: SettingsViewModel, listeningForButton: LogicalButton?) {
    val palette = LocalGinnyPalette.current
    val gridRows by viewModel.settings.gridRows.collectAsState(initial = 2)
    val themeMode by viewModel.settings.themeMode.collectAsState(initial = ThemeMode.MENTA)
    val musicVolume by viewModel.settings.musicVolume.collectAsState(initial = 0.5f)
    val uiVolume by viewModel.settings.uiSoundVolume.collectAsState(initial = 0.7f)
    val buttonMapping by viewModel.settings.buttonMapping.collectAsState(
        initial = com.ginny.frontend.data.ButtonMapping.DEFAULT,
    )
    val userFolder by viewModel.settings.userFolderUri.collectAsState(initial = null)
    val storedMusicTrack by viewModel.settings.backgroundMusicTrack.collectAsState(initial = null)
    val customMusicTracks by produceState(initialValue = emptyList<MusicSelection.Custom>(), userFolder) {
        value = userFolder?.let { uri ->
            viewModel.musicRepository.listCustomTracks(Uri.parse(uri))
        }.orEmpty()
    }

    SectionLabel("Grilla")
    SettingsCard {
        SettingsRow(title = "Filas visibles") {
            SegmentedPillPicker(
                options = listOf(1, 2, 3, 4),
                selected = gridRows,
                onSelect = { viewModel.setGridRows(it) },
                label = { it.toString() },
            )
        }
    }

    SectionLabel("Tema")
    SettingsCard {
        SettingsRow(title = "Paleta de color", subtitle = "\"Ciclo\" mezcla las 4 paletas con el tiempo") {
            SegmentedPillPicker(
                options = ThemeMode.entries,
                selected = themeMode,
                onSelect = { viewModel.setThemeMode(it) },
                label = { it.displayName },
            )
        }
    }

    SectionLabel("Audio")
    SettingsCard {
        SettingsRow(title = "Música de fondo") {
            GinnySlider(value = musicVolume, onValueChange = { viewModel.setMusicVolume(it) }, modifier = Modifier.width(280.dp))
        }
        androidx.compose.material3.HorizontalDivider(color = palette.line)
        SettingsRow(title = "Sonidos de interfaz") {
            GinnySlider(value = uiVolume, onValueChange = { viewModel.setUiSoundVolume(it) }, modifier = Modifier.width(280.dp))
        }
        androidx.compose.material3.HorizontalDivider(color = palette.line)
        SettingsRow(title = "Pista de fondo") {
            PillDropdown(
                selectedLabel = MusicSelection.decode(storedMusicTrack, customMusicTracks).label(),
                options = (BundledMusicTrack.entries.map { MusicSelection.Bundled(it) } + customMusicTracks)
                    .map { it.label() to it },
                onSelect = { viewModel.setBackgroundMusicTrack(it) },
                modifier = Modifier.width(280.dp),
            )
        }
        Text(
            if (userFolder != null) {
                "Para agregar más pistas, colocá archivos de audio en Interface/Background Music dentro de la Carpeta de Usuario."
            } else {
                "Configurá la Carpeta de Usuario (pestaña Carpetas) para poder agregar tus propias pistas."
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }

    SectionLabel("Mapeo de botones físicos")
    SettingsCard {
        LogicalButton.entries.forEach { button ->
            SettingsRow(title = button.name, subtitle = button.description()) {
                val listening = listeningForButton == button
                Box(
                    modifier = Modifier
                        .background(if (listening) palette.accent else palette.chip, RoundedCornerShape(percent = 50))
                        .clickable {
                            if (listening) viewModel.cancelListening() else viewModel.startListeningForButton(button)
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        if (listening) "Presiona un botón…" else android.view.KeyEvent.keyCodeToString(buttonMapping.keyCodeFor(button)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (listening) Color.White else palette.ink,
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(color = palette.line)
        }
        Box(modifier = Modifier.padding(vertical = 14.dp)) {
            Text(
                "Restablecer mapeo por defecto",
                style = MaterialTheme.typography.labelLarge,
                color = palette.accentInk,
                modifier = Modifier.clickable { viewModel.resetButtonMapping() },
            )
        }
    }
}

@Composable
private fun CarpetasTabContent(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val userFolder by viewModel.settings.userFolderUri.collectAsState(initial = null)
    val romFolder by viewModel.settings.romFolderUri.collectAsState(initial = null)
    val savesFolder by viewModel.settings.retroArchSavesUri.collectAsState(initial = null)
    val lastScanAt by viewModel.settings.lastScanAt.collectAsState(initial = null)
    val isScanningLibrary by viewModel.isScanningLibrary.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    var showScanDialog by remember { mutableStateOf(false) }

    val pickUserFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onUserFolderPicked(context, it) }
    }
    val pickRomFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onRomFolderPicked(context, it) }
    }
    val pickSavesFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onSavesFolderPicked(context, it) }
    }

    FolderCard(title = "Carpeta de Usuario (Assets)", path = userFolder, onPick = { pickUserFolder.launch(null) })

    FolderCard(
        title = "Carpeta de ROMs",
        path = romFolder,
        onPick = { pickRomFolder.launch(null) },
        note = lastScanAt?.let { "Último escaneo: ${formatScanTimestamp(it)}" }
            ?: "Todavía no se escaneó la biblioteca — la navegación siempre lee de ese último escaneo, no del disco.",
        scanLabel = if (isScanningLibrary) "Escaneando…" else "Escanear biblioteca",
        onScan = {
            showScanDialog = true
            viewModel.scanLibrary(context)
        },
        scanEnabled = romFolder != null && !isScanningLibrary,
    )

    FolderCard(
        title = "Estados guardados de RetroArch (opcional)",
        path = savesFolder,
        onPick = { pickSavesFolder.launch(null) },
        note = "Activá \"savestate_thumbnail_enable\" en RetroArch para que se generen las miniaturas que esta app necesita.",
    )

    if (showScanDialog) {
        ScanProgressDialog(
            scanning = isScanningLibrary,
            entries = scanProgress,
            onStop = {
                viewModel.stopScanning()
                showScanDialog = false
            },
            onDismiss = { showScanDialog = false },
        )
    }
}

@Composable
private fun FolderCard(
    title: String,
    path: String?,
    onPick: () -> Unit,
    note: String? = null,
    scanLabel: String? = null,
    onScan: (() -> Unit)? = null,
    scanEnabled: Boolean = true,
) {
    val palette = LocalGinnyPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .border(1.dp, palette.line, RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = palette.ink)
                Text(
                    path?.let { Uri.parse(it).path ?: it } ?: "No configurada",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = palette.accentInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(palette.chip, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .background(palette.chip, RoundedCornerShape(percent = 50))
                        .clickable(onClick = onPick)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text("Elegir carpeta", style = MaterialTheme.typography.labelLarge, color = palette.accentInk)
                }
                if (scanLabel != null && onScan != null) {
                    val scanBackground = if (scanEnabled) palette.grad else androidx.compose.ui.graphics.Brush.linearGradient(listOf(palette.line, palette.line))
                    Box(
                        modifier = Modifier
                            .background(scanBackground, RoundedCornerShape(percent = 50))
                            .clickable(enabled = scanEnabled, onClick = onScan)
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        Text(scanLabel, style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatScanTimestamp(epochMillis: Long): String {
    val formatter = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(epochMillis))
}

@Composable
private fun EmuladoresTabContent(viewModel: SettingsViewModel) {
    val userFolderUri by viewModel.settings.userFolderUri.collectAsState(initial = null)
    SettingsCard {
        viewModel.systems.forEachIndexed { index, system ->
            SystemEmulatorRow(system = system, viewModel = viewModel, userFolderUri = userFolderUri)
            if (index != viewModel.systems.lastIndex) {
                androidx.compose.material3.HorizontalDivider(color = LocalGinnyPalette.current.line)
            }
        }
    }
}

@Composable
private fun SystemEmulatorRow(
    system: SystemDefinition,
    viewModel: SettingsViewModel,
    userFolderUri: String?,
) {
    val palette = LocalGinnyPalette.current
    val emulator by viewModel.settings.emulatorFor(system.id).collectAsState(initial = system.defaultEmulator)
    val coreId by viewModel.settings.retroArchCoreFor(system.id).collectAsState(initial = system.defaultRetroArchCoreId)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SystemIconBox(system = system, userFolderUri = userFolderUri, viewModel = viewModel, modifier = Modifier.size(EmulatorRowIconSize))

        Column(modifier = Modifier.width(160.dp)) {
            Text(system.displayName, style = MaterialTheme.typography.titleSmall, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        PillDropdown(
            selectedLabel = emulator.displayName,
            options = system.availableEmulators.map { it.displayName to it },
            onSelect = { viewModel.setEmulator(system.id, it) },
            modifier = Modifier.width(200.dp),
        )

        CoreOrOverrideField(system = system, emulator = emulator, coreId = coreId, viewModel = viewModel)
    }
}

@Composable
private fun SystemIconBox(system: SystemDefinition, userFolderUri: String?, viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val palette = LocalGinnyPalette.current
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, palette.line, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        SystemLogoBox(
            system = system,
            userFolderUri = userFolderUri,
            assetRepository = viewModel.assetRepository,
            imagePadding = 8.dp,
        )
    }
}

@Composable
private fun CoreOrOverrideField(system: SystemDefinition, emulator: EmulatorTarget, coreId: String?, viewModel: SettingsViewModel) {
    val palette = LocalGinnyPalette.current
    if (emulator in EmulatorTarget.RETROARCH_VARIANTS && system.retroArchCores.isNotEmpty()) {
        val selectedCore = system.retroArchCores.firstOrNull { it.id == coreId } ?: system.retroArchCores.first()
        PillDropdown(
            selectedLabel = selectedCore.label,
            options = system.retroArchCores.map { it.label to it.id },
            onSelect = { viewModel.setCore(system.id, it) },
            modifier = Modifier.width(200.dp),
            background = Color.White,
            bordered = true,
        )
    } else if (system.supportsPackageOverride) {
        val packageOverride by viewModel.settings.packageOverrideFor(system.id).collectAsState(initial = null)
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(EmulatorRowControlHeight)
                .background(Color.White, RoundedCornerShape(23.dp))
                .border(1.dp, palette.line, RoundedCornerShape(23.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = packageOverride.orEmpty(),
                onValueChange = { viewModel.setPackageOverride(system.id, it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelLarge.copy(color = palette.ink),
                cursorBrush = SolidColor(palette.accent),
                decorationBox = { inner ->
                    if (packageOverride.isNullOrEmpty()) {
                        Text("Package…", style = MaterialTheme.typography.labelLarge, color = palette.muted)
                    }
                    inner()
                },
            )
        }
    } else {
        Box(modifier = Modifier.width(200.dp))
    }
}

private fun MusicSelection.label(): String = when (this) {
    is MusicSelection.Bundled -> track.displayName
    is MusicSelection.Custom -> displayName
}

private fun LogicalButton.description(): String = when (this) {
    LogicalButton.A -> "Abrir / seleccionar"
    LogicalButton.B -> "Retroceder"
    LogicalButton.Y -> "Opciones"
    LogicalButton.MENU -> "Abrir configuración"
    LogicalButton.L -> "Saltar página hacia atrás"
    LogicalButton.R -> "Saltar página hacia adelante"
    LogicalButton.DPAD_UP -> "Mover foco arriba"
    LogicalButton.DPAD_DOWN -> "Mover foco abajo"
    LogicalButton.DPAD_LEFT -> "Mover foco a la izquierda"
    LogicalButton.DPAD_RIGHT -> "Mover foco a la derecha"
}
