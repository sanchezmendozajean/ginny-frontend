package com.ginny.frontend.ui.screens.games

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ginny.frontend.data.model.GameEntry
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
    onOpenGame: (GameEntry) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onSelectionChanged: (GridEntry?) -> Unit = {},
    onPagingChanged: (pageCount: Int, currentPage: Int) -> Unit = { _, _ -> },
    /** Cuántos juegos hay (null mientras se lee el escaneo): la barra superior lo muestra junto al nombre del sistema. */
    onCountChanged: (Int?) -> Unit = {},
    /** Lo que la pantalla secundaria muestra del juego enfocado: su wheel o, si no tiene, su grid (null = nada). */
    onFocusedArtworkChanged: (Uri?) -> Unit = {},
    /** Sube cada vez que se toca "Abrir" en el pie: abre el elemento seleccionado, como A. */
    activateTick: Int = 0,
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

    val currentOnCountChanged by rememberUpdatedState(onCountChanged)
    LaunchedEffect(games.size, loading) { currentOnCountChanged(if (loading) null else games.size) }
    DisposableEffect(Unit) { onDispose { currentOnCountChanged(null) } }

    val entries = games.map { game ->
        GridEntry(
            key = game.romUri.toString(),
            title = game.displayName,
            image = game.iconUri,
        )
    }

    // Subtítulo del pie: "Estudio (Año)" del juego seleccionado (si está scrapeado) o, si no, el nombre del archivo.
    // Se lee solo el del seleccionado (los metadatos están en un JSON por juego) y se recuerda; mientras se lee
    // queda en blanco, para no mostrar un instante el nombre del archivo y luego cambiarlo.
    var picked by remember { mutableStateOf<GridEntry?>(null) }
    val subtitles = remember(games) { mutableStateMapOf<String, String>() }
    LaunchedEffect(picked?.key, games) {
        val key = picked?.key ?: return@LaunchedEffect
        if (key in subtitles) return@LaunchedEffect
        val game = games.firstOrNull { it.romUri.toString() == key } ?: return@LaunchedEffect
        val metadata = viewModel.loadGameAssets(game).metadata
        subtitles[key] = studioAndYear(metadata?.developer, metadata?.releaseYear) ?: game.romFileName
    }
    // Imagen para la pantalla secundaria: el wheel del juego enfocado o, si no tiene, su grid (el ícono).
    val currentOnArtwork by rememberUpdatedState(onFocusedArtworkChanged)
    LaunchedEffect(picked?.key, games) {
        val key = picked?.key
        val game = games.firstOrNull { it.romUri.toString() == key }
        if (game == null) {
            currentOnArtwork(null)
            return@LaunchedEffect
        }
        currentOnArtwork(viewModel.loadGameAssets(game).wheel ?: game.iconUri)
    }
    DisposableEffect(Unit) { onDispose { currentOnArtwork(null) } }

    val currentOnSelectionChanged by rememberUpdatedState(onSelectionChanged)
    LaunchedEffect(picked, subtitles.toMap()) {
        val entry = picked
        currentOnSelectionChanged(entry?.copy(subtitle = subtitles[entry.key].orEmpty()))
    }

    Content(
        entries = entries,
        rows = rows,
        selectedIndex = viewModel.focusedIndex,
        onSelectedIndexChange = { viewModel.focusedIndex = it },
        buttonMapping = buttonMapping,
        // Abrir un juego: muestra su modal (Jugar, emulador, favorito…).
        onActivate = { entry -> games.firstOrNull { it.romUri.toString() == entry.key }?.let(onOpenGame) },
        loading = loading,
        active = active,
        modifier = modifier,
        onSelectionChanged = { picked = it },
        onPagingChanged = onPagingChanged,
        activateTick = activateTick,
    )
}

/** "Estudio (Año)"; solo el estudio si no hay año; null si no hay estudio (el pie muestra entonces el nombre del archivo). */
internal fun studioAndYear(developer: String?, year: Int?): String? {
    val studio = developer?.takeIf { it.isNotBlank() } ?: return null
    return if (year != null) "$studio ($year)" else studio
}
