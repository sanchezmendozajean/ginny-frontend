package com.ginny.frontend.ui.screens.scrap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.data.scrap.ScrapScope
import com.ginny.frontend.data.scrap.SystemScrapStats
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.GinnyDropdown
import com.ginny.frontend.ui.components.settings.GinnyProgressBar
import androidx.compose.foundation.layout.widthIn
import com.ginny.frontend.ui.screens.settings.SettingsModalCornerRadius
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/** Desde este ancho de tarjeta se usan dos sistemas por fila y los combos junto al título. */
private val WideLayoutMinWidth = 680.dp

/**
 * Modal de "Scrappear": la lista de sistemas con sus juegos (total y pendientes), dos combos —qué
 * juegos procesar y si se sobrescribe lo que ya existe— y el botón que inicia el lote.
 *
 * Por defecto quedan marcados los sistemas que tienen pendientes. Al abrirse revisa la biblioteca
 * (lee `Assets/`), así que los números van apareciendo sistema por sistema.
 */
@Composable
fun ScrapLaunchModal(visible: Boolean, viewModel: ScrapViewModel, onClose: () -> Unit, onStarted: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val palette = GinnyThemes.current
        val systems by viewModel.systems.collectAsState()
        val stats by viewModel.stats.collectAsState()
        val logos by viewModel.logos.collectAsState()
        val selected by viewModel.selected.collectAsState()
        val scope by viewModel.scope.collectAsState()
        val overwrite by viewModel.overwrite.collectAsState()
        val inspecting by viewModel.inspecting.collectAsState()
        val running by viewModel.isRunning.collectAsState()

        val toProcess = viewModel.selectedGameCount(stats, selected, scope)
        val canStart = !running && !inspecting && toProcess > 0

        ScrapModalScrim(onDismiss = onClose) {
            val shape = RoundedCornerShape(SettingsModalCornerRadius)
            // Mismo tamaño que el modal de Configuración: toda la pantalla menos el margen (lo pone el scrim).
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f), shape)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                // Pantalla ancha: combos junto al título y dos sistemas por fila; angosta: una columna.
                val wide = maxWidth >= WideLayoutMinWidth
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    val title: @Composable () -> Unit = {
                        Text("Scrappear", fontFamily = GinnyFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = palette.ink)
                    }
                    val combos: @Composable () -> Unit = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GinnyDropdown(
                                selected = scope,
                                options = ScrapScope.entries.map { it.label to it },
                                onSelect = viewModel::setScope,
                                width = 230.dp,
                            )
                            GinnyDropdown(
                                selected = overwrite,
                                options = listOf("No sobreescribir si existe" to false, "Sobreescribir" to true),
                                onSelect = viewModel::setOverwrite,
                                width = 230.dp,
                            )
                        }
                    }
                    if (wide) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f)) { title() }
                            combos()
                        }
                    } else {
                        title()
                        combos()
                    }

                    if (systems.isEmpty()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Text(
                                text = if (inspecting) "Revisando la biblioteca..." else "No hay juegos en la biblioteca. Escanéala primero en Configuración > Carpetas.",
                                fontFamily = GinnyFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = palette.muted,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (wide) 2 else 1),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            items(systems, key = { it.name }) { id ->
                                SystemChoiceRow(
                                    id = id,
                                    stats = stats[id],
                                    logo = logos[id],
                                    checked = id in selected,
                                    onToggle = { viewModel.toggleSystem(id) },
                                )
                            }
                        }
                    }

                    // Casilla "todos" y resumen a la izquierda; botones a la derecha, en la misma fila.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        if (systems.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp) // zona de toque más grande que la casilla
                                    .pointerInput(Unit) { detectTapGestures { viewModel.toggleAll() } },
                            ) {
                                Checkbox(checked = selected.size == systems.size, size = 18.dp)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = when {
                                    running -> "Ya hay un scrapeo en curso."
                                    inspecting -> "Revisando lo que ya hay guardado... ${stats.size} de ${systems.size} sistemas"
                                    else -> "Se procesarán $toProcess ${if (toProcess == 1) "juego" else "juegos"}."
                                },
                                fontFamily = GinnyFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = palette.muted,
                            )
                            if (inspecting && systems.isNotEmpty()) {
                                GinnyProgressBar(fraction = stats.size.toFloat() / systems.size, modifier = Modifier.widthIn(max = 320.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GinnyButton("Cancelar", onClick = onClose)
                            GinnyButton(
                                "Iniciar",
                                primary = true,
                                enabled = canStart,
                                onClick = {
                                    viewModel.start()
                                    onStarted()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Una fila de la lista: casilla, ícono del sistema, su nombre y sus números. */
@Composable
private fun SystemChoiceRow(id: SystemId, stats: SystemScrapStats?, logo: android.net.Uri?, checked: Boolean, onToggle: () -> Unit) {
    val palette = GinnyThemes.current
    val shape = RoundedCornerShape(14.dp)
    val name = SystemCatalog.byId.getValue(id).displayName

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.7f), shape)
            .border(1.dp, palette.ink.copy(alpha = 0.08f), shape)
            .pointerInput(Unit) { detectTapGestures { onToggle() } }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Checkbox(checked)
        Spacer(Modifier.width(12.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
            if (logo != null) {
                AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp))
            } else {
                Text(
                    text = name.take(2).uppercase(),
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = palette.muted,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontFamily = GinnyFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (stats == null) "Revisando..." else counts(stats),
                fontFamily = GinnyFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun counts(stats: SystemScrapStats): String = buildString {
    append("${stats.total} ${if (stats.total == 1) "juego" else "juegos"} · ")
    append("${stats.pending} ${if (stats.pending == 1) "pendiente" else "pendientes"}")
    if (stats.notFound > 0) append(" · ${stats.notFound} sin encontrar")
}

@Composable
private fun Checkbox(checked: Boolean, size: androidx.compose.ui.unit.Dp = 22.dp) {
    val palette = GinnyThemes.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(if (checked) palette.accent else Color.Transparent, CircleShape)
            .border(2.dp, if (checked) palette.accent else palette.ink.copy(alpha = 0.25f), CircleShape),
    ) {
        if (checked) Text("✓", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White)
    }
}
