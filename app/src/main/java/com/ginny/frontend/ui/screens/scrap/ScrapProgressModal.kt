package com.ginny.frontend.ui.screens.scrap

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import com.ginny.frontend.ui.components.ScrapSpinner
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.tween
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.ui.components.settings.LocalSettingsNav
import com.ginny.frontend.ui.components.settings.SettingsCursorOverlay
import com.ginny.frontend.ui.components.settings.SettingsNav
import com.ginny.frontend.ui.util.LocalSoundManager
import com.ginny.frontend.ui.util.appContainer
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.data.scrap.ScrapCurrent
import com.ginny.frontend.data.scrap.ScrapMedia
import com.ginny.frontend.data.scrap.ScrapOutcome
import com.ginny.frontend.data.scrap.ScrapProgress
import com.ginny.frontend.ui.components.FaceButton
import com.ginny.frontend.ui.components.FaceButtonIcon
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.GinnyProgressBar
import com.ginny.frontend.ui.screens.settings.SettingsModalCornerRadius
import com.ginny.frontend.ui.screens.settings.SettingsModalMargin
import com.ginny.frontend.ui.screens.settings.SettingsScrimColor
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes


/**
 * Avance del scrapeo, al estilo del de ESDE pero sin log: una barra, "Elemento N de M" y los datos
 * del juego que se está procesando (archivo, sistema, qué se consiguió). Se abre tocando el ícono
 * animado de la barra superior. Tiene el botón **Detener** mientras el lote corre; B (o "Cerrar") lo
 * cierra sin detener nada.
 */
@Composable
fun ScrapProgressModal(visible: Boolean, viewModel: ScrapViewModel, onClose: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val palette = GinnyThemes.current
        val progress by viewModel.progress.collectAsState()
        val running by viewModel.isRunning.collectAsState()

        // Sin cursor: los botones llevan el ícono del botón del mando que los activa (B = Cerrar, Y = Detener).
        ScrapModalScrim(
            onDismiss = onClose,
            onButton = { if (it == LogicalButton.Y && running) viewModel.stop() },
        ) {
            val shape = RoundedCornerShape(SettingsModalCornerRadius)
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize() // mismo tamaño que Configuración: pantalla completa menos el margen
                    .background(Color.White.copy(alpha = 0.85f), shape)
                    .pointerInput(Unit) { detectTapGestures { } } // tocar la tarjeta no la cierra
                    .padding(24.dp),
            ) {
                Text(
                    text = title(progress, running),
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = palette.ink,
                )

                val shown = progress
                if (shown == null) {
                    Text("Preparando el lote...", fontFamily = GinnyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = palette.muted)
                    Spacer(Modifier.weight(1f)) // empuja los botones al fondo
                } else {
                    ProgressHeader(shown, running)
                    // La única parte que cede alto: si no cabe, se desplaza y los botones quedan siempre a la vista.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ProgressDetails(shown)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (running) {
                        GinnyButton("Detener", onClick = viewModel::stop, leadingIcon = { FaceButtonIcon(FaceButton.Y, tint = it, size = 20.dp) })
                    }
                    GinnyButton(
                        "Cerrar",
                        primary = !running,
                        onClick = onClose,
                        leadingIcon = { FaceButtonIcon(FaceButton.B, tint = it, size = 20.dp) },
                    )
                }
            }
        }
    }
}

/** Cuántas medias se muestran por fila en el modal de avance. */
private const val MEDIA_PER_ROW = 3

/** Duración del fundido de la caja de datos al pasar al juego siguiente (sale, y luego entra la nueva). */
private const val BOX_FADE_MS = 180

/** Tamaño de la animación junto a la media que se está descargando. */
private val MEDIA_SPINNER_SIZE = 14.dp

private fun title(progress: ScrapProgress?, running: Boolean): String = when {
    running -> "Scrapeando"
    progress?.stopReason != null -> "Scrapeo detenido"
    progress?.finished == true -> "Scrapeo terminado"
    else -> "Scrapeo"
}

/** Lo que siempre se ve: "Elemento N de M", el porcentaje y la barra. */
@Composable
private fun ProgressHeader(progress: ScrapProgress, running: Boolean) {
    val palette = GinnyThemes.current
    val current = progress.current

    // Durante el lote, el elemento en curso; al terminar, cuántos se revisaron.
    val item = if (running) (current?.index ?: progress.processed) else progress.processed
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Elemento $item de ${progress.total}",
                fontFamily = GinnyFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = palette.ink,
                modifier = Modifier.weight(1f),
            )
            val percent = if (progress.total > 0) progress.processed * 100 / progress.total else 0
            Text("$percent %", fontFamily = GinnyFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.muted)
        }
        GinnyProgressBar(fraction = if (progress.total > 0) progress.processed.toFloat() / progress.total else 0f)
    }
}

/**
 * Los datos del juego, los contadores y los avisos. Va dentro de la zona desplazable: su alto cambia
 * mientras corre el lote y no debe quitarle espacio a los botones.
 */
@Composable
private fun ProgressDetails(progress: ScrapProgress) {
    val palette = GinnyThemes.current
    val current = progress.current

    if (current != null) {
        // Al pasar al juego siguiente, la caja del que terminó se desvanece y luego entra la nueva (también con un fundido).
        // contentKey = el número del juego: los cambios dentro del mismo juego (otra media, otro estado) no animan nada y
        // la caja que sale conserva los datos con que terminó.
        AnimatedContent(
            targetState = current,
            contentKey = { it.index },
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(tween(BOX_FADE_MS, delayMillis = BOX_FADE_MS)),
                    initialContentExit = fadeOut(tween(BOX_FADE_MS)),
                    sizeTransform = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            label = "scrapCurrentBox",
        ) { shown -> CurrentGameBox(shown) }
    }

    Text(
        text = "Con datos nuevos: ${progress.scraped} · Sin coincidencia: ${progress.notFound} · " +
            "Ya completos: ${progress.skipped} · Con error: ${progress.failed}",
        fontFamily = GinnyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = palette.ink,
    )
    progress.warnings.forEach { Note("⚠ $it") }
    progress.stopReason?.let { Note("Se detuvo: $it") }
}

/** Caja con los datos del juego en curso: archivo, sistema, qué se consiguió y qué se está haciendo. */
@Composable
private fun CurrentGameBox(current: ScrapCurrent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        InfoLine("Archivo", current.fileName)
        InfoLine("Sistema", current.systemName)
        if (current.metadataWanted) InfoLine("Metadatos", yesNo(current.metadata))
        // Siempre presente si hay imágenes elegidas (con "..." mientras no se sabe): la lista no salta.
        if (current.media.keys.any { it != ScrapMedia.VIDEO }) InfoLine("Imágenes", yesNo(current.images))
        // Las medias, de a tres por fila para no ocupar tanto alto. La que se está descargando lleva la animación al lado.
        current.media.entries.chunked(MEDIA_PER_ROW).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (media, has) ->
                    MediaCell(media.label, yesNo(has), downloading = media == current.downloading, modifier = Modifier.weight(1f))
                }
                // Completa la última fila para que las columnas coincidan.
                repeat(MEDIA_PER_ROW - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        InfoLine("Estado", stateText(current))
    }
}

private fun yesNo(value: Boolean?): String = when (value) {
    true -> "Sí"
    false -> "No"
    null -> "..."
}

private fun stateText(current: ScrapCurrent): String = current.step ?: when (current.outcome) {
    ScrapOutcome.SCRAPED -> "Listo"
    ScrapOutcome.NOT_FOUND -> "Sin coincidencia en ScreenScraper"
    ScrapOutcome.SKIPPED -> "Ya estaba completo"
    ScrapOutcome.FAILED -> "Con error (se reintentará)"
    null -> "—"
}

/** Una media (ícono, wheel, caja…) con su Sí/No, para la cuadrícula de tres por fila. */
@Composable
private fun MediaCell(label: String, value: String, downloading: Boolean, modifier: Modifier = Modifier) {
    val palette = GinnyThemes.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp),
        )
        // El hueco existe siempre (con o sin animación): así los valores no se corren cuando la media empieza a descargarse.
        // Aparece y se va con un fundido (animateFloatAsState en vez de AnimatedVisibility: dentro de un Row, este último
        // resuelve a la variante de RowScope y no compila desde un Box anidado).
        val spinnerAlpha by animateFloatAsState(if (downloading) 1f else 0f, tween(150), label = "mediaSpinnerAlpha")
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(MEDIA_SPINNER_SIZE)) {
            if (spinnerAlpha > 0f) {
                ScrapSpinner(iconSize = MEDIA_SPINNER_SIZE, modifier = Modifier.graphicsLayer { alpha = spinnerAlpha })
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(text = value, fontFamily = GinnyFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = palette.ink, maxLines = 1)
    }
}

@Composable
private fun InfoLine(label: String, value: String, muted: Boolean = false) {
    val palette = GinnyThemes.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = palette.muted,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            fontFamily = GinnyFont,
            fontWeight = if (muted) FontWeight.Medium else FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = palette.ink,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, fontFamily = GinnyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = GinnyThemes.current.muted)
}

/**
 * El foco por cruceta de un modal del scrapper: lo mismo que en Configuración (cursor sobre cada botón,
 * casilla o desplegable), pero sin lista de secciones a la que volver. Se usa con [ScrapModalScrim].
 */
@Composable
fun rememberModalNav(): SettingsNav {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember {
        SettingsNav(
            scope = scope,
            rowTolerancePx = with(density) { 10.dp.toPx() },
            edgeMarginPx = with(density) { 16.dp.toPx() },
        ).also { it.canLeave = false }
    }
}

/**
 * Cortina oscura a pantalla completa con el contenido centrado. Tocar fuera de la tarjeta ([onDismiss])
 * cierra el modal; los toques no llegan a lo que hay detrás.
 *
 * Con [nav] el modal se maneja con el mando: el cursor empieza en el último control (el botón principal,
 * abajo a la derecha), la cruceta lo mueve y A actúa sobre el que tiene el cursor. B (cerrar) lo maneja
 * MainActivity antes de llegar aquí. Tocar la pantalla apaga el cursor; la cruceta lo vuelve a mostrar.
 */
@Composable
fun ScrapModalScrim(
    onDismiss: () -> Unit,
    nav: SettingsNav? = null,
    /** El cursor empieza en el último control (el botón principal de abajo a la derecha) o, si es false, en el primero. */
    focusLast: Boolean = true,
    /** Cada botón del mando que se pulsa (para los modales con atajos propios, como Y = Detener). */
    onButton: ((LogicalButton?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val buttonMapping by appContainer().settingsRepository.buttonMapping.collectAsState(initial = ButtonMapping.DEFAULT)
    val sound = LocalSoundManager.current

    // Se queda con el foco y consume las teclas: la cruceta no debe mover lo que hay detrás (Configuración
    // o la grilla).
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    if (nav != null) {
        nav.buttonMapping = buttonMapping
        nav.sound = sound
        nav.reclaimFocus = { runCatching { focusRequester.requestFocus() } }
        // Con los controles ya colocados, el cursor empieza en el botón principal.
        LaunchedEffect(nav) {
            delay(150)
            nav.enter(last = focusLast)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsScrimColor)
            .then(
                if (nav != null) {
                    // Cualquier toque apaga el cursor (sin consumirlo).
                    Modifier.pointerInput(nav) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.pressed }) nav.hideCursor()
                            }
                        }
                    }
                } else {
                    Modifier
                },
            )
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (onButton != null && event.type == KeyEventType.KeyDown) {
                    onButton(buttonMapping.buttonForKeyCode(event.nativeKeyEvent.keyCode))
                }
                if (nav != null && event.type == KeyEventType.KeyDown) {
                    val button = buttonMapping.buttonForKeyCode(event.nativeKeyEvent.keyCode)
                    // Con el cursor apagado, la primera tecla solo lo vuelve a mostrar (A no actúa a ciegas).
                    val wasHidden = !nav.cursorVisible
                    if (wasHidden) nav.showCursor()
                    if (nav.active && !(wasHidden && button == LogicalButton.A)) nav.handleKey(button)
                }
                true
            }
            .padding(SettingsModalMargin),
    ) {
        if (nav == null) {
            content()
        } else {
            CompositionLocalProvider(LocalSettingsNav provides nav) {
                content()
                // El cursor, encima de la tarjeta y recortado a sus esquinas.
                SettingsCursorOverlay(
                    nav = nav,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(SettingsModalCornerRadius)),
                    compact = true, // más corto y fino que en Configuración
                )
            }
        }
    }
}
