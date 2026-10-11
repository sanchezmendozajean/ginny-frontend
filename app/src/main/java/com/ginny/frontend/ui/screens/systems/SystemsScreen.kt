package com.ginny.frontend.ui.screens.systems

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ginny.frontend.R
import com.ginny.frontend.ui.components.game.GridEntry
import com.ginny.frontend.ui.layout.Content
import com.ginny.frontend.ui.layout.gamesLabel
import com.ginny.frontend.ui.screens.games.GamesScope
import com.ginny.frontend.ui.util.appContainer

/**
 * Pantalla de Sistemas: Favoritos (si hay alguno) y los sistemas con juegos, en orden alfabético.
 * Abrir uno llama a [onOpenSystem] con lo que la pantalla de Juegos debe listar y su nombre.
 *
 * El ViewModel vive mientras la Activity: al volver desde Juegos, la selección sigue donde estaba.
 */
@Composable
fun SystemsScreen(
    onOpenSystem: (scope: GamesScope, title: String) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onSelectionChanged: (GridEntry?) -> Unit = {},
    onPagingChanged: (pageCount: Int, currentPage: Int) -> Unit = { _, _ -> },
    /** Sube cada vez que se toca "Abrir" en el pie: abre el elemento seleccionado, como A. */
    activateTick: Int = 0,
) {
    val container = appContainer()
    val viewModel: SystemsViewModel = viewModel(
        factory = viewModelFactory { initializer { SystemsViewModel(container) } },
    )
    val tiles by viewModel.tiles.collectAsState()           // sistemas con juegos, en orden alfabético
    val gameCounts by viewModel.gameCounts.collectAsState()
    val favoriteCount by viewModel.favoriteCount.collectAsState()
    val logos by viewModel.systemLogos.collectAsState()     // null mientras busca
    val rows by viewModel.gridRows.collectAsState()         // filas visibles, de Configuración (1–4)
    val buttonMapping by viewModel.buttonMapping.collectAsState()

    val entries = tiles.map { tile ->
        when (tile) {
            SystemTileItem.Favorites -> GridEntry(
                key = FavoritesKey,
                title = "Favoritos",
                subtitle = gamesLabel(favoriteCount),
                icon = R.drawable.ic_star,
            )
            is SystemTileItem.Real -> GridEntry(
                key = tile.system.id.name,
                title = tile.system.displayName,
                subtitle = gamesLabel(gameCounts[tile.system.id] ?: 0),
                image = logos?.get(tile.system.id),
            )
        }
    }

    Content(
        entries = entries,
        rows = rows,
        selectedIndex = viewModel.focusedIndex,
        onSelectedIndexChange = { viewModel.focusedIndex = it },
        buttonMapping = buttonMapping,
        activateSound = { it.playEnterSystemSound() },
        onActivate = { entry ->
            val tile = tiles.firstOrNull { it.key() == entry.key } ?: return@Content
            when (tile) {
                SystemTileItem.Favorites -> onOpenSystem(GamesScope.Favorites, entry.title)
                is SystemTileItem.Real -> onOpenSystem(GamesScope.Single(tile.system.id), entry.title)
            }
        },
        // Espera a saber qué logos hay: si no, los elementos nacerían "sin imagen" (con el
        // título) y un instante después recibirían el logo.
        loading = logos == null,
        active = active,
        modifier = modifier,
        onSelectionChanged = onSelectionChanged,
        onPagingChanged = onPagingChanged,
        activateTick = activateTick,
    )
}

private const val FavoritesKey = "favorites"

private fun SystemTileItem.key(): String = when (this) {
    SystemTileItem.Favorites -> FavoritesKey
    is SystemTileItem.Real -> system.id.name
}
