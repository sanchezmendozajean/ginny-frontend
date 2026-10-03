package com.ginny.frontend.ui.layout

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ginny.frontend.R
import com.ginny.frontend.ui.components.Container
import com.ginny.frontend.ui.components.game.ArrowDirection
import com.ginny.frontend.ui.components.game.ElementGrid
import com.ginny.frontend.ui.components.game.GridEntry
import com.ginny.frontend.ui.components.game.GridLayout
import com.ginny.frontend.ui.components.game.GridNavigation
import com.ginny.frontend.ui.components.game.PageArrow
import com.ginny.frontend.ui.screens.systems.SystemTileItem
import com.ginny.frontend.ui.screens.systems.SystemsViewModel
import com.ginny.frontend.ui.util.LocalSoundManager
import com.ginny.frontend.ui.util.appContainer

/**
 * Panel central: flecha izquierda · grilla de elementos · flecha derecha.
 * Las flechas se ven semitransparentes cuando no hay página hacia ese lado; tocarlas cambia de
 * página.
 *
 * [onSelectionChanged] y [onPagingChanged] informan hacia afuera el elemento seleccionado y las
 * páginas (total, actual), para que el footer los muestre.
 */
@Composable
fun Content(
    modifier: Modifier = Modifier,
    onSelectionChanged: (GridEntry?) -> Unit = {},
    onPagingChanged: (pageCount: Int, currentPage: Int) -> Unit = { _, _ -> },
) {
    // Provisional: por ahora muestra los sistemas. Cuando exista la pantalla de Sistemas, el
    // ViewModel se creará allí y Content recibirá ya la lista de elementos.
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
    val sound = LocalSoundManager.current

    // Forma de la grilla (la informa ElementGrid al medirse); null hasta entonces.
    var layout by remember { mutableStateOf<GridLayout?>(null) }

    val entries = tiles.map { tile ->
        when (tile) {
            SystemTileItem.Favorites -> GridEntry(
                key = "favorites",
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

    // La selección vive en el ViewModel (regla del spec: sobrevive a recomposiciones).
    val selectedIndex = viewModel.focusedIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    val currentLayout = layout
    val pageCount = currentLayout?.pageCount(entries.size) ?: 1
    val currentPage = currentLayout?.pageOf(selectedIndex) ?: 0

    /** Cambia la selección y suena el efecto de foco (regla del spec). */
    fun select(index: Int) {
        if (index == viewModel.focusedIndex) return
        viewModel.focusedIndex = index
        sound?.playFocusSound()
    }

    // Avisa hacia afuera (fuera de la composición) cuando cambian la selección o las páginas.
    val selectedEntry = entries.getOrNull(selectedIndex)
    LaunchedEffect(selectedEntry) { onSelectionChanged(selectedEntry) }
    LaunchedEffect(pageCount, currentPage) { onPagingChanged(pageCount, currentPage) }

    Container(modifier = modifier.fillMaxWidth()) {
        PageArrow(
            ArrowDirection.Left,
            enabled = currentPage > 0,
            onClick = { currentLayout?.let { select(GridNavigation.previousPage(selectedIndex, it, entries.size)) } },
        )

        // Espera a saber qué logos hay: si no, los elementos nacerían "sin imagen" (con el
        // título) y un instante después recibirían el logo.
        if (logos != null) {
            ElementGrid(
                entries = entries,
                rows = rows,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = ::select,
                buttonMapping = buttonMapping,
                modifier = Modifier
                    .weight(1f)                 // ocupa todo el ancho entre las dos flechas
                    .fillMaxHeight()
                    .padding(vertical = 16.dp), // aire arriba y abajo (también para el cursor)
                onLayoutChanged = { layout = it },
            )
        } else {
            // Mientras tanto, el mismo espacio vacío, para que las flechas no salten de lugar.
            Spacer(Modifier.weight(1f))
        }

        PageArrow(
            ArrowDirection.Right,
            enabled = currentPage < pageCount - 1,
            onClick = { currentLayout?.let { select(GridNavigation.nextPage(selectedIndex, it, entries.size)) } },
        )
    }
}

/** "1 juego" / "N juegos". */
private fun gamesLabel(count: Int): String = if (count == 1) "1 juego" else "$count juegos"
