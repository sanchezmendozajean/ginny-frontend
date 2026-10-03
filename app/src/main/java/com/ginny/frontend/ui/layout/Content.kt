package com.ginny.frontend.ui.layout

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.ginny.frontend.ui.components.game.PageArrow
import com.ginny.frontend.ui.screens.systems.SystemTileItem
import com.ginny.frontend.ui.screens.systems.SystemsViewModel
import com.ginny.frontend.ui.util.appContainer
import com.ginny.frontend.ui.util.drawableUri

/**
 * Panel central: flecha izquierda · grilla de elementos · flecha derecha.
 * Las flechas se ven semitransparentes cuando no hay página hacia ese lado.
 */
@Composable
fun Content(modifier: Modifier = Modifier) {
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

    // Por ahora siempre la primera página; el cambio de página viene después.
    val currentPage = 0
    var pageCount by remember { mutableIntStateOf(1) }

    Container(modifier = modifier.fillMaxWidth()) {
        PageArrow(ArrowDirection.Left, enabled = currentPage > 0)

        // Espera a saber qué logos hay: si no, los elementos nacerían "sin imagen" (con el
        // título) y un instante después recibirían el logo.
        val loadedLogos = logos
        if (loadedLogos != null) {
            val starUri = drawableUri(R.drawable.ic_star)
            val entries = tiles.map { tile ->
                when (tile) {
                    SystemTileItem.Favorites -> GridEntry(
                        key = "favorites",
                        title = "Favoritos",
                        subtitle = "$favoriteCount juegos",
                        image = starUri,
                    )
                    is SystemTileItem.Real -> GridEntry(
                        key = tile.system.id.name,
                        title = tile.system.displayName,
                        subtitle = "${gameCounts[tile.system.id] ?: 0} juegos",
                        image = loadedLogos[tile.system.id],
                    )
                }
            }

            ElementGrid(
                entries = entries,
                rows = rows,
                page = currentPage,
                modifier = Modifier
                    .weight(1f)                 // ocupa todo el ancho entre las dos flechas
                    .fillMaxHeight()
                    .padding(vertical = 16.dp), // aire arriba y abajo (también para el cursor)
                onPageCountChanged = { pageCount = it },
            )
        } else {
            // Mientras tanto, el mismo espacio vacío, para que las flechas no salten de lugar.
            Spacer(Modifier.weight(1f))
        }

        PageArrow(ArrowDirection.Right, enabled = currentPage < pageCount - 1)
    }
}
