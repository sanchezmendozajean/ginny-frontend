package com.ginny.frontend.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.components.GeometricBackground
import com.ginny.frontend.ui.components.game.GridEntry
import com.ginny.frontend.ui.layout.GinnyFooter
import com.ginny.frontend.ui.layout.GinnyHeader
import com.ginny.frontend.ui.modifiers.LocalHazeState
import com.ginny.frontend.ui.navigation.Screen
import com.ginny.frontend.ui.screens.games.GamesScreen
import com.ginny.frontend.ui.screens.games.GameModal
import com.ginny.frontend.ui.screens.games.GamesViewModel
import com.ginny.frontend.ui.util.appContainer
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.model.GameEntry
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import com.ginny.frontend.ui.screens.settings.SettingsModal
import com.ginny.frontend.ui.screens.systems.SystemsScreen
import com.ginny.frontend.ui.theme.GinnyThemes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/** Cuánto se desenfoca la interfaz de atrás cuando Configuración está abierta. */
val SettingsBackdropBlur = 16.dp

/**
 * Raíz de la interfaz, reconstruida paso a paso (ver docs/plan-reconstruccion-ui.md).
 * La implementación anterior completa está archivada en docs/ui-legacy/ como referencia.
 *
 * [settingsOpen]: si el modal de Configuración está abierto (lo controla MainActivity).
 * [onCloseSettings]: cerrarlo desde la interfaz (ej. tocando "Cerrar").
 */
@Composable
fun GinnyApp(settingsOpen: Boolean = false, onCloseSettings: () -> Unit = {}) {
    // El tema lo provee GinnyTheme (en MainActivity), leído de Configuración.
    val palette = GinnyThemes.current
    // Lo que el contenido informa y el footer muestra: elemento seleccionado y páginas.
    var selected by remember { mutableStateOf<GridEntry?>(null) }
    var pageCount by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(0) }

    // Pantalla actual. B (vía MainActivity → onBackPressedDispatcher), el gesto atrás del sistema
    // o tocar "Regresar" vuelven a Sistemas. En Sistemas no hay a dónde volver: BackHandler queda
    // deshabilitado y MainActivity no deja que B cierre la app.
    var screen by remember { mutableStateOf<Screen>(Screen.Systems) }
    val goBack = { screen = Screen.Systems }
    BackHandler(enabled = screen != Screen.Systems && !settingsOpen, onBack = goBack)

    // Juego abierto (su modal), o null. Se declara después del BackHandler de pantalla: con los dos
    // habilitados, B cierra primero el modal.
    var openGame by remember { mutableStateOf<GameEntry?>(null) }
    BackHandler(enabled = openGame != null && !settingsOpen) { openGame = null }
    // La grilla responde solo si no hay nada encima.
    val gridActive = !settingsOpen && openGame == null

    // Une el fondo (que se desenfoca) con los Container (que lo desenfocan detrás de sí).
    val hazeState = remember { HazeState() }

    // Desenfoque animado de la interfaz de atrás mientras Configuración está abierta.
    // Modifier.blur funciona en API 31+; en 29-30 no hace nada y queda solo la cortina oscura.
    val backdropBlur by animateDpAsState(
        targetValue = if (settingsOpen || openGame != null) SettingsBackdropBlur else 0.dp,
        label = "settingsBackdropBlur",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ── La interfaz de siempre (se desenfoca cuando el modal está abierto) ──────────────
        Box(modifier = Modifier.fillMaxSize().blur(backdropBlur)) {
            // Capa 1: fondo geométrico animado, marcado como "desenfocable".
            GeometricBackground(
                modifier = Modifier
                    .fillMaxSize()
                    .haze(state = hazeState, backgroundColor = palette.background),
            )

            // Capa 2: la interfaz. Todo lo de adentro puede desenfocar el fondo (ver Container).
            CompositionLocalProvider(LocalHazeState provides hazeState) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),                                // margen con los bordes de la pantalla
                    verticalArrangement = Arrangement.spacedBy(12.dp),  // separación entre las tres filas
                ) {
                    GinnyHeader(title = (screen as? Screen.Games)?.title)

                    val contentModifier = Modifier.weight(1f)           // ocupa todo el alto sobrante
                    val onSelectionChanged: (GridEntry?) -> Unit = { selected = it }
                    val onPagingChanged: (Int, Int) -> Unit = { count, page ->
                        pageCount = count
                        currentPage = page
                    }
                    when (val current = screen) {
                        Screen.Systems -> SystemsScreen(
                            onOpenSystem = { scope, title -> screen = Screen.Games(scope, title) },
                            modifier = contentModifier,
                            active = gridActive,                        // con un modal abierto, la grilla no responde
                            onSelectionChanged = onSelectionChanged,
                            onPagingChanged = onPagingChanged,
                        )
                        is Screen.Games -> // key: cada sistema es una pantalla nueva (su grilla, su foco)
                            key(current.scope) {
                                GamesScreen(
                                    scope = current.scope,
                                    onOpenGame = { openGame = it },
                                    modifier = contentModifier,
                                    active = gridActive,
                                    onSelectionChanged = onSelectionChanged,
                                    onPagingChanged = onPagingChanged,
                                )
                            }
                    }
                    GinnyFooter(
                        title = selected?.title.orEmpty(),
                        subtitle = selected?.subtitle.orEmpty(),
                        pageCount = pageCount,
                        currentPage = currentPage,
                        onBack = if (screen == Screen.Systems) null else goBack,
                    )
                }
            }
        }

        // ── Modal del juego abierto ────────────────────────────────────────────────────────────
        // Usa el mismo GamesViewModel que la grilla (misma key → misma instancia).
        val gamesScope = (screen as? Screen.Games)?.scope
        val gamesViewModel: GamesViewModel? = gamesScope?.let { s ->
            val container = appContainer()
            viewModel(key = s.toString(), factory = viewModelFactory { initializer { GamesViewModel(container, s) } })
        }
        val buttonMapping by (gamesViewModel?.buttonMapping ?: remember { MutableStateFlow(ButtonMapping.DEFAULT) }).collectAsState()
        GameModal(
            game = openGame,
            viewModel = gamesViewModel,
            buttonMapping = buttonMapping,
            onClose = { openGame = null },
        )

        // ── Modal de Configuración, encima de todo ───────────────────────────────────────────
        SettingsModal(visible = settingsOpen, onClose = onCloseSettings)
    }
}
