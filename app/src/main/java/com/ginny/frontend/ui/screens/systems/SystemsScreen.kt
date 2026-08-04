package com.ginny.frontend.ui.screens.systems

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.di.AppContainer
import com.ginny.frontend.ui.common.gameCountLabel
import com.ginny.frontend.ui.components.AuroraBackground
import com.ginny.frontend.ui.components.FocusableTile
import com.ginny.frontend.ui.components.GinnyBottomBar
import com.ginny.frontend.ui.components.GinnyTopBar
import com.ginny.frontend.ui.components.GridPanel
import com.ginny.frontend.ui.components.SnapFocusGrid
import com.ginny.frontend.ui.components.TilePlaceholderLabel
import com.ginny.frontend.ui.theme.GinnyColors

@Composable
fun SystemsScreen(
    container: AppContainer,
    soundManager: SoundManager,
    onOpenSystem: (SystemDefinition) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SystemsViewModel,
) {
    val gridRows by viewModel.gridRows.collectAsState()
    val buttonMapping by viewModel.buttonMapping.collectAsState()
    val userFolderUri by container.settingsRepository.userFolderUri.collectAsState(initial = null)
    val romFolderUri by container.settingsRepository.romFolderUri.collectAsState(initial = null)
    val systems by viewModel.availableSystems.collectAsState()
    val gameCounts by viewModel.gameCounts.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val selected = systems.getOrNull(viewModel.focusedIndex)

    AuroraBackground {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            GinnyTopBar(contextualTitle = "Ginny Frontend", onOptionsClick = onOpenSettings)
            Spacer(Modifier.height(14.dp))
            GridPanel(modifier = Modifier.weight(1f)) {
                if (systems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                isScanning -> ""
                                romFolderUri == null -> "Configurá la carpeta de ROMs en Configuración (botón X) para empezar."
                                else -> "No se encontraron juegos. Presioná \"Escanear biblioteca\" en Configuración > Carpetas."
                            },
                        )
                    }
                } else {
                    SnapFocusGrid(
                        entries = systems,
                        visibleRows = gridRows,
                        focusedIndex = viewModel.focusedIndex,
                        onFocusedIndexChange = {
                            if (it != viewModel.focusedIndex) soundManager.playFocusSound()
                            viewModel.focusedIndex = it
                        },
                        onActivate = { system ->
                            soundManager.playActivateSound()
                            onOpenSystem(system)
                        },
                        buttonMapping = buttonMapping,
                        itemKey = { it.id },
                        modifier = Modifier.fillMaxSize(),
                    ) { system, focused, tileSize ->
                        FocusableTile(focused = focused, modifier = Modifier.size(tileSize)) {
                            SystemLogoOrPlaceholder(system, userFolderUri, container)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GinnyBottomBar(
                selectedName = selected?.let { "${it.displayName} - ${gameCountLabel(gameCounts[it.id] ?: 0)}" }.orEmpty(),
                showBack = false,
                onBackClick = {},
                onOpenClick = {
                    selected?.let {
                        soundManager.playActivateSound()
                        onOpenSystem(it)
                    }
                },
            )
        }
    }
}

@Composable
private fun SystemLogoOrPlaceholder(
    system: SystemDefinition,
    userFolderUri: String?,
    container: AppContainer,
) {
    val logoUri by produceState<android.net.Uri?>(initialValue = null, userFolderUri, system.id) {
        value = userFolderUri?.let { uriString ->
            runCatching {
                container.assetRepository.systemLogoUri(android.net.Uri.parse(uriString), system.id)
            }.getOrNull()
        }
    }

    if (logoUri != null) {
        AsyncImage(
            model = logoUri,
            contentDescription = system.displayName,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        val colorIndex = system.id.ordinal % GinnyColors.SystemColors.size
        TilePlaceholderLabel(text = system.displayName, color = GinnyColors.SystemColors[colorIndex])
    }
}
