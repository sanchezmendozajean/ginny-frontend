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
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SystemsViewModel(private val container: AppContainer) : ViewModel() {

    val gridRows: StateFlow<Int> = container.settingsRepository.gridRows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val buttonMapping: StateFlow<ButtonMapping> = container.settingsRepository.buttonMapping
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ButtonMapping.DEFAULT)

    /** Only systems whose ROM subfolder exists and has at least one valid game — per spec, a
     * system with nothing to show simply isn't listed rather than appearing empty. Read from the
     * Room-backed scan cache (populated from Settings > Carpetas > "Escanear biblioteca"), not a
     * live filesystem scan — see [com.ginny.frontend.data.GameIndexRepository]. The Favoritos tile
     * is prepended whenever at least one game is favorited (see [SystemTileItem]). */
    private val _tiles = MutableStateFlow<List<SystemTileItem>>(emptyList())
    val tiles: StateFlow<List<SystemTileItem>> = _tiles

    /** Game count per system, gathered alongside the availability scan so the UI can show
     * "[Sistema] - N juegos" without re-scanning. */
    private val _gameCounts = MutableStateFlow<Map<SystemId, Int>>(emptyMap())
    val gameCounts: StateFlow<Map<SystemId, Int>> = _gameCounts

    private val _favoriteCount = MutableStateFlow(0)
    val favoriteCount: StateFlow<Int> = _favoriteCount

    /** Distinguishes "still loading the cache" from "loaded, cache is empty" for the empty-state message. */
    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning

    var focusedIndex by mutableStateOf(0)

    /**
     * Logo de cada sistema que tenga uno (`Assets/<sistema>/system.png` en la Carpeta de Usuario),
     * por id. Los que no tienen logo simplemente no están en el mapa → `systemLogos[id]` da null.
     * Se calcula en segundo plano (búsqueda real de archivos vía SAF) y se recalcula solo si cambia
     * la Carpeta de Usuario en Configuración. Uso en la UI: `image = logos[SystemId.SNES]`.
     *
     * null = todavía buscando. Distinto de un mapa vacío ("ya busqué, no hay logos"): así la UI
     * puede esperar en vez de mostrar primero el reemplazo y después, de golpe, el logo.
     */
    val systemLogos: StateFlow<Map<SystemId, Uri>?> = container.settingsRepository.userFolderUri
        .map { uriString ->
            val userTree = uriString?.let(Uri::parse) ?: return@map emptyMap()
            SystemCatalog.all.mapNotNull { system ->
                runCatching { container.assetRepository.systemLogoUri(userTree, system.id) }
                    .getOrNull()
                    ?.let { logo -> system.id to logo }
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            combine(
                container.gameIndexRepository.observeGameCounts(),
                container.settingsRepository.favoriteGameKeys,
            ) { counts, favorites -> counts to favorites.size }
                .collect { (counts, favoriteCount) -> applyCounts(counts, favoriteCount) }
        }
    }

    private fun applyCounts(counts: Map<SystemId, Int>, favoriteCount: Int) {
        _gameCounts.value = counts
        _favoriteCount.value = favoriteCount
        val systems: List<SystemDefinition> = SystemCatalog.allSortedByName.filter { (counts[it.id] ?: 0) > 0 }
        _tiles.value = buildList {
            if (favoriteCount > 0) add(SystemTileItem.Favorites)
            addAll(systems.map(SystemTileItem::Real))
        }
        _isScanning.value = false
        focusedIndex = focusedIndex.coerceIn(0, (_tiles.value.size - 1).coerceAtLeast(0))
    }
}
