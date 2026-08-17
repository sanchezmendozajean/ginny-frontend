package com.ginny.frontend.ui.screens.savestates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.model.SaveStateEntry
import com.ginny.frontend.launch.LaunchResult
import com.ginny.frontend.ui.common.EmulatorConfigDialog
import com.ginny.frontend.ui.components.AuroraBackground
import com.ginny.frontend.ui.components.FocusableTile
import com.ginny.frontend.ui.components.GinnyBottomBar
import com.ginny.frontend.ui.components.GinnyTopBar
import com.ginny.frontend.ui.components.GridPanel
import com.ginny.frontend.ui.components.SnapFocusGrid
import com.ginny.frontend.ui.theme.GinnyColors
import kotlinx.coroutines.launch

@Composable
fun SaveStatesScreen(
    soundManager: SoundManager,
    systemDisplayName: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    menuExpanded: MutableState<Boolean>,
    viewModel: SaveStatesViewModel,
) {
    val entries by viewModel.entries.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val buttonMapping by viewModel.buttonMapping.collectAsState()
    val scope = rememberCoroutineScope()
    var emulatorConfigEntry by remember { mutableStateOf<SaveStateEntry?>(null) }

    val selected = entries.getOrNull(viewModel.focusedIndex)

    fun activate(entry: SaveStateEntry) {
        soundManager.playActivateSound()
        scope.launch {
            when (val result = viewModel.launch(entry)) {
                is LaunchResult.EmulatorNotInstalled -> emulatorConfigEntry = entry
                is LaunchResult.Failed, LaunchResult.Launched -> Unit
            }
        }
    }

    AuroraBackground {
        Column(modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
            GinnyTopBar(
                contextualTitle = systemDisplayName,
                onOptionsClick = onOpenSettings,
                menuExpanded = menuExpanded,
                buttonMapping = buttonMapping,
            )
            GridPanel(modifier = Modifier.weight(1f)) {
                SnapFocusGrid(
                    entries = entries,
                    visibleRows = gridRows,
                    focusedIndex = viewModel.focusedIndex,
                    onFocusedIndexChange = {
                        if (it != viewModel.focusedIndex) soundManager.playFocusSound()
                        viewModel.focusedIndex = it
                    },
                    onActivate = ::activate,
                    buttonMapping = buttonMapping,
                    itemKey = { entry ->
                        when (entry) {
                            SaveStateEntry.NewGame -> "new_game"
                            is SaveStateEntry.Slot -> "slot_${entry.slotIndex}"
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { entry, focused, tileSize ->
                    FocusableTile(focused = focused, modifier = Modifier.size(tileSize)) {
                        SaveStateTileContent(entry)
                    }
                }
            }
            GinnyBottomBar(
                selectedName = selected.label(),
                showBack = true,
                onBackClick = onBack,
                onOpenClick = { selected?.let(::activate) },
                openLabel = if (selected is SaveStateEntry.NewGame) "Iniciar" else "Cargar",
            )
        }
    }

    emulatorConfigEntry?.let { entry ->
        val currentEmulator by viewModel.settingsRepository.emulatorFor(viewModel.systemDefinition.id)
            .collectAsState(initial = viewModel.systemDefinition.defaultEmulator)
        val currentCoreId by viewModel.settingsRepository.retroArchCoreFor(viewModel.systemDefinition.id)
            .collectAsState(initial = viewModel.systemDefinition.defaultRetroArchCoreId)
        val currentPackageOverride by viewModel.settingsRepository.packageOverrideFor(viewModel.systemDefinition.id)
            .collectAsState(initial = null)
        val userFolderUri by viewModel.settingsRepository.userFolderUri.collectAsState(initial = null)

        EmulatorConfigDialog(
            system = viewModel.systemDefinition,
            assetRepository = viewModel.assetRepository,
            userFolderUri = userFolderUri,
            initialEmulator = currentEmulator,
            initialCoreId = currentCoreId,
            initialPackageOverride = currentPackageOverride,
            onSaveAndLaunch = { emulator, coreId, packageOverride ->
                scope.launch {
                    viewModel.settingsRepository.setEmulatorFor(viewModel.systemDefinition.id, emulator)
                    coreId?.let { viewModel.settingsRepository.setRetroArchCoreFor(viewModel.systemDefinition.id, it) }
                    packageOverride?.let { viewModel.settingsRepository.setPackageOverride(viewModel.systemDefinition.id, it) }
                    emulatorConfigEntry = null
                    activate(entry)
                }
            },
            onSaveAndClose = { emulator, coreId, packageOverride ->
                scope.launch {
                    viewModel.settingsRepository.setEmulatorFor(viewModel.systemDefinition.id, emulator)
                    coreId?.let { viewModel.settingsRepository.setRetroArchCoreFor(viewModel.systemDefinition.id, it) }
                    packageOverride?.let { viewModel.settingsRepository.setPackageOverride(viewModel.systemDefinition.id, it) }
                    emulatorConfigEntry = null
                }
            },
            onCancel = { emulatorConfigEntry = null },
        )
    }
}

private fun SaveStateEntry?.label(): String = when (this) {
    null -> ""
    SaveStateEntry.NewGame -> "Juego nuevo"
    is SaveStateEntry.Slot -> label
}

@Composable
private fun SaveStateTileContent(entry: SaveStateEntry) {
    when (entry) {
        SaveStateEntry.NewGame -> androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", style = MaterialTheme.typography.displaySmall, color = GinnyColors.AccentBlue)
                Text("Juego nuevo", style = MaterialTheme.typography.labelMedium, color = GinnyColors.AccentBlue)
            }
        }

        is SaveStateEntry.Slot -> {
            if (entry.thumbnailUri != null) {
                AsyncImage(
                    model = entry.thumbnailUri,
                    contentDescription = entry.label,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(entry.label, style = MaterialTheme.typography.labelMedium, color = GinnyColors.TextPrimary)
                }
            }
        }
    }
}
