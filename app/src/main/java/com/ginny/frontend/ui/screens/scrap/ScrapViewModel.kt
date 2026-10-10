package com.ginny.frontend.ui.screens.scrap

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.data.scrap.ScrapProgress
import com.ginny.frontend.data.scrap.ScrapScope
import com.ginny.frontend.data.scrap.SystemScrapStats
import com.ginny.frontend.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Estado de los dos modales del scrapeo: el que **lanza** el lote (lista de sistemas, alcance y
 * sobrescritura) y el que **muestra su avance**. El lote en sí vive en
 * [com.ginny.frontend.data.scrap.ScrapRunner], fuera de cualquier pantalla.
 */
class ScrapViewModel(private val container: AppContainer) : ViewModel() {
    private val runner = container.scrapRunner

    val progress: StateFlow<ScrapProgress?> = runner.progress
    val isRunning: StateFlow<Boolean> = runner.isRunning

    // ── Modal de lanzamiento ──────────────────────────────────────────────────────────────────

    /** Calculado sistema por sistema: un sistema ausente todavía se está revisando. */
    private val _stats = MutableStateFlow<Map<SystemId, SystemScrapStats>>(emptyMap())
    val stats: StateFlow<Map<SystemId, SystemScrapStats>> = _stats

    /** Sistemas con juegos escaneados, en el orden en que se listan. */
    private val _systems = MutableStateFlow<List<SystemId>>(emptyList())
    val systems: StateFlow<List<SystemId>> = _systems

    private val _logos = MutableStateFlow<Map<SystemId, Uri>>(emptyMap())
    val logos: StateFlow<Map<SystemId, Uri>> = _logos

    /** Revisando lo que hay guardado (en bibliotecas grandes tarda). */
    private val _inspecting = MutableStateFlow(false)
    val inspecting: StateFlow<Boolean> = _inspecting

    private val _selected = MutableStateFlow<Set<SystemId>>(emptySet())
    val selected: StateFlow<Set<SystemId>> = _selected

    private val _scope = MutableStateFlow(ScrapScope.PENDING)
    val scope: StateFlow<ScrapScope> = _scope

    private val _overwrite = MutableStateFlow(false)
    val overwrite: StateFlow<Boolean> = _overwrite

    /** Mientras el usuario no toque la lista, se marcan solos los sistemas que van resultando con pendientes. */
    private var selectionTouched = false
    private var inspectJob: Job? = null

    /** Abre el modal de lanzamiento: revisa la biblioteca y deja marcados los sistemas con pendientes. */
    fun prepareLaunch() {
        inspectJob?.cancel()
        selectionTouched = false
        _stats.value = emptyMap()
        _selected.value = emptySet()
        _inspecting.value = true
        inspectJob = viewModelScope.launch {
            try {
                val games = runner.libraryGames()
                _systems.value = SystemCatalog.allSortedByName.map { it.id }.filter { id -> games.any { it.systemId == id } }
                loadLogos()
                runner.inspect(games).collect { stats ->
                    _stats.value = _stats.value + (stats.systemId to stats)
                    if (!selectionTouched && stats.pending > 0) _selected.value = _selected.value + stats.systemId
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Si la revisión falla a medias, la lista queda con lo que alcanzó a calcular.
            } finally {
                _inspecting.value = false
            }
        }
    }

    /** Cierra el modal de lanzamiento: deja de revisar. */
    fun cancelPrepare() {
        inspectJob?.cancel()
        inspectJob = null
    }

    private suspend fun loadLogos() {
        val userTree = container.settingsRepository.userFolderUri.first()?.let(Uri::parse) ?: return
        _logos.value = _systems.value.mapNotNull { id ->
            runCatching { container.assetRepository.systemLogoUri(userTree, id) }.getOrNull()?.let { id to it }
        }.toMap()
    }

    fun toggleSystem(id: SystemId) {
        selectionTouched = true
        _selected.value = _selected.value.let { if (id in it) it - id else it + id }
    }

    /** Marca todos los sistemas o, si ya lo están, ninguno. */
    fun toggleAll() {
        selectionTouched = true
        _selected.value = if (_selected.value.size == _systems.value.size) emptySet() else _systems.value.toSet()
    }

    fun setScope(value: ScrapScope) {
        _scope.value = value
    }

    fun setOverwrite(value: Boolean) {
        _overwrite.value = value
    }

    /** Cuántos juegos procesaría el lote con la selección y el alcance actuales. */
    fun selectedGameCount(stats: Map<SystemId, SystemScrapStats>, selected: Set<SystemId>, scope: ScrapScope): Int =
        selected.sumOf { stats[it]?.countFor(scope) ?: 0 }

    /** Scrapea un solo juego (desde su modal). false = ya había un lote en curso y no se hizo nada. */
    fun startGame(game: com.ginny.frontend.data.model.GameEntry, overwrite: Boolean): Boolean =
        runner.startGame(game, overwrite)

    fun start() {
        runner.start(_selected.value, _scope.value, _overwrite.value, _stats.value)
    }

    // ── Avance ────────────────────────────────────────────────────────────────────────────────

    fun stop() = runner.stop()
}
