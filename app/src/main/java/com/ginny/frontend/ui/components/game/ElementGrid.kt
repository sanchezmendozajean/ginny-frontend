package com.ginny.frontend.ui.components.game

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import kotlin.math.roundToInt
import kotlin.math.max
import kotlinx.coroutines.launch

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

/** Cuánto avanza el cursor al rebotar contra un borde (no hay a dónde moverse). */
val CursorBumpDistance = 8.dp

/** Duración del avance del rebote; la vuelta es un resorte con un leve temblor. */
const val CursorBumpOutMs = 70

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
    active: Boolean = true,
    onLayoutChanged: (GridLayout) -> Unit = {},
) {
    // Para que el teclado llegue a la grilla, la grilla tiene que tener el foco. Lo pide al
    // aparecer y cada vez que vuelve a estar activa (ej. al cerrarse un modal que se lo quitó).
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(active) { if (active) focusRequester.requestFocus() }

    // BoxWithConstraints: da el tamaño disponible (maxWidth / maxHeight) para calcular el lado.
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val safeRows = rows.coerceIn(1, 4)
        // Lado y separación en píxeles enteros. Si quedaran fraccionarios, cada elemento y cada
        // separación se redondearían por su lado al dibujarse, y el cursor (calculado con el
        // valor exacto) se correría unos píxeles del elemento.
        val density = LocalDensity.current
        val gapPx = with(density) { gap.roundToPx() }
        val tilePx = with(density) { ((maxHeight.toPx() - gapPx * (safeRows - 1)) / safeRows).toInt() }
        val tileSize = with(density) { tilePx.toDp() }
        val gridGap = with(density) { gapPx.toDp() }
        val columns = max(1, (constraints.maxWidth + gapPx) / (tilePx + gapPx))
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

        // Rebote del cursor cuando se intenta ir a donde no se puede (borde de la grilla, primera
        // o última página): avanza un poco en esa dirección y vuelve con un leve temblor.
        // [bumpDirection] (-1/0/1 por eje) y [bump] (0…1) se leen al ubicar el cursor: no recomponen.
        val scope = rememberCoroutineScope()
        val bump = remember { Animatable(0f) }
        var bumpDirection by remember { mutableStateOf(IntOffset.Zero) }
        fun bumpTowards(dx: Int, dy: Int) {
            bumpDirection = IntOffset(dx, dy)
            scope.launch {
                bump.snapTo(0f)
                bump.animateTo(1f, tween(CursorBumpOutMs))
                bump.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium))
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(gridGap),
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (!active || event.type != KeyEventType.KeyDown || count == 0) return@onKeyEvent false
                    val (i, g, mapping) = current
                    val button = mapping.buttonForKeyCode(event.nativeKeyEvent.keyCode)
                    val target = when (button) {
                        LogicalButton.DPAD_UP -> GridNavigation.up(i, g, count)
                        LogicalButton.DPAD_DOWN -> GridNavigation.down(i, g, count)
                        LogicalButton.DPAD_LEFT -> GridNavigation.left(i, g, count)
                        LogicalButton.DPAD_RIGHT -> GridNavigation.right(i, g, count)
                        LogicalButton.L -> GridNavigation.previousPage(i, g, count)
                        LogicalButton.R -> GridNavigation.nextPage(i, g, count)
                        else -> return@onKeyEvent false // A, B, Y, Menú: no son de la grilla
                    }
                    if (target != i) {
                        onSelectedIndexChange(target)
                    } else when (button) {
                        // No hay a dónde ir: el cursor rebota hacia ese lado.
                        LogicalButton.DPAD_UP -> bumpTowards(0, -1)
                        LogicalButton.DPAD_DOWN -> bumpTowards(0, 1)
                        LogicalButton.DPAD_LEFT, LogicalButton.L -> bumpTowards(-1, 0)
                        else -> bumpTowards(1, 0) // derecha o R
                    }
                    true
                },
        ) {
            for (row in 0 until safeRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gridGap)) {
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
            // Dónde queda el elemento seleccionado, en píxeles enteros y redondeando igual que el
            // centrado de la Box (Alignment.Center), para caer exactamente sobre el elemento.
            val localIndex = selected - pageStart
            val outsetPx = with(density) { CursorOutset.roundToPx() }
            val gridWidthPx = tilePx * columns + gapPx * (columns - 1)
            val gridHeightPx = tilePx * safeRows + gapPx * (safeRows - 1)
            val originX = ((constraints.maxWidth - gridWidthPx) / 2f).roundToInt()
            val originY = ((constraints.maxHeight - gridHeightPx) / 2f).roundToInt()
            val target = with(density) {
                DpOffset(
                    x = (originX + (tilePx + gapPx) * (localIndex % columns) - outsetPx).toDp(),
                    y = (originY + (tilePx + gapPx) * (localIndex / columns) - outsetPx).toDp(),
                )
            }

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
                size = with(density) { (tilePx + outsetPx * 2).toDp() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // La posición se lee al ubicar el cursor: la animación no recompone nada.
                    .offset {
                        val push = CursorBumpDistance.toPx() * bump.value
                        IntOffset(
                            (position.value.x.toPx() + bumpDirection.x * push).roundToInt(),
                            (position.value.y.toPx() + bumpDirection.y * push).roundToInt(),
                        )
                    },
            )
        }
    }
}
