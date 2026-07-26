package com.ginny.frontend.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    const val SAVES = "saves"
    const val SETTINGS = "settings"

    fun games(systemId: SystemId) = "games/${systemId.name}"
}

@Composable
fun GinnyNavHost(
    container: AppContainer,
    soundManager: SoundManager,
    navController: NavHostController = rememberNavController(),
    onNavControllerReady: (NavHostController) -> Unit = {},
) {
    val selectedGameForSaves = remember { mutableStateOf<GameEntry?>(null) }
    val selectedSystemDisplayName = remember { mutableStateOf("") }

    SideEffect { onNavControllerReady(navController) }

    NavHost(navController = navController, startDestination = Routes.SYSTEMS) {
        composable(Routes.SYSTEMS) {
            val viewModel: SystemsViewModel = viewModel(
                factory = viewModelFactory { initializer { SystemsViewModel(container) } },
            )
            SystemsScreen(
                container = container,
                soundManager = soundManager,
                onOpenSystem = { system -> navController.navigate(Routes.games(system.id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                viewModel = viewModel,
            )
        }

        composable(
            route = Routes.GAMES,
            arguments = listOf(navArgument("systemId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val systemId = SystemId.valueOf(backStackEntry.arguments!!.getString("systemId")!!)
            val viewModel: GamesViewModel = viewModel(
                key = systemId.name,
                factory = viewModelFactory { initializer { GamesViewModel(container, systemId) } },
            )
            GamesScreen(
                soundManager = soundManager,
                onBack = { navController.popBackStack() },
                onOpenSaveStates = { game ->
                    selectedGameForSaves.value = game
                    selectedSystemDisplayName.value = SystemCatalog.byId.getValue(systemId).displayName
                    navController.navigate(Routes.SAVES)
                },
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
                    viewModel = viewModel,
                )
            }
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory { initializer { SettingsViewModel(container) } },
            )
            SettingsScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
        }
    }
}
