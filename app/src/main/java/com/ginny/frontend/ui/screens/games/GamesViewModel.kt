package com.ginny.frontend.ui.screens.games

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.data.model.favoriteKey
import com.ginny.frontend.di.AppContainer
import com.ginny.frontend.launch.LaunchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GameSelectionOutcome {
    data class NavigateToSaveStates(val game: GameEntry) : GameSelectionOutcome
    data class Launched(val result: LaunchResult) : GameSelectionOutcome
}

class GamesViewModel(
    private val container: AppContainer,
    val scope: GamesScope,
) : ViewModel() {

    /** Null in [GamesScope.Favorites] — games there each carry their own [GameEntry.systemId]. */
    val systemDefinition: SystemDefinition? = (scope as? GamesScope.Single)?.let { SystemCatalog.byId.getValue(it.systemId) }
    val settingsRepository = container.settingsRepository
    val assetRepository = container.assetRepository

    val gridRows: StateFlow<Int> = container.settingsRepository.gridRows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val buttonMapping: StateFlow<ButtonMapping> = container.settingsRepository.buttonMapping
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ButtonMapping.DEFAULT)

    /** Read from the Room-backed scan cache (populated from Settings > Carpetas > "Escanear
     * biblioteca"), not a live filesystem scan — see [com.ginny.frontend.data.GameIndexRepository].
     * In [GamesScope.Favorites], combines every system's cache filtered by favorite keys instead
     * of a single system's list. */
    private val _games = MutableStateFlow<List<GameEntry>>(emptyList())
    val games: StateFlow<List<GameEntry>> = _games

    val favoriteGameKeys: StateFlow<Set<String>> = container.settingsRepository.favoriteGameKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Distinguishes "still loading the cache" from "loaded, cache is empty" for the empty-state
     * message — without this, the message flashes for a frame before the first read completes. */
    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning

    var focusedIndex by mutableStateOf(0)

    init {
        viewModelScope.launch {
            when (scope) {
                is GamesScope.Single -> container.gameIndexRepository.observeGames(scope.systemId).collect(::applyGames)
                GamesScope.Favorites -> combine(
                    container.gameIndexRepository.observeAllGames(),
                    container.settingsRepository.favoriteGameKeys,
                ) { all, favorites -> all.filter { it.favoriteKey() in favorites } }.collect(::applyGames)
            }
        }
    }

    private fun applyGames(list: List<GameEntry>) {
        _games.value = list
        _isScanning.value = false
        focusedIndex = focusedIndex.coerceIn(0, (_games.value.size - 1).coerceAtLeast(0))
    }

    /** Per spec: skip straight to launching when the game has no detected save states. */
    suspend fun onGameSelected(game: GameEntry): GameSelectionOutcome {
        val savesUri = container.settingsRepository.retroArchSavesUri.first()?.let(Uri::parse)
        val saveStates = container.saveStateRepository.listSaveStates(savesUri, game)
        return if (saveStates.isNotEmpty()) {
            GameSelectionOutcome.NavigateToSaveStates(game)
        } else {
            GameSelectionOutcome.Launched(container.gameLauncher.launch(container.applicationContext, game))
        }
    }

    // Emulador, core y package desde el modal del juego. Son del sistema (afectan a todos sus
    // juegos), igual que en Configuración > Sistemas; también se sincronizan con ginny.cfg.
    fun setEmulator(systemId: SystemId, emulator: EmulatorTarget) = viewModelScope.launch {
        settingsRepository.setEmulatorFor(systemId, emulator)
        container.configRepository.requestSync()
    }

    fun setCore(systemId: SystemId, coreId: String) = viewModelScope.launch {
        settingsRepository.setRetroArchCoreFor(systemId, coreId)
        container.configRepository.requestSync()
    }

    fun setPackageOverride(systemId: SystemId, pkg: String) = viewModelScope.launch {
        settingsRepository.setPackageOverride(systemId, pkg)
        container.configRepository.requestSync()
    }

    /** "Jugar" desde el modal del juego: abre el emulador configurado para su sistema. */
    suspend fun launch(context: Context, game: GameEntry): LaunchResult =
        container.gameLauncher.launch(context, game)

    suspend fun toggleFavorite(game: GameEntry) {
        val isFavorite = game.favoriteKey() in favoriteGameKeys.value
        container.settingsRepository.setFavorite(game, !isFavorite)
    }

    /** Guards against a second tap re-entering [rescanSystem] while one is already in flight —
     * not surfaced to the UI, since the action itself isn't disabled while scanning. */
    private var isRescanning = false

    /** "Reescanear este sistema" from the game options modal — same SAF walk as the Settings full
     * library scan, but scoped to just [systemId] so it doesn't touch every other system's cache.
     * Takes an explicit [systemId] (rather than reading [scope]) since in [GamesScope.Favorites]
     * the system to rescan is whichever game's options are open, not a fixed one for the screen. */
    fun rescanSystem(context: Context, systemId: SystemId) {
        if (isRescanning) return
        val definition = SystemCatalog.byId.getValue(systemId)
        viewModelScope.launch {
            val romUri = settingsRepository.romFolderUri.first()
            val overrideUri = settingsRepository.romFolderOverrideFor(systemId).first()?.let(Uri::parse)
            if (romUri == null && overrideUri == null) {
                android.widget.Toast.makeText(
                    context,
                    "Elige primero la carpeta de ROMs.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            isRescanning = true
            try {
                val userUri = settingsRepository.userFolderUri.first()
                val games = container.romRepository.listGames(romUri?.let(Uri::parse), userUri?.let(Uri::parse), systemId, overrideUri)
                container.gameIndexRepository.replaceForSystem(systemId, games)
                container.configRepository.requestSync()
                android.widget.Toast.makeText(
                    context,
                    "${definition.displayName}: ${games.size} juegos encontrados.",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } finally {
                isRescanning = false
            }
        }
    }

    /**
     * "Eliminar juego": removes the ROM physically from the ROM folder, its icon from the Assets
     * folder (if any), and the cached Room row — irreversible, no backup, per the confirmation
     * step shown before this is called. Scope-agnostic: resolves everything from [game.systemId].
     */
    /** Lo guardado del juego que se muestra en su modal: metadatos y texturas de la caja. */
    suspend fun loadGameAssets(game: GameEntry): com.ginny.frontend.data.scrap.GameAssets =
        container.scrapStorage.readGameAssets(game.systemId, game.romFileName)

    suspend fun deleteGame(context: Context, game: GameEntry) {
        val romUri = settingsRepository.romFolderUri.first()?.let(Uri::parse)
        val overrideUri = settingsRepository.romFolderOverrideFor(game.systemId).first()?.let(Uri::parse)
        if (romUri == null && overrideUri == null) {
            android.widget.Toast.makeText(
                context,
                "Elige primero la carpeta de ROMs.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        val deleted = container.romRepository.deleteGame(romUri, game, overrideUri)
        if (deleted) {
            settingsRepository.userFolderUri.first()?.let(Uri::parse)?.let { userUri ->
                container.assetRepository.deleteIcon(userUri, game.systemId, game.romFileName)
            }
            // Lo que descargó el scrapper (wheel, cajas, video, metadatos) también se va con el juego.
            container.scrapStorage.deleteGameAssets(game.systemId, game.romFileName)
            container.gameIndexRepository.deleteGame(game)
            container.settingsRepository.setFavorite(game, false)
            container.configRepository.requestSync()
            android.widget.Toast.makeText(
                context,
                "\"${game.displayName}\" eliminado.",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } else {
            android.widget.Toast.makeText(
                context,
                "No se pudo eliminar \"${game.displayName}\".",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
}
