package com.ginny.frontend.ui.screens.games

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.favoriteKey
import com.ginny.frontend.launch.LaunchResult
import com.ginny.frontend.ui.common.EmulatorConfigDialog
import com.ginny.frontend.ui.common.GameOptionsDialog
import com.ginny.frontend.ui.components.AuroraBackground
import com.ginny.frontend.ui.components.BottomBarHint
import com.ginny.frontend.ui.components.FocusableTile
import com.ginny.frontend.ui.components.GinnyBottomBar
import com.ginny.frontend.ui.components.GinnyTopBar
import com.ginny.frontend.ui.components.GinnyScreenPadding
import com.ginny.frontend.ui.components.GridPanel
import com.ginny.frontend.ui.components.PagedFocusGrid
import com.ginny.frontend.ui.components.PagingInfo
import com.ginny.frontend.ui.components.TileStripedPlaceholder
import com.ginny.frontend.ui.components.TopBarLeading
import com.ginny.frontend.ui.theme.LocalGinnyPalette
import com.ginny.frontend.ui.theme.gameTileHue
import com.ginny.frontend.ui.theme.tileHue
import kotlinx.coroutines.launch

@Composable
fun GamesScreen(
    soundManager: SoundManager,
    onBack: () -> Unit,
    onOpenSaveStates: (GameEntry) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: GamesViewModel,
) {
    val palette = LocalGinnyPalette.current
    val games by viewModel.games.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val buttonMapping by viewModel.buttonMapping.collectAsState()
    val favoriteKeys by viewModel.favoriteGameKeys.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var emulatorNotInstalledFor by remember { mutableStateOf<GameEntry?>(null) }
    var gameOptionsFor by remember { mutableStateOf<GameEntry?>(null) }
    var paging by remember { mutableStateOf(PagingInfo(0, 1) {}) }

    val selected = games.getOrNull(viewModel.focusedIndex)
    val title = when (viewModel.scope) {
        is GamesScope.Favorites -> "Favoritos"
        is GamesScope.Single -> viewModel.systemDefinition!!.displayName
    }
    val headerHue = when (val s = viewModel.scope) {
        is GamesScope.Single -> s.systemId.tileHue()
        GamesScope.Favorites -> palette.hue
    }

    fun activate(game: GameEntry) {
        soundManager.playActivateSound()
        scope.launch {
            when (val outcome = viewModel.onGameSelected(game)) {
                is GameSelectionOutcome.NavigateToSaveStates -> onOpenSaveStates(outcome.game)
                is GameSelectionOutcome.Launched -> when (outcome.result) {
                    is LaunchResult.EmulatorNotInstalled -> emulatorNotInstalledFor = game
                    is LaunchResult.Failed -> Unit // best-effort v1: silently ignore, ROM likely malformed
                    LaunchResult.Launched -> Unit
                }
            }
        }
    }

    AuroraBackground {
        Column(modifier = Modifier.fillMaxSize().padding(GinnyScreenPadding)) {
            GinnyTopBar(
                leading = TopBarLeading.SystemBack(systemName = title, systemHue = headerHue, onBack = onBack),
                onOpenSettings = onOpenSettings,
            )
            GridPanel(modifier = Modifier.weight(1f)) {
                if (games.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                isScanning -> ""
                                viewModel.scope is GamesScope.Favorites -> "Todavía no marcaste ningún juego como favorito."
                                else -> "No se encontraron juegos para $title. " +
                                    "Presioná \"Reescanear este sistema\" desde las opciones de un juego, o " +
                                    "\"Escanear biblioteca\" en Configuración > Carpetas."
                            },
                            color = palette.muted,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    PagedFocusGrid(
                        entries = games,
                        visibleRows = gridRows,
                        focusedIndex = viewModel.focusedIndex,
                        onFocusedIndexChange = {
                            if (it != viewModel.focusedIndex) soundManager.playFocusSound()
                            viewModel.focusedIndex = it
                        },
                        onActivate = ::activate,
                        onSecondaryAction = { game -> gameOptionsFor = game },
                        buttonMapping = buttonMapping,
                        onPagingChanged = { current, count, goTo -> paging = PagingInfo(current, count, goTo) },
                        modifier = Modifier.fillMaxSize(),
                    ) { game, index, focused, tileSize ->
                        val hue = game.systemId.gameTileHue(index)
                        FocusableTile(focused = focused, tileSize = tileSize, frame = WhiteFrame) {
                            Box {
                                if (game.iconUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(game.iconUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = game.displayName,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    TileStripedPlaceholder(hue = hue)
                                }
                                if (game.favoriteKey() in favoriteKeys) {
                                    FavoriteBadge(modifier = Modifier.align(Alignment.TopEnd))
                                }
                            }
                        }
                    }
                }
            }
            GinnyBottomBar(
                selectedName = selected?.displayName.orEmpty(),
                leadingHue = selected?.let { it.systemId.gameTileHue(games.indexOf(it)) } ?: headerHue,
                selectedSecondaryText = selected?.takeIf { it.romFileName != it.displayName }?.romFileName
                    ?: selected?.takeIf { viewModel.scope is GamesScope.Favorites }
                        ?.let { SystemCatalog.byId.getValue(it.systemId).displayName },
                pageCount = paging.count,
                currentPage = paging.page,
                onPageSelected = { paging.goTo(it) },
                hints = buildList {
                    add(BottomBarHint("A", "Jugar") { selected?.let(::activate) })
                    add(BottomBarHint("B", "Atrás", onClick = onBack))
                    add(BottomBarHint("Y", "Opciones") { selected?.let { gameOptionsFor = it } })
                },
            )
        }
    }

    emulatorNotInstalledFor?.let { game ->
        val system = SystemCatalog.byId.getValue(game.systemId)
        val currentEmulator by viewModel.settingsRepository.emulatorFor(game.systemId)
            .collectAsState(initial = system.defaultEmulator)
        val currentCoreId by viewModel.settingsRepository.retroArchCoreFor(game.systemId)
            .collectAsState(initial = system.defaultRetroArchCoreId)
        val currentPackageOverride by viewModel.settingsRepository.packageOverrideFor(game.systemId)
            .collectAsState(initial = null)
        val userFolderUri by viewModel.settingsRepository.userFolderUri.collectAsState(initial = null)

        EmulatorConfigDialog(
            system = system,
            assetRepository = viewModel.assetRepository,
            userFolderUri = userFolderUri,
            initialEmulator = currentEmulator,
            initialCoreId = currentCoreId,
            initialPackageOverride = currentPackageOverride,
            onSaveAndLaunch = { emulator, coreId, packageOverride ->
                scope.launch {
                    viewModel.settingsRepository.setEmulatorFor(game.systemId, emulator)
                    coreId?.let { viewModel.settingsRepository.setRetroArchCoreFor(game.systemId, it) }
                    packageOverride?.let { viewModel.settingsRepository.setPackageOverride(game.systemId, it) }
                    emulatorNotInstalledFor = null
                    activate(game)
                }
            },
            onSaveAndClose = { emulator, coreId, packageOverride ->
                scope.launch {
                    viewModel.settingsRepository.setEmulatorFor(game.systemId, emulator)
                    coreId?.let { viewModel.settingsRepository.setRetroArchCoreFor(game.systemId, it) }
                    packageOverride?.let { viewModel.settingsRepository.setPackageOverride(game.systemId, it) }
                    emulatorNotInstalledFor = null
                }
            },
            onCancel = { emulatorNotInstalledFor = null },
        )
    }

    gameOptionsFor?.let { game ->
        val system = SystemCatalog.byId.getValue(game.systemId)
        val currentEmulator by viewModel.settingsRepository.emulatorFor(game.systemId)
            .collectAsState(initial = system.defaultEmulator)
        val currentCoreId by viewModel.settingsRepository.retroArchCoreFor(game.systemId)
            .collectAsState(initial = system.defaultRetroArchCoreId)
        val currentPackageOverride by viewModel.settingsRepository.packageOverrideFor(game.systemId)
            .collectAsState(initial = null)
        val romFolder by viewModel.settingsRepository.romFolderUri.collectAsState(initial = null)
        val romFilePath = romFolder?.let { "${Uri.parse(it).path ?: it}/${system.displayName}/" }

        GameOptionsDialog(
            game = game,
            system = system,
            romFilePath = romFilePath,
            isFavorite = game.favoriteKey() in favoriteKeys,
            currentEmulator = currentEmulator,
            currentCoreId = currentCoreId,
            onSetEmulator = { emulator -> scope.launch { viewModel.settingsRepository.setEmulatorFor(game.systemId, emulator) } },
            onSetCore = { coreId -> scope.launch { viewModel.settingsRepository.setRetroArchCoreFor(game.systemId, coreId) } },
            onSetPackageOverride = { pkg -> scope.launch { viewModel.settingsRepository.setPackageOverride(game.systemId, pkg) } },
            packageOverride = currentPackageOverride,
            onToggleFavorite = { scope.launch { viewModel.toggleFavorite(game) } },
            onRescanSystem = { viewModel.rescanSystem(context, game.systemId); gameOptionsFor = null },
            onDelete = { scope.launch { viewModel.deleteGame(context, game); gameOptionsFor = null } },
            onPlay = { gameOptionsFor = null; activate(game) },
            onDismiss = { gameOptionsFor = null },
        )
    }
}

private val WhiteFrame = Brush.linearGradient(listOf(Color.White, Color.White))

@Composable
private fun FavoriteBadge(modifier: Modifier = Modifier) {
    val palette = LocalGinnyPalette.current
    Box(
        modifier = modifier.padding(8.dp).size(28.dp).background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("★", color = palette.accent, style = MaterialTheme.typography.titleSmall)
    }
}
