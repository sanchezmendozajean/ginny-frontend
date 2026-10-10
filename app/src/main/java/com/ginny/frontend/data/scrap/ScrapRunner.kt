package com.ginny.frontend.data.scrap

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
    private val _progress = MutableStateFlow<ScrapProgress?>(null)
    val progress: StateFlow<ScrapProgress?> = _progress

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var job: Job? = null

    /**
     * Scrapea toda la biblioteca escaneada. [force]: vuelve a consultar los juegos que ScreenScraper no
     * conocía y las medias que no tenía (no sobrescribe archivos). No hace nada si ya hay un lote en curso.
     */
    fun startLibrary(force: Boolean) {
        if (!_isRunning.compareAndSet(expect = false, update = true)) return
        job = scope.launch {
            try {
                val games = repository.libraryGames()
                if (games.isEmpty()) {
                    _progress.value = ScrapProgress(total = 0)
                        .stopped("No hay juegos en la biblioteca. Escanéala primero en Configuración > Carpetas.")
                    return@launch
                }
                repository.run(games, force).collect { _progress.value = it }
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
    }

    /** Detiene el lote en curso: aborta la petición actual y no deja archivos a medias. */
    fun stop() {
        job?.cancel()
    }
}
