package com.ginny.frontend.ui.screens.games

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.launch.LaunchResult
import com.ginny.frontend.ui.common.EmulatorMissingDialog
import com.ginny.frontend.ui.common.gameCountLabel
import com.ginny.frontend.ui.components.AuroraBackground
import com.ginny.frontend.ui.components.FocusableTile
import com.ginny.frontend.ui.components.GinnyBottomBar
import com.ginny.frontend.ui.components.GinnyTopBar
import com.ginny.frontend.ui.components.GridPanel
import com.ginny.frontend.ui.components.SnapFocusGrid
import com.ginny.frontend.ui.components.TilePlaceholderLabel
import kotlinx.coroutines.launch

@Composable
fun GamesScreen(
    soundManager: SoundManager,
    onBack: () -> Unit,
    onOpenSaveStates: (GameEntry) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: GamesViewModel,
) {
    val games by viewModel.games.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val buttonMapping by viewModel.buttonMapping.collectAsState()
    val scope = rememberCoroutineScope()

    var missingEmulatorDialog by remember { mutableStateOf<Pair<com.ginny.frontend.data.model.EmulatorTarget, String?>?>(null) }

    val selected = games.getOrNull(viewModel.focusedIndex)

    fun activate(game: GameEntry) {
        soundManager.playActivateSound()
        scope.launch {
            when (val outcome = viewModel.onGameSelected(game)) {
                is GameSelectionOutcome.NavigateToSaveStates -> onOpenSaveStates(outcome.game)
                is GameSelectionOutcome.Launched -> when (val result = outcome.result) {
                    is LaunchResult.EmulatorNotInstalled ->
                        missingEmulatorDialog = result.emulator to result.storeUrl
                    is LaunchResult.Failed -> Unit // best-effort v1: silently ignore, ROM likely malformed
                    LaunchResult.Launched -> Unit
                }
            }
        }
    }

    AuroraBackground {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            GinnyTopBar(
                contextualTitle = "${viewModel.systemDefinition.displayName} - ${gameCountLabel(games.size)}",
                onOptionsClick = onOpenSettings,
            )
            Spacer(Modifier.height(14.dp))
            GridPanel(modifier = Modifier.weight(1f)) {
                if (games.isEmpty()) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (isScanning) {
                                ""
                            } else {
                                "No se encontraron juegos para ${viewModel.systemDefinition.displayName}. " +
                                    "Presioná \"Escanear biblioteca\" en Configuración > Carpetas."
                            },
                        )
                    }
                } else {
                    SnapFocusGrid(
                        entries = games,
                        visibleRows = gridRows,
                        focusedIndex = viewModel.focusedIndex,
                        onFocusedIndexChange = {
                            if (it != viewModel.focusedIndex) soundManager.playFocusSound()
                            viewModel.focusedIndex = it
                        },
                        onActivate = ::activate,
                        buttonMapping = buttonMapping,
                        itemKey = { it.romUri },
                        modifier = Modifier.fillMaxSize(),
                    ) { game, focused, tileSize ->
                        FocusableTile(focused = focused, modifier = Modifier.size(tileSize)) {
                            if (game.iconUri != null) {
                                AsyncImage(
                                    model = game.iconUri,
                                    contentDescription = game.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                TilePlaceholderLabel(text = game.displayName)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GinnyBottomBar(
                selectedName = selected?.displayName.orEmpty(),
                selectedSecondaryText = selected?.takeIf { it.romFileName != it.displayName }?.romFileName,
                showBack = true,
                onBackClick = onBack,
                onOpenClick = { selected?.let(::activate) },
                onReloadClick = {
                    soundManager.playActivateSound()
                    viewModel.reload()
                },
            )
        }
    }

    missingEmulatorDialog?.let { (emulator, storeUrl) ->
        EmulatorMissingDialog(emulator = emulator, storeUrl = storeUrl, onDismiss = { missingEmulatorDialog = null })
    }
}
