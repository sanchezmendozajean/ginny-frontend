package com.ginny.frontend.ui.screens.games

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.launch.LaunchResult
import com.ginny.frontend.ui.common.DeleteGameDialog
import com.ginny.frontend.ui.common.EmulatorConfigDialog
import com.ginny.frontend.ui.common.gameCountLabel
import com.ginny.frontend.ui.components.AuroraBackground
import com.ginny.frontend.ui.components.FocusableTile
import com.ginny.frontend.ui.components.GinnyBottomBar
import com.ginny.frontend.ui.components.GinnyTopBar
import com.ginny.frontend.ui.components.GridPanel
import com.ginny.frontend.ui.components.MenuAction
import com.ginny.frontend.ui.components.SnapFocusGrid
import com.ginny.frontend.ui.components.TilePlaceholderLabel
import kotlinx.coroutines.launch

/** [manual] distinguishes "Seleccionar emulador" from the menu (emulator may already be fine, just
 * changing it) from the modal popping up because a launch actually failed — they show different
 * copy in [EmulatorConfigDialog]. */
private data class EmulatorConfigRequest(val game: GameEntry, val manual: Boolean)

@Composable
fun GamesScreen(
    soundManager: SoundManager,
    onBack: () -> Unit,
    onOpenSaveStates: (GameEntry) -> Unit,
    onOpenSettings: () -> Unit,
    menuExpanded: MutableState<Boolean>,
    viewModel: GamesViewModel,
) {
    val games by viewModel.games.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val buttonMapping by viewModel.buttonMapping.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var emulatorConfigRequest by remember { mutableStateOf<EmulatorConfigRequest?>(null) }
    var gameToDelete by remember { mutableStateOf<GameEntry?>(null) }

    val selected = games.getOrNull(viewModel.focusedIndex)

    fun activate(game: GameEntry) {
        soundManager.playActivateSound()
        scope.launch {
            when (val outcome = viewModel.onGameSelected(game)) {
                is GameSelectionOutcome.NavigateToSaveStates -> onOpenSaveStates(outcome.game)
                is GameSelectionOutcome.Launched -> when (val result = outcome.result) {
                    is LaunchResult.EmulatorNotInstalled -> emulatorConfigRequest = EmulatorConfigRequest(game, manual = false)
                    is LaunchResult.Failed -> Unit // best-effort v1: silently ignore, ROM likely malformed
                    LaunchResult.Launched -> Unit
                }
            }
        }
    }

    AuroraBackground {
        Column(modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
            GinnyTopBar(
                contextualTitle = "${viewModel.systemDefinition.displayName} - ${gameCountLabel(games.size)}",
                onOptionsClick = onOpenSettings,
                menuExpanded = menuExpanded,
                buttonMapping = buttonMapping,
                extraMenuGroups = listOf(
                    listOfNotNull(
                        selected?.let { game ->
                            MenuAction("Seleccionar emulador") {
                                emulatorConfigRequest = EmulatorConfigRequest(game, manual = true)
                            }
                        },
                        MenuAction("Reescanear este sistema") { viewModel.rescanSystem(context) },
                    ),
                    listOfNotNull(
                        selected?.let { game -> MenuAction("Eliminar juego") { gameToDelete = game } },
                    ),
                ),
            )
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
            GinnyBottomBar(
                selectedName = selected?.displayName.orEmpty(),
                selectedSecondaryText = selected?.takeIf { it.romFileName != it.displayName }?.romFileName,
                showBack = true,
                onBackClick = onBack,
                onOpenClick = { selected?.let(::activate) },
            )
        }
    }

    emulatorConfigRequest?.let { request ->
        val game = request.game
        val currentEmulator by viewModel.settingsRepository.emulatorFor(viewModel.systemId)
            .collectAsState(initial = viewModel.systemDefinition.defaultEmulator)
        val currentCoreId by viewModel.settingsRepository.retroArchCoreFor(viewModel.systemId)
            .collectAsState(initial = viewModel.systemDefinition.defaultRetroArchCoreId)
        val currentPackageOverride by viewModel.settingsRepository.packageOverrideFor(viewModel.systemId)
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
                    viewModel.settingsRepository.setEmulatorFor(viewModel.systemId, emulator)
                    coreId?.let { viewModel.settingsRepository.setRetroArchCoreFor(viewModel.systemId, it) }
                    packageOverride?.let { viewModel.settingsRepository.setPackageOverride(viewModel.systemId, it) }
                    emulatorConfigRequest = null
                    activate(game)
                }
            },
            onSaveAndClose = { emulator, coreId, packageOverride ->
                scope.launch {
                    viewModel.settingsRepository.setEmulatorFor(viewModel.systemId, emulator)
                    coreId?.let { viewModel.settingsRepository.setRetroArchCoreFor(viewModel.systemId, it) }
                    packageOverride?.let { viewModel.settingsRepository.setPackageOverride(viewModel.systemId, it) }
                    emulatorConfigRequest = null
                }
            },
            onCancel = { emulatorConfigRequest = null },
            title = if (request.manual) "Seleccionar emulador" else "Emulador no encontrado",
            description = if (request.manual) {
                "Elegí el emulador (y el core, si aplica) para ${viewModel.systemDefinition.displayName}."
            } else {
                "El emulador seleccionado no se encuentra instalado en el dispositivo. " +
                    "Selecciona un emulador diferente."
            },
        )
    }

    gameToDelete?.let { game ->
        DeleteGameDialog(
            game = game,
            onConfirm = {
                scope.launch {
                    viewModel.deleteGame(context, game)
                    gameToDelete = null
                }
            },
            onCancel = { gameToDelete = null },
        )
    }
}
