package com.ginny.frontend.ui.screens.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.ui.components.game.GridEntry
import com.ginny.frontend.ui.layout.Content
import com.ginny.frontend.ui.util.appContainer

/**
 * Pantalla de Juegos: los juegos de un sistema (o los Favoritos de todos), en orden alfabético,
 * leídos del último escaneo de la biblioteca. Cada juego muestra su ícono
 * (`Assets/<sistema>/icons/<rom>.png`) o, si no tiene, su nombre.
 *
 * Un ViewModel por [scope] (key): cada sistema recuerda su propia selección mientras la app esté
 * abierta.
 */
@Composable
fun GamesScreen(
    scope: GamesScope,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onSelectionChanged: (GridEntry?) -> Unit = {},
    onPagingChanged: (pageCount: Int, currentPage: Int) -> Unit = { _, _ -> },
) {
    val container = appContainer()
    val viewModel: GamesViewModel = viewModel(
        key = scope.toString(),
        factory = viewModelFactory { initializer { GamesViewModel(container, scope) } },
    )
    val games by viewModel.games.collectAsState()
    val loading by viewModel.isScanning.collectAsState()    // true hasta leer el escaneo guardado
    val rows by viewModel.gridRows.collectAsState()
    val buttonMapping by viewModel.buttonMapping.collectAsState()

    val entries = games.map { game ->
        GridEntry(
            key = game.romUri.toString(),
            title = game.displayName,
            // En Favoritos se mezclan sistemas: el subtítulo dice de cuál es cada juego.
            subtitle = if (scope == GamesScope.Favorites) SystemCatalog.byId.getValue(game.systemId).displayName else "",
            image = game.iconUri,
        )
    }

    Content(
        entries = entries,
        rows = rows,
        selectedIndex = viewModel.focusedIndex,
        onSelectedIndexChange = { viewModel.focusedIndex = it },
        buttonMapping = buttonMapping,
        // Abrir un juego (lanzarlo o ir a sus estados guardados): siguiente paso.
        onActivate = { },
        loading = loading,
        active = active,
        modifier = modifier,
        onSelectionChanged = onSelectionChanged,
        onPagingChanged = onPagingChanged,
    )
}
