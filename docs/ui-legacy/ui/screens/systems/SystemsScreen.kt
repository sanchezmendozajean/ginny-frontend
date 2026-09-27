package com.ginny.frontend.ui.screens.systems

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.di.AppContainer
import com.ginny.frontend.ui.common.gameCountLabel
import com.ginny.frontend.ui.components.AuroraBackground
import com.ginny.frontend.ui.components.BottomBarHint
import com.ginny.frontend.ui.components.FocusableTile
import com.ginny.frontend.ui.components.GinnyBottomBar
import com.ginny.frontend.ui.components.GinnyTopBar
import com.ginny.frontend.ui.components.GinnyScreenPadding
import com.ginny.frontend.ui.components.GridPanel
import com.ginny.frontend.ui.components.PagedFocusGrid
import com.ginny.frontend.ui.components.PagingInfo
import com.ginny.frontend.ui.components.SystemLogoBox
import com.ginny.frontend.ui.components.TopBarLeading
import com.ginny.frontend.ui.theme.LocalGinnyPalette
import com.ginny.frontend.ui.theme.diagonalGradient
import com.ginny.frontend.ui.theme.oklch
import com.ginny.frontend.ui.theme.tileHue

@Composable
fun SystemsScreen(
    container: AppContainer,
    soundManager: SoundManager,
    onOpenSystem: (SystemDefinition) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SystemsViewModel,
) {
    val palette = LocalGinnyPalette.current
    val gridRows by viewModel.gridRows.collectAsState()
    val buttonMapping by viewModel.buttonMapping.collectAsState()
    val userFolderUri by container.settingsRepository.userFolderUri.collectAsState(initial = null)
    val romFolderUri by container.settingsRepository.romFolderUri.collectAsState(initial = null)
    val tiles by viewModel.tiles.collectAsState()
    val gameCounts by viewModel.gameCounts.collectAsState()
    val favoriteCount by viewModel.favoriteCount.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    var paging by remember { mutableStateOf(PagingInfo(0, 1) {}) }

    val selected = tiles.getOrNull(viewModel.focusedIndex)

    fun open(item: SystemTileItem) {
        soundManager.playActivateSound()
        when (item) {
            SystemTileItem.Favorites -> onOpenFavorites()
            is SystemTileItem.Real -> onOpenSystem(item.system)
        }
    }

    AuroraBackground {
        Column(modifier = Modifier.fillMaxSize().padding(GinnyScreenPadding)) {
            GinnyTopBar(leading = TopBarLeading.AppLogo, onOpenSettings = onOpenSettings)
            GridPanel(modifier = Modifier.weight(1f)) {
                if (tiles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                isScanning -> ""
                                romFolderUri == null -> "Configura la carpeta de ROMs en Configuración."
                                else -> "No se encontraron juegos. Presiona \"Escanear biblioteca\" en Configuración > Carpetas."
                            },
                            color = palette.muted,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    PagedFocusGrid(
                        entries = tiles,
                        visibleRows = gridRows,
                        focusedIndex = viewModel.focusedIndex,
                        onFocusedIndexChange = {
                            if (it != viewModel.focusedIndex) soundManager.playFocusSound()
                            viewModel.focusedIndex = it
                        },
                        onActivate = ::open,
                        onSecondaryAction = { onOpenSettings() },
                        buttonMapping = buttonMapping,
                        onPagingChanged = { current, count, goTo -> paging = PagingInfo(current, count, goTo) },
                        modifier = Modifier.fillMaxSize(),
                    ) { item, _, focused, tileSize ->
                        when (item) {
                            SystemTileItem.Favorites -> FocusableTile(
                                focused = focused,
                                tileSize = tileSize,
                                frame = diagonalGradient(oklch(0.78f, 0.13f, palette.hue), oklch(0.83f, 0.11f, palette.hue + 50f)),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("★", color = palette.accentInk, style = MaterialTheme.typography.displaySmall)
                                }
                            }
                            is SystemTileItem.Real -> FocusableTile(
                                focused = focused,
                                tileSize = tileSize,
                                frame = diagonalGradient(
                                    oklch(0.78f, 0.13f, item.system.id.tileHue()),
                                    oklch(0.83f, 0.11f, item.system.id.tileHue() + 50f),
                                ),
                            ) {
                                SystemLogoBox(
                                    system = item.system,
                                    userFolderUri = userFolderUri,
                                    assetRepository = container.assetRepository,
                                )
                            }
                        }
                    }
                }
            }
            GinnyBottomBar(
                selectedName = selected?.let {
                    when (it) {
                        SystemTileItem.Favorites -> "Favoritos"
                        is SystemTileItem.Real -> it.system.displayName
                    }
                }.orEmpty(),
                leadingHue = selected?.let {
                    when (it) {
                        SystemTileItem.Favorites -> palette.hue
                        is SystemTileItem.Real -> it.system.id.tileHue()
                    }
                } ?: palette.hue,
                selectedSecondaryText = selected?.let {
                    when (it) {
                        SystemTileItem.Favorites -> gameCountLabel(favoriteCount)
                        is SystemTileItem.Real -> gameCountLabel(gameCounts[it.system.id] ?: 0)
                    }
                },
                pageCount = paging.count,
                currentPage = paging.page,
                onPageSelected = { paging.goTo(it) },
                hints = listOf(
                    BottomBarHint("A", "Abrir") { selected?.let(::open) },
                    BottomBarHint("Y", "Opciones", onClick = onOpenSettings),
                ),
            )
        }
    }
}

