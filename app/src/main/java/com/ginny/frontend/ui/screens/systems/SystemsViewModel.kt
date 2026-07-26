package com.ginny.frontend.ui.screens.systems

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SystemsViewModel(private val container: AppContainer) : ViewModel() {

    val gridRows: StateFlow<Int> = container.settingsRepository.gridRows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val buttonMapping: StateFlow<ButtonMapping> = container.settingsRepository.buttonMapping
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ButtonMapping.DEFAULT)

    /** Only systems whose ROM subfolder exists and has at least one valid game — per spec, a
     * system with nothing to show simply isn't listed rather than appearing empty. */
    private val _availableSystems = MutableStateFlow<List<SystemDefinition>>(emptyList())
    val availableSystems: StateFlow<List<SystemDefinition>> = _availableSystems

    /** Distinguishes "still scanning" from "scanned, found nothing" for the empty-state message. */
    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning

    var focusedIndex by mutableStateOf(0)

    init {
        viewModelScope.launch {
            combine(
                container.settingsRepository.romFolderUri,
                container.settingsRepository.userFolderUri,
            ) { rom, user -> rom to user }.collect { (romUri, userUri) ->
                scan(romUri, userUri)
            }
        }
    }

    private suspend fun scan(romUri: String?, userUri: String?) {
        _isScanning.value = true
        _availableSystems.value = if (romUri != null) {
            val romTreeUri = Uri.parse(romUri)
            val userTreeUri = userUri?.let(Uri::parse)
            SystemCatalog.allSortedByName.filter { system ->
                container.romRepository.hasAnyGames(romTreeUri, userTreeUri, system.id)
            }
        } else {
            emptyList()
        }
        _isScanning.value = false
        focusedIndex = focusedIndex.coerceIn(0, (_availableSystems.value.size - 1).coerceAtLeast(0))
    }

    /** Re-scans the ROM folder for the "Y Recargar" bottom-bar action, without waiting for the
     * folder settings themselves to change. */
    fun reload() {
        viewModelScope.launch {
            scan(
                container.settingsRepository.romFolderUri.first(),
                container.settingsRepository.userFolderUri.first(),
            )
        }
    }
}
