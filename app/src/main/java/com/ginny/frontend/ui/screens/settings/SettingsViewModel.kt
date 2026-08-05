package com.ginny.frontend.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.MusicSelection
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One line of the scan-progress modal: a system that finished scanning and how many games it had. */
data class ScanProgressEntry(val systemDisplayName: String, val gameCount: Int)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settingsRepository
    val musicRepository = container.musicRepository
    val assetRepository = container.assetRepository
    val systems = SystemCatalog.allSortedByName

    /** Non-null logical button currently waiting for the next physical key press to bind to it. */
    var listeningForButton by mutableStateOf<LogicalButton?>(null)

    private val _isScanningLibrary = MutableStateFlow(false)
    val isScanningLibrary: StateFlow<Boolean> = _isScanningLibrary

    private val _scanProgress = MutableStateFlow<List<ScanProgressEntry>>(emptyList())
    /** Grows one entry at a time as each system finishes scanning — drives the progress modal. */
    val scanProgress: StateFlow<List<ScanProgressEntry>> = _scanProgress

    /** Walks the whole ROM folder (every system) and replaces the cached library index that
     * Systems/Games screens read from — the only place this expensive filesystem scan should run. */
    fun scanLibrary(context: Context) = viewModelScope.launch {
        val romUri = settings.romFolderUri.first()
        if (romUri == null) {
            android.widget.Toast.makeText(
                context,
                "Configurá primero la Carpeta de ROMs.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return@launch
        }
        _isScanningLibrary.value = true
        _scanProgress.value = emptyList()
        try {
            val userUri = settings.userFolderUri.first()
            val games = container.romRepository.scanAllSystems(Uri.parse(romUri), userUri?.let(Uri::parse)) { definition, systemGames ->
                if (systemGames.isNotEmpty()) {
                    _scanProgress.value = _scanProgress.value + ScanProgressEntry(definition.displayName, systemGames.size)
                }
            }
            container.gameIndexRepository.replaceAll(games)
            settings.setLastScanAt(System.currentTimeMillis())
            android.widget.Toast.makeText(
                context,
                "Biblioteca escaneada: ${games.size} juegos encontrados.",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } finally {
            _isScanningLibrary.value = false
        }
    }

    fun setGridRows(rows: Int) = viewModelScope.launch { settings.setGridRows(rows) }

    fun onUserFolderPicked(context: Context, uri: Uri) = viewModelScope.launch {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        settings.setUserFolderUri(uri.toString())
        val result = container.storageBootstrapper.ensureUserFolderStructure(uri)
        if (result.isFailure) {
            android.widget.Toast.makeText(
                context,
                "No se pudo preparar la carpeta Assets: ${result.exceptionOrNull()?.message}",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun onRomFolderPicked(context: Context, uri: Uri) = viewModelScope.launch {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        settings.setRomFolderUri(uri.toString())
    }

    fun onSavesFolderPicked(context: Context, uri: Uri) = viewModelScope.launch {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        settings.setRetroArchSavesUri(uri.toString())
    }

    fun setPackageOverride(systemId: SystemId, pkg: String) =
        viewModelScope.launch { settings.setPackageOverride(systemId, pkg) }

    fun setMusicVolume(v: Float) = viewModelScope.launch { settings.setMusicVolume(v) }
    fun setUiSoundVolume(v: Float) = viewModelScope.launch { settings.setUiSoundVolume(v) }

    fun setBackgroundMusicTrack(selection: MusicSelection) =
        viewModelScope.launch { settings.setBackgroundMusicTrack(MusicSelection.encode(selection)) }

    fun setEmulator(systemId: SystemId, emulator: EmulatorTarget) =
        viewModelScope.launch { settings.setEmulatorFor(systemId, emulator) }

    fun setCore(systemId: SystemId, coreId: String) =
        viewModelScope.launch { settings.setRetroArchCoreFor(systemId, coreId) }

    fun startListeningForButton(button: LogicalButton) {
        listeningForButton = button
    }

    fun onKeyCaptured(keyCode: Int) {
        val button = listeningForButton ?: return
        listeningForButton = null
        viewModelScope.launch { settings.setButtonKeyCode(button, keyCode) }
    }

    fun cancelListening() {
        listeningForButton = null
    }

    fun resetButtonMapping() = viewModelScope.launch { settings.resetButtonMapping() }
}
