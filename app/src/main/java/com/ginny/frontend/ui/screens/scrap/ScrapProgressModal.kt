package com.ginny.frontend.ui.screens.scrap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.GinnyProgressBar
import com.ginny.frontend.ui.screens.settings.SettingsModalCornerRadius
import com.ginny.frontend.ui.screens.settings.SettingsModalMargin
import com.ginny.frontend.ui.screens.settings.SettingsScrimColor
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/** Ancho máximo de la tarjeta del modal de avance. */
private val ProgressCardMaxWidth = 560.dp

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

        ScrapModalScrim(onDismiss = onClose) {
            val shape = RoundedCornerShape(SettingsModalCornerRadius)
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .widthIn(max = ProgressCardMaxWidth)
                    .fillMaxWidth()
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
                } else {
                    ProgressHeader(shown, running)
                    // La única parte que cede alto: si no cabe, se desplaza y los botones quedan siempre a la vista.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
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
                    if (running) GinnyButton("Detener", onClick = viewModel::stop)
                    GinnyButton("Cerrar", primary = !running, onClick = onClose)
                }
            }
        }
    }
}

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
            current.media.forEach { (media, has) -> InfoLine("· ${media.label}", yesNo(has), muted = true) }
            InfoLine("Estado", stateText(current))
        }
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
 * Cortina oscura a pantalla completa con el contenido centrado. Tocar fuera de la tarjeta ([onDismiss])
 * cierra el modal; los toques no llegan a lo que hay detrás.
 */
@Composable
fun ScrapModalScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // Se queda con el foco y consume las teclas: la cruceta no debe mover lo que hay detrás (Configuración
    // o la grilla). B lo maneja MainActivity antes de llegar aquí.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsScrimColor)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { true }
            .padding(SettingsModalMargin),
    ) {
        content()
    }
}
