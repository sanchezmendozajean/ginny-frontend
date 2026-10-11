package com.ginny.frontend.ui.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * La pantalla donde va la interfaz: la principal del dispositivo, o la secundaria si [swap] (Configuración >
 * Interfaz > Intercambiar pantalla principal) y existe. Sin pantalla secundaria siempre es la principal.
 */
fun DisplayManager.mainDisplayId(swap: Boolean): Int =
    if (swap) secondaryDisplay()?.displayId ?: Display.DEFAULT_DISPLAY
    else Display.DEFAULT_DISPLAY

/**
 * La otra pantalla —donde va la actividad secundaria— respecto de la que muestra la interfaz
 * ([activityDisplayId]): la secundaria si la interfaz está en la principal y, si está en la secundaria, la principal.
 */
fun DisplayManager.otherDisplay(activityDisplayId: Int): Display? =
    if (activityDisplayId == Display.DEFAULT_DISPLAY) secondaryDisplay()
    else getDisplay(Display.DEFAULT_DISPLAY)

/** La primera pantalla que no es la principal, o null si el dispositivo solo tiene una. */
private fun DisplayManager.secondaryDisplay(): Display? =
    displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }

/**
 * La otra pantalla (null = no hay), según en cuál está la interfaz. Se actualiza sola cuando una pantalla se
 * conecta o se desconecta (también las simuladas).
 */
@Composable
fun rememberOtherDisplay(): Display? {
    val context = LocalContext.current
    val activityDisplayId = LocalView.current.display?.displayId ?: Display.DEFAULT_DISPLAY
    val manager = remember(context) { context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    var display by remember(activityDisplayId) { mutableStateOf(manager.otherDisplay(activityDisplayId)) }

    DisposableEffect(manager, activityDisplayId) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { display = manager.otherDisplay(activityDisplayId) }
            override fun onDisplayRemoved(displayId: Int) { display = manager.otherDisplay(activityDisplayId) }
            override fun onDisplayChanged(displayId: Int) = Unit
        }
        manager.registerDisplayListener(listener, null)
        display = manager.otherDisplay(activityDisplayId)
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    return display
}
