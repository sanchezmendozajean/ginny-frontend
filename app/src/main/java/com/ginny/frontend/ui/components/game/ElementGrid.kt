package com.ginny.frontend.ui.components.game

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import kotlin.math.floor
import kotlin.math.max

/** Lo que la grilla necesita saber de cada elemento, venga de un sistema, juego o estado guardado. */
data class GridEntry(
    val key: String,       // identidad estable (ej. el id del sistema): Compose la usa para no confundirlos
    val title: String,
    val subtitle: String = "",
    val image: Uri? = null,
    @DrawableRes val icon: Int? = null, // alternativa a [image]: ícono de la app, chico y teñido
)

/** Separación entre elementos. */
val ElementGridGap = 8.dp

/** Cuánto sobresale el cursor del elemento, por cada lado. */
val CursorOutset = 10.dp

/**
 * Una página de la grilla: [rows] filas de elementos cuadrados, con todas las columnas que entren,
 * y el cursor sobre el elemento seleccionado.
 *
 * Reglas del spec:
 * - Lado del elemento = (alto disponible − separaciones) ÷ filas → siempre caben exactamente
 *   [rows] filas, sin cortar ninguna.
 * - Columnas = las que entren en el ancho con ese lado.
 * - Elementos por página = columnas × filas. Si la página no se llena, el resto son huecos
 *   ([Element] con empty = true), para que la grilla conserve su forma.
 * - El orden es el de [entries] (la pantalla lo entrega ya ordenado alfabéticamente).
 *
 * Selección: la página mostrada es la del elemento [selectedIndex]. La cruceta y L/R (leídos con
 * [buttonMapping], remapeable) y el toque sobre un elemento piden cambiarla vía
 * [onSelectedIndexChange]; quien usa la grilla guarda el índice (en el ViewModel, regla del spec).
 * Las reglas de movimiento están en [GridNavigation]. B nunca se maneja aquí (va a nivel Activity).
 *
 * [onLayoutChanged] informa la forma de la grilla (columnas × filas), para calcular páginas.
 */
@Composable
fun ElementGrid(
    entries: List<GridEntry>,
    rows: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    buttonMapping: ButtonMapping,
    modifier: Modifier = Modifier,
    gap: Dp = ElementGridGap,
    onLayoutChanged: (GridLayout) -> Unit = {},
) {
    // Para que el teclado llegue a la grilla, la grilla tiene que tener el foco.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // BoxWithConstraints: da el tamaño disponible (maxWidth / maxHeight) para calcular el lado.
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val safeRows = rows.coerceIn(1, 4)
        val tileSize = (maxHeight - gap * (safeRows - 1)) / safeRows
        val columns = max(1, floor((maxWidth + gap) / (tileSize + gap)).toInt())
        val layout = GridLayout(columns, safeRows)

        // Se informa fuera de la composición (efecto), nunca mientras se dibuja.
        LaunchedEffect(layout) { onLayoutChanged(layout) }

        val count = entries.size
        val selected = selectedIndex.coerceIn(0, max(0, count - 1))
        val page = layout.pageOf(selected)
        val pageStart = page * layout.perPage

        // El manejo de teclas se crea una vez; rememberUpdatedState le da siempre los valores
        // actuales sin tener que recrearlo.
        val current by rememberUpdatedState(Triple(selected, layout, buttonMapping))

        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || count == 0) return@onKeyEvent false
                    val (i, g, mapping) = current
                    val target = when (mapping.buttonForKeyCode(event.nativeKeyEvent.keyCode)) {
                        LogicalButton.DPAD_UP -> GridNavigation.up(i, g, count)
                        LogicalButton.DPAD_DOWN -> GridNavigation.down(i, g, count)
                        LogicalButton.DPAD_LEFT -> GridNavigation.left(i, g, count)
                        LogicalButton.DPAD_RIGHT -> GridNavigation.right(i, g, count)
                        LogicalButton.L -> GridNavigation.previousPage(i, g, count)
                        LogicalButton.R -> GridNavigation.nextPage(i, g, count)
                        else -> return@onKeyEvent false // A, B, Y, Menú: no son de la grilla
                    }
                    if (target != i) onSelectedIndexChange(target)
                    true
                },
        ) {
            for (row in 0 until safeRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until columns) {
                        val index = pageStart + row * columns + col
                        val entry = entries.getOrNull(index)
                        if (entry != null) {
                            // key: si los datos cambian, cada Element conserva su propio estado
                            // (ej. su fade) en lugar de heredar el del vecino.
                            key(entry.key) {
                                Element(
                                    size = tileSize,
                                    title = entry.title,
                                    subtitle = entry.subtitle,
                                    image = entry.image,
                                    icon = entry.icon,
                                    // Tocar un elemento lo selecciona.
                                    modifier = Modifier.pointerInput(index) {
                                        detectTapGestures { onSelectedIndexChange(index) }
                                    },
                                )
                            }
                        } else {
                            Element(size = tileSize, empty = true)
                        }
                    }
                }
            }
        }

        // ── Cursor ─────────────────────────────────────────────────────────────────────────
        if (count > 0) {
            // Dónde queda el elemento seleccionado: la grilla está centrada en el espacio.
            val localIndex = selected - pageStart
            val gridWidth = tileSize * columns + gap * (columns - 1)
            val gridHeight = tileSize * safeRows + gap * (safeRows - 1)
            val target = DpOffset(
                x = (maxWidth - gridWidth) / 2 + (tileSize + gap) * (localIndex % columns) - CursorOutset,
                y = (maxHeight - gridHeight) / 2 + (tileSize + gap) * (localIndex / columns) - CursorOutset,
            )

            val position = remember { Animatable(target, DpOffset.VectorConverter) }
            var lastPage by remember { mutableIntStateOf(page) }
            LaunchedEffect(target, page) {
                if (page != lastPage) {
                    // Cambió la página: el cursor salta, en vez de cruzar toda la pantalla.
                    lastPage = page
                    position.snapTo(target)
                } else {
                    // Misma página: se desliza hasta el elemento, con un leve rebote.
                    position.animateTo(target, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                }
            }

            Cursor(
                size = tileSize + CursorOutset * 2,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // La posición se lee al ubicar el cursor: la animación no recompone nada.
                    .offset { IntOffset(position.value.x.roundToPx(), position.value.y.roundToPx()) },
            )
        }
    }
}
