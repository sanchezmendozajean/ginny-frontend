package com.ginny.frontend

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.ui.GinnyApp
import com.ginny.frontend.ui.theme.GinnyTheme
import com.ginny.frontend.ui.util.LocalSoundManager

/**
 * Single-activity host. Immersive landscape shell.
 *
 * La UI se está reconstruyendo paso a paso (docs/plan-reconstruccion-ui.md). El manejo global de
 * Back(B)/Menu vía dispatchKeyEvent y la navegación de la versión anterior están
 * archivados en docs/ui-legacy/MainActivity.kt y se reincorporarán cuando existan pantallas.
 */
class MainActivity : ComponentActivity() {

    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val container = (application as GinnyApplication).container
        soundManager = SoundManager(this, container.settingsRepository, container.musicRepository, lifecycleScope)
        soundManager.start()

        setContent {
            GinnyTheme(settingsRepository = container.settingsRepository) {
                // Los efectos de sonido, disponibles para toda la interfaz.
                CompositionLocalProvider(LocalSoundManager provides soundManager) {
                    GinnyApp()
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
}
