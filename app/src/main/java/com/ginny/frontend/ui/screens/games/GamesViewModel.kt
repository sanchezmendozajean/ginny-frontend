package com.ginny.frontend.ui.screens.games

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.di.AppContainer
import com.ginny.frontend.launch.LaunchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GameSelectionOutcome {
    data class NavigateToSaveStates(val game: GameEntry) : GameSelectionOutcome
    data class Launched(val result: LaunchResult) : GameSelectionOutcome
}

class GamesViewModel(
    private val container: AppContainer,
    val systemId: SystemId,
) : ViewModel() {

    val systemDefinition = SystemCatalog.byId.getValue(systemId)
    val settingsRepository = container.settingsRepository
    val assetRepository = container.assetRepository

    val gridRows: StateFlow<Int> = container.settingsRepository.gridRows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val buttonMapping: StateFlow<ButtonMapping> = container.settingsRepository.buttonMapping
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ButtonMapping.DEFAULT)

    /** Read from the Room-backed scan cache (populated from Settings > Carpetas > "Escanear
     * biblioteca"), not a live filesystem scan — see [com.ginny.frontend.data.GameIndexRepository]. */
    private val _games = MutableStateFlow<List<GameEntry>>(emptyList())
    val games: StateFlow<List<GameEntry>> = _games

    /** Distinguishes "still loading the cache" from "loaded, cache is empty" for the empty-state
     * message — without this, the message flashes for a frame before the first read completes. */
    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning

    var focusedIndex by mutableStateOf(0)

    init {
        viewModelScope.launch {
            container.gameIndexRepository.observeGames(systemId).collect { list -> applyGames(list) }
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

    /** Guards against a second tap re-entering [rescanSystem] while one is already in flight —
     * not surfaced to the UI, since the menu item itself isn't disabled while scanning. */
    private var isRescanning = false

    /** "Reescanear este sistema" from the overflow menu — same SAF walk as the Settings full
     * library scan, but scoped to just [systemId] so it doesn't touch every other system's cache. */
    fun rescanSystem(context: Context) {
        if (isRescanning) return
        viewModelScope.launch {
            val romUri = settingsRepository.romFolderUri.first()
            if (romUri == null) {
                android.widget.Toast.makeText(
                    context,
                    "Configurá primero la Carpeta de ROMs.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            isRescanning = true
            try {
                val userUri = settingsRepository.userFolderUri.first()
                val games = container.romRepository.listGames(Uri.parse(romUri), userUri?.let(Uri::parse), systemId)
                container.gameIndexRepository.replaceForSystem(systemId, games)
                container.configRepository.requestSync()
                android.widget.Toast.makeText(
                    context,
                    "${systemDefinition.displayName}: ${games.size} juegos encontrados.",
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
     * dialog shown before this is called.
     */
    suspend fun deleteGame(context: Context, game: GameEntry) {
        val romUri = settingsRepository.romFolderUri.first()?.let(Uri::parse)
        if (romUri == null) {
            android.widget.Toast.makeText(
                context,
                "Configurá primero la Carpeta de ROMs.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        val deleted = container.romRepository.deleteGame(romUri, game)
        if (deleted) {
            settingsRepository.userFolderUri.first()?.let(Uri::parse)?.let { userUri ->
                container.assetRepository.deleteIcon(userUri, game.systemId, game.romFileName)
            }
            container.gameIndexRepository.deleteGame(game)
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
