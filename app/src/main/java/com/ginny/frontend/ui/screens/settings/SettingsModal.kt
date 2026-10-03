package com.ginny.frontend.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Color de la cortina oscura que cubre la pantalla detrás del modal. */
val SettingsScrimColor = Color.Black.copy(alpha = 0.35f)

/** Margen entre el borde de la pantalla y el contenido del modal. */
val SettingsModalMargin = 36.dp

/**
 * Modal de Configuración: una cortina oscura sobre toda la pantalla y, encima, el contenido (por
 * ahora una fila vacía que ocupa la pantalla con un margen de [SettingsModalMargin]).
 *
 * El desenfoque de lo que queda detrás no se hace aquí: lo aplica GinnyApp a la interfaz de
 * atrás mientras el modal está abierto (Modifier.blur, API 31+; en 29-30 queda solo la cortina).
 *
 * Abrir / cerrar lo decide MainActivity (Menú / Y abren, B cierra), igual que en el diseño
 * anterior: así funciona sin importar qué tenga el foco. Aparece y desaparece con un fade.
 */
@Composable
fun SettingsModal(visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        // Mientras está abierto, el modal se queda con el foco: así la cruceta no mueve el cursor
        // de la grilla que está detrás.
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SettingsScrimColor)
                // Los toques sobre la cortina no llegan a lo que está detrás.
                .pointerInput(Unit) { detectTapGestures { } }
                .focusRequester(focusRequester)
                .focusable()
                // Por ahora no hay nada navegable adentro: se consumen las teclas para que el
                // foco no se escape hacia la grilla. (B y Menú los maneja MainActivity antes.)
                .onKeyEvent { true },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SettingsModalMargin)
                    // Fondo provisional, solo para ver dónde está la fila.
                    .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(16.dp)),
            ) {
                // Contenido de Configuración
            }
        }
    }
}
