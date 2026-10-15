package com.ginny.frontend

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
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
import com.ginny.frontend.ui.components.GinnyMenus
import com.ginny.frontend.ui.components.LocalButtonLayout
import com.ginny.frontend.ui.display.SecondaryDisplayActivity
import com.ginny.frontend.ui.display.mainDisplayId
import com.ginny.frontend.ui.display.otherDisplay
import com.ginny.frontend.ui.theme.GinnyTheme
import com.ginny.frontend.ui.util.LocalSoundManager
import kotlinx.coroutines.launch

/** Cuánto se espera, tras abrir la actividad de la otra pantalla, para devolverle el foco a la interfaz. */
private const val FocusReturnDelayMs = 600L

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

    private lateinit var displayManager: DisplayManager
    private var swapDisplays = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = syncDisplays()
        override fun onDisplayRemoved(displayId: Int) = syncDisplays()
        override fun onDisplayChanged(displayId: Int) = Unit
    }

    /** La pantalla en la que está esta actividad ahora. */
    private fun currentDisplayId(): Int {
        @Suppress("DEPRECATION")
        return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay)
            ?.displayId ?: Display.DEFAULT_DISPLAY
    }

    /**
     * Doble pantalla: deja la interfaz en la pantalla que le toca y la actividad secundaria (caja, video, logos y
     * wheels) en la otra; sin otra pantalla, la secundaria se cierra.
     */
    private fun syncDisplays() {
        if (isFinishing || isDestroyed) return
        val current = currentDisplayId()
        val target = displayManager.mainDisplayId(swapDisplays)
        if (current != target) {
            // Misma tarea: el sistema la mueve de pantalla (y avisa en onConfigurationChanged, que vuelve aquí).
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(target)
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    options.toBundle(),
                )
            }
            return
        }

        val other = displayManager.otherDisplay(current)
        val secondary = SecondaryDisplayActivity.instance
        when {
            other == null -> secondary?.finish()
            secondary == null || secondary.displayIdNow != other.displayId -> {
                secondary?.finish()
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(other.displayId)
                runCatching {
                    startActivity(
                        Intent(this, SecondaryDisplayActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                        options.toBundle(),
                    )
                }
                // La actividad nueva se queda con el foco de entrada de su pantalla; el mando debe seguir hablando
                // con la interfaz, así que se le devuelve (la ventana de la secundaria no es enfocable).
                window.decorView.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        runCatching {
                            startActivity(
                                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                ActivityOptions.makeBasic().setLaunchDisplayId(current).toBundle(),
                            )
                        }
                    }
                }, FocusReturnDelayMs)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncDisplays()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val container = (application as GinnyApplication).container
        soundManager = SoundManager(this, container.settingsRepository, container.musicRepository, lifecycleScope)
        soundManager.start()

        // Doble pantalla: la interfaz va en la pantalla que indique "Intercambiar pantalla principal"; si no es la
        // actual (al abrir, al cambiar la opción o al conectar / desconectar una pantalla), se relanza allí.
        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)
        lifecycleScope.launch {
            container.settingsRepository.swapDisplays.collect {
                swapDisplays = it
                syncDisplays()
            }
        }

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
        if (hasFocus) { hideSystemBars(); syncDisplays() }
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
        displayManager.unregisterDisplayListener(displayListener)
        SecondaryDisplayActivity.instance?.finish()
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
                    if (!settingsOpen.value && !yBelongsToGameModal && !GinnyMenus.isOpen) {
                        settingsOpen.value = true
                        return true
                    }
                }
                // B cierra Configuración; si está cerrada, regresa a la pantalla anterior (lo
                // maneja el BackHandler de GinnyApp). En Sistemas, el BackHandler de GinnyApp
                // pide confirmación para cerrar la app (el "atrás" por defecto la cerraría sin preguntar).
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
