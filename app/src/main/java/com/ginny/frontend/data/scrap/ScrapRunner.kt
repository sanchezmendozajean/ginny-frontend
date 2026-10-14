package com.ginny.frontend.data.scrap

import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SystemId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Ejecuta un lote de scrapeo **fuera de cualquier pantalla**: corre en el scope de la aplicación, así
 * que salir de Configuración no lo detiene, y la pantalla solo observa [progress].
 *
 * Un solo lote a la vez. El estado final (terminado, cortado o detenido por el usuario) queda en
 * [progress] hasta que se inicie otro.
 */
class ScrapRunner(
    private val repository: ScrapRepository,
    private val scope: CoroutineScope,
) {
    /** Calcula lo pendiente de cada sistema (ver [ScrapRepository.inspect]). */
    suspend fun libraryGames() = repository.libraryGames()

    fun inspect(games: List<com.ginny.frontend.data.model.GameEntry>) = repository.inspect(games)

    private val _progress = MutableStateFlow<ScrapProgress?>(null)
    val progress: StateFlow<ScrapProgress?> = _progress

    /**
     * El modal de avance está abierto: el lote se pone al ritmo de la pantalla (ver [ScrapRepository.paceForUi]); cerrado,
     * corre de corrido.
     */
    fun setProgressVisible(visible: Boolean) {
        repository.paceForUi = visible
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var job: Job? = null

    /**
     * Scrapea los juegos de [systems] que entran en [scope] (según [stats], lo calculado por
     * [ScrapRepository.inspect]). [ScrapScope.force] decide si se ignoran las cachés de "no encontrado";
     * [overwrite] reemplaza también lo ya guardado. No hace nada si ya hay un lote en curso.
     */
    fun start(systems: Set<SystemId>, scope: ScrapScope, overwrite: Boolean, stats: Map<SystemId, SystemScrapStats>) {
        launchBatch(force = scope.force, overwrite = overwrite, emptyMessage = "No hay juegos que scrapear con esta selección.") {
            repository.libraryGames().filter { game ->
                game.systemId in systems && stats[game.systemId]?.includes(scope, game.romFileName) != false
            }
        }
    }

    /**
     * Scrapea un solo juego (desde su modal). Como lo pide el usuario a propósito, siempre ignora las
     * cachés de "no encontrado". [overwrite] = false → solo lo que falte; true → todo, reemplazando lo
     * ya guardado. Devuelve false (sin hacer nada) si ya hay un lote en curso.
     */
    fun startGame(game: GameEntry, overwrite: Boolean): Boolean =
        launchBatch(force = true, overwrite = overwrite, emptyMessage = "No hay nada que scrapear.") { listOf(game) }

    private fun launchBatch(
        force: Boolean,
        overwrite: Boolean,
        emptyMessage: String,
        games: suspend () -> List<GameEntry>,
    ): Boolean {
        if (!_isRunning.compareAndSet(expect = false, update = true)) return false
        _progress.value = null
        job = scope.launch {
            try {
                val selected = games()
                if (selected.isEmpty()) {
                    _progress.value = ScrapProgress(total = 0).stopped(emptyMessage)
                    return@launch
                }
                repository.run(selected, force = force, overwrite = overwrite).collect { _progress.value = it }
            } catch (e: CancellationException) {
                // El usuario lo detuvo: se conserva el último avance, marcado como detenido.
                _progress.value = _progress.value?.takeIf { !it.finished }?.stopped("Detenido.")
                throw e
            } catch (e: Exception) {
                _progress.value = (_progress.value ?: ScrapProgress(total = 0))
                    .stopped("Se detuvo por un error inesperado: ${ScrapRedactor.redact(e.message ?: e.javaClass.simpleName)}")
            } finally {
                _isRunning.value = false
            }
        }
        return true
    }

    /** Detiene el lote en curso: aborta la petición actual y no deja archivos a medias. */
    fun stop() {
        job?.cancel()
    }
}
