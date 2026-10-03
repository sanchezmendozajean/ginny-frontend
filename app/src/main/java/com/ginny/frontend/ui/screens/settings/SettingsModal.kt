package com.ginny.frontend.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.ui.theme.GinnyThemes
import com.ginny.frontend.ui.util.LocalSoundManager
import com.ginny.frontend.ui.util.appContainer

/** Color de la cortina oscura que cubre la pantalla detrás del modal. */
val SettingsScrimColor = Color.Black.copy(alpha = 0.35f)

/** Margen entre el borde de la pantalla y el contenido del modal. */
val SettingsModalMargin = 36.dp

/** Radio de las esquinas del modal. */
val SettingsModalCornerRadius = 28.dp

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
fun SettingsModal(visible: Boolean, onClose: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val palette = GinnyThemes.current
        val sound = LocalSoundManager.current
        val buttonMapping by appContainer().settingsRepository.buttonMapping
            .collectAsState(initial = ButtonMapping.DEFAULT)

        // Sección elegida. Se reinicia en Interfaz cada vez que se abre el modal.
        var section by remember { mutableStateOf(SettingsSection.Interfaz) }
        fun select(target: SettingsSection) {
            if (target == section) return
            section = target
            sound?.playFocusSound()
        }

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
                // Cruceta arriba/abajo: cambia de sección. Todas las teclas se consumen, para que
                // el foco no se escape hacia la grilla. (B y Menú los maneja MainActivity antes.)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val sections = SettingsSection.entries
                        when (buttonMapping.buttonForKeyCode(event.nativeKeyEvent.keyCode)) {
                            LogicalButton.DPAD_UP -> select(sections[(section.ordinal - 1).coerceAtLeast(0)])
                            LogicalButton.DPAD_DOWN -> select(sections[(section.ordinal + 1).coerceAtMost(sections.lastIndex)])
                            else -> Unit
                        }
                    }
                    true
                },
        ) {
            val shape = RoundedCornerShape(SettingsModalCornerRadius)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SettingsModalMargin)
                    .clip(shape) // recorta la barra lateral a las esquinas redondeadas
                    .background(Color.White.copy(alpha = 0.75f), shape),
            ) {
                SettingsSidebar(selected = section, onSelect = ::select, onClose = onClose)

                // Línea divisoria entre la barra lateral y el contenido.
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(palette.ink.copy(alpha = 0.08f)),
                )

                // Contenido de la sección elegida (siguiente paso).
                Box(modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
