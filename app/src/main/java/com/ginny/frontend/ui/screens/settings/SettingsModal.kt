package com.ginny.frontend.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import com.ginny.frontend.ui.components.settings.SettingsCursorOverlay
import com.ginny.frontend.ui.components.settings.SettingsNav
import com.ginny.frontend.ui.components.settings.LocalSettingsNav
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
fun SettingsModal(
    visible: Boolean,
    onClose: () -> Unit,
    /** Hay otro modal (el de scrapeo) encima: Configuración le cede el foco y lo recupera al cerrarse. */
    nestedModalOpen: Boolean = false,
    onOpenScrapLaunch: () -> Unit = {},
    onOpenScrapProgress: () -> Unit = {},
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val palette = GinnyThemes.current
        val sound = LocalSoundManager.current
        val container = appContainer()
        val buttonMapping by container.settingsRepository.buttonMapping
            .collectAsState(initial = ButtonMapping.DEFAULT)
        // Lee y guarda la configuración (y sincroniza ginny.cfg).
        val viewModel: SettingsViewModel = viewModel(
            factory = viewModelFactory { initializer { SettingsViewModel(container) } },
        )

        // Sección elegida. Se reinicia en Interfaz cada vez que se abre el modal.
        var section by remember { mutableStateOf(SettingsSection.Interfaz) }

        // Foco por cruceta: en la lista de secciones (nav.active = false) o en las opciones de la sección.
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val nav = remember {
            SettingsNav(
                scope = scope,
                rowTolerancePx = with(density) { 10.dp.toPx() },
                edgeMarginPx = with(density) { (SettingsFadeHeight + 8.dp).toPx() },
            )
        }
        nav.buttonMapping = buttonMapping
        nav.sound = sound

        fun select(target: SettingsSection) {
            if (target == section) return
            nav.leave()
            section = target
            nav.selectSection(target.ordinal)
            sound?.playFocusSound()
        }

        // B con el foco en las opciones: de vuelta a la lista de secciones (antes de cerrar la Configuración).
        // Con un modal de scrapeo encima, B es de ese modal.
        BackHandler(enabled = nav.active && !nestedModalOpen) { nav.back() }

        // Mientras está abierto, el modal se queda con el foco: así la cruceta no mueve el cursor
        // de la grilla que está detrás.
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(nestedModalOpen) { if (!nestedModalOpen) focusRequester.requestFocus() }
        // Los controles que abren otra ventana (menú desplegable) o toman el foco (campo de texto) lo devuelven aquí.
        nav.reclaimFocus = { runCatching { focusRequester.requestFocus() } }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SettingsScrimColor)
                // Cualquier toque apaga el cursor (sin consumirlo): la pantalla pasa a manejarse con el dedo.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed }) nav.hideCursor()
                        }
                    }
                }
                // Los toques sobre la cortina no llegan a lo que está detrás.
                .pointerInput(Unit) { detectTapGestures { } }
                .focusRequester(focusRequester)
                .focusable()
                // Foco en la lista de secciones: arriba/abajo cambian de sección; A o derecha pasan el foco a
                // las opciones. Foco en las opciones: ver SettingsNav (B vuelve a la lista, más arriba).
                // Todas las teclas se consumen, para que el foco no se escape hacia la grilla.
                // (B y Menú los maneja MainActivity antes.)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val button = buttonMapping.buttonForKeyCode(event.nativeKeyEvent.keyCode)
                        // Con el cursor apagado (se tocó la pantalla), la primera tecla lo vuelve a mostrar; la
                        // cruceta además se mueve, pero A no actúa sobre algo que el usuario no veía.
                        val wasHidden = !nav.cursorVisible
                        if (wasHidden) nav.showCursor()
                        if (wasHidden && button == LogicalButton.A) return@onKeyEvent true
                        if (nav.active) {
                            nav.handleKey(button)
                        } else {
                            val sections = SettingsSection.entries
                            when (button) {
                                LogicalButton.DPAD_UP -> select(sections[(section.ordinal - 1).coerceAtLeast(0)])
                                LogicalButton.DPAD_DOWN -> select(sections[(section.ordinal + 1).coerceAtMost(sections.lastIndex)])
                                LogicalButton.A, LogicalButton.DPAD_RIGHT -> {
                                    sound?.playActivateSound()
                                    nav.enter()
                                }
                                else -> Unit
                            }
                        }
                    }
                    true
                },
        ) {
            val shape = RoundedCornerShape(SettingsModalCornerRadius)
            CompositionLocalProvider(LocalSettingsNav provides nav) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SettingsModalMargin)
                    .clip(shape) // recorta la barra lateral a las esquinas redondeadas
                    .background(Color.White.copy(alpha = 0.75f), shape),
            ) {
                SettingsSidebar(
                    selected = section,
                    onSelect = ::select,
                    onClose = onClose,
                    focusInOptions = nav.active,
                    onBackToSections = { nav.back() },
                )

                // Línea divisoria entre la barra lateral y el contenido.
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(palette.ink.copy(alpha = 0.08f)),
                )

                // Opciones de la sección elegida. key: cada sección empieza con su propio
                // desplazamiento, desde arriba.
                key(section) {
                    SettingsContent(modifier = Modifier.weight(1f)) {
                        when (section) {
                            SettingsSection.Interfaz -> InterfazOptions(viewModel)
                            SettingsSection.Carpetas -> CarpetasOptions(viewModel)
                            SettingsSection.Sistemas -> SistemasOptions(viewModel)
                            SettingsSection.Scrapper -> ScrapperOptions(viewModel, onOpenScrapLaunch, onOpenScrapProgress)
                            SettingsSection.Creditos -> CreditosOptions()
                        }
                    }
                }
            }

            // El cursor (en la lista de secciones o en las opciones), encima de todo y recortado a la tarjeta.
            SettingsCursorOverlay(
                nav = nav,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SettingsModalMargin)
                    .clip(shape),
            )
            }
        }
    }
}
