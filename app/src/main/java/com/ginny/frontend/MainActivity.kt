package com.ginny.frontend

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.ui.navigation.GinnyNavHost
import com.ginny.frontend.ui.navigation.Routes
import com.ginny.frontend.ui.theme.GinnyFrontendTheme
import kotlinx.coroutines.launch

/**
 * Single-activity host. Immersive landscape shell; hardware Back(B)/Menu are handled globally
 * here via [dispatchKeyEvent] rather than per-screen, since they must work regardless of which
 * composable currently owns D-Pad focus (see SnapFocusGrid, which deliberately never consumes B).
 */
class MainActivity : ComponentActivity() {

    private lateinit var soundManager: SoundManager
    private var navController: NavHostController? = null
    private var buttonMapping: ButtonMapping = ButtonMapping.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val container = (application as GinnyApplication).container
        soundManager = SoundManager(this, container.settingsRepository, container.musicRepository, lifecycleScope)
        soundManager.start()

        lifecycleScope.launch {
            container.settingsRepository.buttonMapping.collect { buttonMapping = it }
        }

        setContent {
            GinnyFrontendTheme {
                GinnyNavHost(
                    container = container,
                    soundManager = soundManager,
                    onNavControllerReady = { navController = it },
                )
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val nav = navController
            when (buttonMapping.buttonForKeyCode(event.keyCode)) {
                LogicalButton.B -> {
                    if (nav != null && nav.previousBackStackEntry != null) {
                        nav.popBackStack()
                        return true
                    }
                }
                LogicalButton.MENU -> {
                    if (nav != null && nav.currentDestination?.route != Routes.SETTINGS) {
                        nav.navigate(Routes.SETTINGS)
                        return true
                    }
                }
                else -> Unit
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
