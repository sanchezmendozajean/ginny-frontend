package com.ginny.frontend.ui.screens.savestates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.model.SaveStateEntry
import com.ginny.frontend.launch.LaunchResult
import com.ginny.frontend.ui.common.EmulatorConfigDialog
import com.ginny.frontend.ui.components.AuroraBackground
import com.ginny.frontend.ui.components.BottomBarHint
import com.ginny.frontend.ui.components.FocusableTile
import com.ginny.frontend.ui.components.GinnyBottomBar
import com.ginny.frontend.ui.components.GinnyTopBar
import com.ginny.frontend.ui.components.GinnyScreenPadding
import com.ginny.frontend.ui.components.GridPanel
import com.ginny.frontend.ui.components.PagedFocusGrid
import com.ginny.frontend.ui.components.PagingInfo
import com.ginny.frontend.ui.components.TopBarLeading
import com.ginny.frontend.ui.theme.LocalGinnyPalette
import com.ginny.frontend.ui.theme.tileHue
import kotlinx.coroutines.launch

@Composable
fun SaveStatesScreen(
    soundManager: SoundManager,
    systemDisplayName: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SaveStatesViewModel,
) {
    val entries by viewModel.entries.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val buttonMapping by viewModel.buttonMapping.collectAsState()
    val scope = rememberCoroutineScope()
    var emulatorConfigEntry by remember { mutableStateOf<SaveStateEntry?>(null) }
    var paging by remember { mutableStateOf(PagingInfo(0, 1) {}) }

    val selected = entries.getOrNull(viewModel.focusedIndex)
    val hue = viewModel.systemDefinition.id.tileHue()

    fun activate(entry: SaveStateEntry) {
        soundManager.playActivateSound()
        scope.launch {
            when (viewModel.launch(entry)) {
                is LaunchResult.EmulatorNotInstalled -> emulatorConfigEntry = entry
                is LaunchResult.Failed, LaunchResult.Launched -> Unit
            }
        }
    }

    AuroraBackground {
        Column(modifier = Modifier.fillMaxSize().padding(GinnyScreenPadding)) {
            GinnyTopBar(
                leading = TopBarLeading.SystemBack(systemName = systemDisplayName, systemHue = hue, onBack = onBack),
                onOpenSettings = onOpenSettings,
            )
            GridPanel(modifier = Modifier.weight(1f)) {
                PagedFocusGrid(
                    entries = entries,
                    visibleRows = gridRows,
                    focusedIndex = viewModel.focusedIndex,
                    onFocusedIndexChange = {
                        if (it != viewModel.focusedIndex) soundManager.playFocusSound()
                        viewModel.focusedIndex = it
                    },
                    onActivate = ::activate,
                    onSecondaryAction = { onOpenSettings() },
                    buttonMapping = buttonMapping,
                    onPagingChanged = { current, count, goTo -> paging = PagingInfo(current, count, goTo) },
                    modifier = Modifier.fillMaxSize(),
                ) { entry, _, focused, tileSize ->
                    FocusableTile(focused = focused, tileSize = tileSize, frame = com.ginny.frontend.ui.theme.diagonalGradient(Color.White, Color.White)) {
                        SaveStateTileContent(entry)
                    }
                }
            }
            GinnyBottomBar(
                selectedName = selected.label(),
                leadingHue = hue,
                pageCount = paging.count,
                currentPage = paging.page,
                onPageSelected = { paging.goTo(it) },
                hints = listOf(
                    BottomBarHint("A", if (selected is SaveStateEntry.NewGame) "Iniciar" else "Cargar") { selected?.let(::activate) },
                    BottomBarHint("B", "Atrás", onClick = onBack),
                    BottomBarHint("Y", "Opciones", onClick = onOpenSettings),
                ),
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
    val palette = LocalGinnyPalette.current
    when (entry) {
        SaveStateEntry.NewGame -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.chip, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", style = MaterialTheme.typography.displaySmall, color = palette.accentInk)
                Text("Juego nuevo", style = MaterialTheme.typography.labelMedium, color = palette.accentInk)
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(entry.label, style = MaterialTheme.typography.labelMedium, color = palette.ink)
                }
            }
        }
    }
}
