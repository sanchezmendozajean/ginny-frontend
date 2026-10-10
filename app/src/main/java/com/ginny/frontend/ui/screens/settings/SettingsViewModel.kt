package com.ginny.frontend.ui.screens.settings

import com.ginny.frontend.data.model.ButtonLayout
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
import com.ginny.frontend.data.scrap.ScrapMedia
import com.ginny.frontend.data.scrap.ScrapProgress
import com.ginny.frontend.data.scrap.ScrapProvider
import com.ginny.frontend.data.scrap.ScrapResult
import com.ginny.frontend.data.scrap.ScrapperDevCredentials
import com.ginny.frontend.data.model.MusicSelection
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.di.AppContainer
import com.ginny.frontend.ui.theme.ThemeMode
import kotlinx.coroutines.Job
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
    val scrapper = container.scrapperSettingsRepository
    val systems = SystemCatalog.allSortedByName

    /** Non-null logical button currently waiting for the next physical key press to bind to it. */
    var listeningForButton by mutableStateOf<LogicalButton?>(null)

    private val _isScanningLibrary = MutableStateFlow(false)
    val isScanningLibrary: StateFlow<Boolean> = _isScanningLibrary

    private val _scanProgress = MutableStateFlow<List<ScanProgressEntry>>(emptyList())
    /** Grows one entry at a time as each system finishes scanning — drives the progress modal. */
    val scanProgress: StateFlow<List<ScanProgressEntry>> = _scanProgress

    private var scanJob: Job? = null

    /** Walks the whole ROM folder (every system) and replaces the cached library index that
     * Systems/Games screens read from — the only place this expensive filesystem scan should run. */
    fun scanLibrary(context: Context) {
        scanJob = viewModelScope.launch {
            val romUri = settings.romFolderUri.first()
            val overrides = settings.romFolderOverrides.first().mapValues { Uri.parse(it.value) }
            // Basta con la carpeta de ROMs o con algún sistema que tenga su propia carpeta.
            if (romUri == null && overrides.isEmpty()) {
                android.widget.Toast.makeText(
                    context,
                    "Elige primero la carpeta de ROMs.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            _isScanningLibrary.value = true
            _scanProgress.value = emptyList()
            try {
                val userUri = settings.userFolderUri.first()
                val games = container.romRepository.scanAllSystems(romUri?.let(Uri::parse), userUri?.let(Uri::parse), overrides) { definition, systemGames ->
                    if (systemGames.isNotEmpty()) {
                        _scanProgress.value = _scanProgress.value + ScanProgressEntry(definition.displayName, systemGames.size)
                    }
                }
                container.gameIndexRepository.replaceAll(games)
                settings.setLastScanAt(System.currentTimeMillis())
                container.configRepository.requestSync()
                android.widget.Toast.makeText(
                    context,
                    "Biblioteca escaneada: ${games.size} juegos encontrados.",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } finally {
                _isScanningLibrary.value = false
            }
        }
    }

    /** Cancels an in-progress [scanLibrary] run — cooperative cancellation means the scan abandons
     * before its final game-index write, so nothing partial is persisted. */
    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        _isScanningLibrary.value = false
    }

    fun setGridRows(rows: Int) = viewModelScope.launch {
        settings.setGridRows(rows)
        container.configRepository.requestSync()
    }

    fun setButtonLayout(layout: ButtonLayout) = viewModelScope.launch {
        settings.setButtonLayout(layout)
        container.configRepository.requestSync()
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settings.setThemeMode(mode)
        container.configRepository.requestSync()
    }

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
        // Lee ginny.cfg si ya existe en la carpeta (y aplica su configuración), o lo crea con los
        // valores actuales si es la primera vez que se usa esta carpeta.
        val configResult = container.configRepository.ensureConfigFile(uri)
        if (configResult.isFailure) {
            android.widget.Toast.makeText(
                context,
                "No se pudo leer/crear ginny.cfg: ${configResult.exceptionOrNull()?.message}",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun onRomFolderPicked(context: Context, uri: Uri) = viewModelScope.launch {
        // Write access is needed here too, not just read — "Eliminar juego" deletes the ROM file
        // physically from this tree (see RomRepository.deleteGame), which fails silently without it.
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        settings.setRomFolderUri(uri.toString())
        container.configRepository.requestSync()
    }

    /** Carpeta de ROMs propia de un sistema (Configuración > Sistemas). Después reescanea ese sistema. */
    fun onSystemRomFolderPicked(context: Context, systemId: SystemId, uri: Uri) = viewModelScope.launch {
        // Escritura también, como en la carpeta de ROMs general: "Eliminar juego" borra el archivo.
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        settings.setRomFolderOverride(systemId, uri.toString())
        container.configRepository.requestSync()
        rescanSystem(context, systemId)
    }

    /** Vuelve a la carpeta de siempre (`<ROMS>/<sistema>`) y reescanea ese sistema. */
    fun resetSystemRomFolder(context: Context, systemId: SystemId) = viewModelScope.launch {
        settings.setRomFolderOverride(systemId, null)
        container.configRepository.requestSync()
        rescanSystem(context, systemId)
    }

    /** Actualiza la biblioteca guardada de un solo sistema (su carpeta cambió). */
    private suspend fun rescanSystem(context: Context, systemId: SystemId) {
        val romUri = settings.romFolderUri.first()?.let(Uri::parse)
        val overrideUri = settings.romFolderOverrideFor(systemId).first()?.let(Uri::parse)
        val userUri = settings.userFolderUri.first()?.let(Uri::parse)
        val games = container.romRepository.listGames(romUri, userUri, systemId, overrideUri)
        container.gameIndexRepository.replaceForSystem(systemId, games)
        android.widget.Toast.makeText(
            context,
            "${SystemCatalog.byId.getValue(systemId).displayName}: ${games.size} ${if (games.size == 1) "juego" else "juegos"}",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    fun onSavesFolderPicked(context: Context, uri: Uri) = viewModelScope.launch {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        settings.setRetroArchSavesUri(uri.toString())
        container.configRepository.requestSync()
    }

    fun setPackageOverride(systemId: SystemId, pkg: String) = viewModelScope.launch {
        settings.setPackageOverride(systemId, pkg)
        container.configRepository.requestSync()
    }

    fun setMusicVolume(v: Float) = viewModelScope.launch {
        settings.setMusicVolume(v)
        container.configRepository.requestSync()
    }

    fun setUiSoundVolume(v: Float) = viewModelScope.launch {
        settings.setUiSoundVolume(v)
        container.configRepository.requestSync()
    }

    fun setBackgroundMusicTrack(selection: MusicSelection) = viewModelScope.launch {
        settings.setBackgroundMusicTrack(MusicSelection.encode(selection))
        container.configRepository.requestSync()
    }

    fun setEmulator(systemId: SystemId, emulator: EmulatorTarget) = viewModelScope.launch {
        settings.setEmulatorFor(systemId, emulator)
        container.configRepository.requestSync()
    }

    fun setCore(systemId: SystemId, coreId: String) = viewModelScope.launch {
        settings.setRetroArchCoreFor(systemId, coreId)
        container.configRepository.requestSync()
    }

    // Scrapper: no llama a configRepository.requestSync() a propósito — las credenciales no deben
    // terminar en ginny.cfg (texto plano en la Carpeta de Usuario).
    fun setScreenScraperUser(value: String) = viewModelScope.launch { scrapper.setScreenScraperUser(value) }
    fun setScreenScraperPassword(value: String) = viewModelScope.launch { scrapper.setScreenScraperPassword(value) }
    fun setSteamGridDbApiKey(value: String) = viewModelScope.launch { scrapper.setSteamGridDbApiKey(value) }
    fun setScrapMediaEnabled(media: ScrapMedia, enabled: Boolean) = viewModelScope.launch { scrapper.setMediaEnabled(media, enabled) }
    fun setScrapOnlyOnWifi(value: Boolean) = viewModelScope.launch { scrapper.setOnlyOnWifi(value) }

    // ── Scrapeo de la biblioteca ──────────────────────────────────────────────────────────────
    // El lote vive en el scope de la aplicación (ScrapRunner): salir de Configuración no lo detiene.

    val scrapProgress: StateFlow<ScrapProgress?> = container.scrapRunner.progress
    val isScrapping: StateFlow<Boolean> = container.scrapRunner.isRunning

    /** [force]: vuelve a consultar lo que antes no se encontró (no sobrescribe archivos). */
    fun startScrap(force: Boolean) = container.scrapRunner.startLibrary(force)

    fun stopScrap() = container.scrapRunner.stop()

    // ── Verificar credenciales ────────────────────────────────────────────────────────────────

    /** Resultado de "Verificar" de un proveedor. */
    data class VerifyState(val running: Boolean = false, val ok: Boolean = false, val message: String? = null)

    private val _verify = MutableStateFlow<Map<ScrapProvider, VerifyState>>(emptyMap())
    val verify: StateFlow<Map<ScrapProvider, VerifyState>> = _verify

    private fun setVerify(provider: ScrapProvider, state: VerifyState) {
        _verify.value = _verify.value + (provider to state)
    }

    fun verifyScreenScraper() = viewModelScope.launch {
        setVerify(ScrapProvider.SCREENSCRAPER, VerifyState(running = true))
        val state = if (!ScrapperDevCredentials.screenScraperAvailable) {
            VerifyState(message = "Este APK se compiló sin las credenciales de desarrollador de ScreenScraper (ver README).")
        } else {
            when (val result = container.screenScraperClient.verifyAccount()) {
                is ScrapResult.Ok -> {
                    val user = result.value
                    VerifyState(
                        ok = true,
                        message = "Cuenta válida · hoy ${user.requestsToday ?: "?"} de ${user.maxRequestsPerDay ?: "?"} peticiones · " +
                            "${user.maxThreads ?: "?"} ${if (user.maxThreads == 1) "hilo" else "hilos"}",
                    )
                }
                is ScrapResult.Failure -> VerifyState(message = result.message)
            }
        }
        setVerify(ScrapProvider.SCREENSCRAPER, state)
    }

    fun verifySteamGridDb() = viewModelScope.launch {
        setVerify(ScrapProvider.STEAMGRIDDB, VerifyState(running = true))
        val state = when (val result = container.steamGridDbClient.verifyKey()) {
            is ScrapResult.Ok -> VerifyState(ok = true, message = "Clave válida")
            is ScrapResult.Failure -> VerifyState(message = result.message)
        }
        setVerify(ScrapProvider.STEAMGRIDDB, state)
    }

    fun startListeningForButton(button: LogicalButton) {
        listeningForButton = button
    }

    fun onKeyCaptured(keyCode: Int) {
        val button = listeningForButton ?: return
        listeningForButton = null
        viewModelScope.launch {
            settings.setButtonKeyCode(button, keyCode)
            container.configRepository.requestSync()
        }
    }

    fun cancelListening() {
        listeningForButton = null
    }

    fun resetButtonMapping() = viewModelScope.launch {
        settings.resetButtonMapping()
        container.configRepository.requestSync()
    }
}
