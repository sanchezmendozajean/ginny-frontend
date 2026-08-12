package com.ginny.frontend.ui.screens.savestates

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SaveStateEntry
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.di.AppContainer
import com.ginny.frontend.launch.LaunchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SaveStatesViewModel(
    private val container: AppContainer,
    val game: GameEntry,
) : ViewModel() {

    val systemDefinition = SystemCatalog.byId.getValue(game.systemId)
    val settingsRepository = container.settingsRepository
    val assetRepository = container.assetRepository

    val gridRows: StateFlow<Int> = container.settingsRepository.gridRows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val buttonMapping: StateFlow<ButtonMapping> = container.settingsRepository.buttonMapping
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ButtonMapping.DEFAULT)

    private val _entries = MutableStateFlow<List<SaveStateEntry>>(listOf(SaveStateEntry.NewGame))
    val entries: StateFlow<List<SaveStateEntry>> = _entries

    var focusedIndex by mutableStateOf(0)

    init {
        viewModelScope.launch {
            val savesUri = container.settingsRepository.retroArchSavesUri.first()?.let(Uri::parse)
            val slots = container.saveStateRepository.listSaveStates(savesUri, game)
            _entries.value = listOf(SaveStateEntry.NewGame) + slots
        }
    }

    suspend fun launch(entry: SaveStateEntry): LaunchResult {
        val slot = (entry as? SaveStateEntry.Slot)?.slotIndex
        return container.gameLauncher.launch(container.applicationContext, game, saveStateSlot = slot)
    }
}
