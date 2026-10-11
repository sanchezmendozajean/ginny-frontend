package com.ginny.frontend

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.data.model.ButtonLayout
import com.ginny.frontend.ui.GinnyApp
import com.ginny.frontend.ui.components.LocalButtonLayout
import com.ginny.frontend.ui.theme.GinnyTheme
import com.ginny.frontend.ui.util.LocalSoundManager
import kotlinx.coroutines.launch

/**
 * Single-activity host. Immersive landscape shell.
 *
 * La UI se está reconstruyendo paso a paso (docs/plan-reconstruccion-ui.md). Los botones globales
 * (Menú / Y abren Configuración, B la cierra) se manejan aquí, en [dispatchKeyEvent], porque deben
 * funcionar sin importar qué composable tenga el foco — mismo enfoque que el diseño anterior
 * (docs/ui-legacy/MainActivity.kt). La navegación entre pantallas se reincorporará cuando existan.
 */
class MainActivity : ComponentActivity() {

    private lateinit var soundManager: SoundManager
    private var buttonMapping: ButtonMapping = ButtonMapping.DEFAULT

    /** Si el modal de Configuración está abierto. Es estado de Compose: la UI se actualiza sola. */
    private val settingsOpen = mutableStateOf(false)

    /** Si hay un modal que usa Y (el de un juego: Video; el de avance del scrapeo: Detener): Y deja de abrir Configuración. */
    @Volatile private var gameModalOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val container = (application as GinnyApplication).container
        soundManager = SoundManager(this, container.settingsRepository, container.musicRepository, lifecycleScope)
        soundManager.start()

        // Mapeo de botones remapeable (Configuración): las teclas se interpretan a través de él.
        lifecycleScope.launch {
            container.settingsRepository.buttonMapping.collect { buttonMapping = it }
        }

        setContent {
            GinnyTheme(settingsRepository = container.settingsRepository) {
                // Distribución de botones (Configuración > Botones), para los íconos de la leyenda.
                val buttonLayout by container.settingsRepository.buttonLayout
                    .collectAsState(initial = ButtonLayout.YX_BA)

                // Los efectos de sonido y la distribución de botones, disponibles para toda la interfaz.
                CompositionLocalProvider(
                    LocalSoundManager provides soundManager,
                    LocalButtonLayout provides buttonLayout,
                ) {
                    GinnyApp(
                        settingsOpen = settingsOpen.value,
                        onCloseSettings = { settingsOpen.value = false },
                        onOpenSettings = { settingsOpen.value = true },
                        onGameModalOpenChanged = { gameModalOpen = it },
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() {
        super.onResume()
        soundManager.resumeMusic()
    }

    override fun onPause() {
        soundManager.pauseMusic()
        super.onPause()
    }

    override fun onDestroy() {
        soundManager.release()
        super.onDestroy()
    }

    /**
     * Corre antes que cualquier manejo de teclas de Compose, por eso aquí van los botones que deben
     * funcionar siempre. Lo que no se maneja aquí sigue su camino hacia Compose (ej. la cruceta).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val button = buttonMapping.buttonForKeyCode(event.keyCode)
            when (button) {
                // Menú abre Configuración. Y también, mientras la única pantalla sea Sistemas
                // (regla del spec: en Juegos, Y abrirá las opciones del juego).
                LogicalButton.MENU, LogicalButton.Y -> {
                    val yBelongsToGameModal = button == LogicalButton.Y && gameModalOpen
                    if (!settingsOpen.value && !yBelongsToGameModal) {
                        settingsOpen.value = true
                        return true
                    }
                }
                // B cierra Configuración; si está cerrada, regresa a la pantalla anterior (lo
                // maneja el BackHandler de GinnyApp). Solo si hay a dónde volver: en Sistemas,
                // B no hace nada (el "atrás" por defecto cerraría la app).
                LogicalButton.B -> {
                    if (settingsOpen.value) {
                        // Con Configuración abierta, los únicos callbacks activos son los de los modales
                        // de scrapeo (encima de ella): B cierra primero esos.
                        if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                            onBackPressedDispatcher.onBackPressed()
                        } else {
                            settingsOpen.value = false
                        }
                    } else if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                        onBackPressedDispatcher.onBackPressed()
                    }
                    return true
                }
                else -> Unit
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
