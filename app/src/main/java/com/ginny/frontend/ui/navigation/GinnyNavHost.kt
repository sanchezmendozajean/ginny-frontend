package com.ginny.frontend.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.di.AppContainer
import com.ginny.frontend.ui.screens.games.GamesScope
import com.ginny.frontend.ui.screens.games.GamesScreen
import com.ginny.frontend.ui.screens.games.GamesViewModel
import com.ginny.frontend.ui.screens.savestates.SaveStatesScreen
import com.ginny.frontend.ui.screens.savestates.SaveStatesViewModel
import com.ginny.frontend.ui.screens.settings.SettingsScreen
import com.ginny.frontend.ui.screens.settings.SettingsViewModel
import com.ginny.frontend.ui.screens.systems.SystemsScreen
import com.ginny.frontend.ui.screens.systems.SystemsViewModel

object Routes {
    const val SYSTEMS = "systems"
    const val GAMES = "games/{systemId}"
    const val GAMES_FAVORITES = "games/favorites"
    const val SAVES = "saves"

    fun games(systemId: SystemId) = "games/${systemId.name}"
}

@Composable
fun GinnyNavHost(
    container: AppContainer,
    soundManager: SoundManager,
    navController: NavHostController = rememberNavController(),
    onNavControllerReady: (NavHostController) -> Unit = {},
    onSettingsStateReady: (MutableState<Boolean>) -> Unit = {},
) {
    val selectedGameForSaves = remember { mutableStateOf<GameEntry?>(null) }
    val selectedSystemDisplayName = remember { mutableStateOf("") }
    // Hoisted here (rather than local to SettingsScreen) so MainActivity's hardware Back/Menu key
    // handling can open/close Settings regardless of which screen is currently active — same
    // pattern as the old menuExpanded it replaces (see docs/architecture.md).
    val settingsOpen = remember { mutableStateOf(false) }

    SideEffect { onNavControllerReady(navController) }
    SideEffect { onSettingsStateReady(settingsOpen) }

    // Settings renders as a plain overlay in this same composition (not a system Dialog/Window),
    // specifically so it can blur the still-composed screen underneath — see docs/diseño's
    // frosted-backdrop look. Modifier.blur() is a no-op below API 31, so older devices just get
    // the dim scrim without the blur; that's an acceptable, harmless degradation.
    val blurRadius by animateDpAsState(if (settingsOpen.value) 16.dp else 0.dp, label = "settingsBackdropBlur")

    Box {
        NavHost(
            navController = navController,
            startDestination = Routes.SYSTEMS,
            modifier = Modifier.fillMaxSize().blur(blurRadius),
        ) {
            composable(Routes.SYSTEMS) {
                val viewModel: SystemsViewModel = viewModel(
                    factory = viewModelFactory { initializer { SystemsViewModel(container) } },
                )
                SystemsScreen(
                    container = container,
                    soundManager = soundManager,
                    onOpenSystem = { system -> navController.navigate(Routes.games(system.id)) },
                    onOpenFavorites = { navController.navigate(Routes.GAMES_FAVORITES) },
                    onOpenSettings = { settingsOpen.value = true },
                    viewModel = viewModel,
                )
            }

            composable(
                route = Routes.GAMES,
                arguments = listOf(navArgument("systemId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val systemId = SystemId.valueOf(backStackEntry.arguments!!.getString("systemId")!!)
                val scope = GamesScope.Single(systemId)
                val viewModel: GamesViewModel = viewModel(
                    key = systemId.name,
                    factory = viewModelFactory { initializer { GamesViewModel(container, scope) } },
                )
                GamesScreen(
                    soundManager = soundManager,
                    onBack = { navController.popBackStack() },
                    onOpenSaveStates = { game ->
                        selectedGameForSaves.value = game
                        selectedSystemDisplayName.value = SystemCatalog.byId.getValue(systemId).displayName
                        navController.navigate(Routes.SAVES)
                    },
                    onOpenSettings = { settingsOpen.value = true },
                    viewModel = viewModel,
                )
            }

            composable(Routes.GAMES_FAVORITES) {
                val viewModel: GamesViewModel = viewModel(
                    key = "favorites",
                    factory = viewModelFactory { initializer { GamesViewModel(container, GamesScope.Favorites) } },
                )
                GamesScreen(
                    soundManager = soundManager,
                    onBack = { navController.popBackStack() },
                    onOpenSaveStates = { game ->
                        selectedGameForSaves.value = game
                        selectedSystemDisplayName.value = SystemCatalog.byId.getValue(game.systemId).displayName
                        navController.navigate(Routes.SAVES)
                    },
                    onOpenSettings = { settingsOpen.value = true },
                    viewModel = viewModel,
                )
            }

            composable(Routes.SAVES) {
                val game = selectedGameForSaves.value
                if (game == null) {
                    // Reached via back/forward or process death with no selection in memory — bail to systems.
                    navController.popBackStack(Routes.SYSTEMS, inclusive = false)
                } else {
                    val viewModel: SaveStatesViewModel = viewModel(
                        key = game.romUri.toString(),
                        factory = viewModelFactory { initializer { SaveStatesViewModel(container, game) } },
                    )
                    SaveStatesScreen(
                        soundManager = soundManager,
                        systemDisplayName = selectedSystemDisplayName.value,
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { settingsOpen.value = true },
                        viewModel = viewModel,
                    )
                }
            }
        }

        if (settingsOpen.value) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory { initializer { SettingsViewModel(container) } },
            )
            SettingsScreen(onClose = { settingsOpen.value = false }, viewModel = viewModel)
        }
    }
}
