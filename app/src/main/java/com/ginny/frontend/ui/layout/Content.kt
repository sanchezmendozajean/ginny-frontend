package com.ginny.frontend.ui.layout

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.ui.components.Container
import com.ginny.frontend.ui.components.game.ArrowDirection
import com.ginny.frontend.ui.components.game.ElementGrid
import com.ginny.frontend.ui.components.game.GridEntry
import com.ginny.frontend.ui.components.game.GridLayout
import com.ginny.frontend.ui.components.game.GridNavigation
import com.ginny.frontend.ui.components.game.PageArrow
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.ui.util.LocalSoundManager

/**
 * Panel central: flecha izquierda · grilla de elementos · flecha derecha. Lo usan todas las
 * pantallas (Sistemas, Juegos…); cada una le entrega sus [entries].
 *
 * Las flechas se ven semitransparentes cuando no hay página hacia ese lado; tocarlas cambia de
 * página.
 *
 * - [selectedIndex] / [onSelectedIndexChange]: la selección la guarda la pantalla (en su
 *   ViewModel, regla del spec). Aquí solo se suena el efecto de foco al cambiarla.
 * - [onActivate]: A o tocar el elemento seleccionado (suena el efecto de activar).
 * - [loading] = true: todavía no se sabe qué mostrar; queda el espacio vacío, así los elementos
 *   no aparecen a medias (ej. sin logo) para completarse un instante después.
 * - [onSelectionChanged] y [onPagingChanged] informan hacia afuera el elemento seleccionado y las
 *   páginas (total, actual), para que el footer los muestre.
 * - [active] = false (ej. con Configuración abierta): la grilla no responde a la cruceta; al volver
 *   a true recupera el foco.
 */
@Composable
fun Content(
    entries: List<GridEntry>,
    rows: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    buttonMapping: ButtonMapping,
    modifier: Modifier = Modifier,
    onActivate: (GridEntry) -> Unit = {},
    loading: Boolean = false,
    active: Boolean = true,
    onSelectionChanged: (GridEntry?) -> Unit = {},
    onPagingChanged: (pageCount: Int, currentPage: Int) -> Unit = { _, _ -> },
    /** Sube cada vez que se toca "Abrir" en el pie: abre el elemento seleccionado, como A. */
    activateTick: Int = 0,
    /** Efecto al activar un elemento (Sistemas: entrar; Juegos: abrir su modal). */
    activateSound: (SoundManager) -> Unit = { it.playActivateSound() },
) {
    val sound = LocalSoundManager.current

    // Forma de la grilla (la informa ElementGrid al medirse); null hasta entonces.
    var layout by remember { mutableStateOf<GridLayout?>(null) }

    val selected = selectedIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    val currentLayout = layout
    val pageCount = currentLayout?.pageCount(entries.size) ?: 1
    val currentPage = currentLayout?.pageOf(selected) ?: 0

    /** Cambia la selección y suena el efecto de foco (regla del spec). */
    fun select(index: Int) {
        if (index == selected) return
        onSelectedIndexChange(index)
        sound?.playFocusSound()
    }

    // Avisa hacia afuera (fuera de la composición) cuando cambian la selección o las páginas.
    val selectedEntry = if (loading) null else entries.getOrNull(selected)
    LaunchedEffect(selectedEntry) { onSelectionChanged(selectedEntry) }
    LaunchedEffect(pageCount, currentPage) { onPagingChanged(pageCount, currentPage) }

    // "Abrir" tocado en el pie: activa el elemento seleccionado. El valor con el que nace esta pantalla
    // no cuenta (una pantalla nueva no debe abrir nada sola).
    val firstTick = remember { activateTick }
    val currentSelected by rememberUpdatedState(selectedEntry)
    LaunchedEffect(activateTick) {
        if (activateTick != firstTick) {
            currentSelected?.let {
                sound?.let(activateSound)
                onActivate(it)
            }
        }
    }

    Container(modifier = modifier.fillMaxWidth()) {
        PageArrow(
            ArrowDirection.Left,
            enabled = currentPage > 0,
            onClick = { currentLayout?.let { select(GridNavigation.previousPage(selected, it, entries.size)) } },
        )

        if (!loading) {
            ElementGrid(
                entries = entries,
                rows = rows,
                selectedIndex = selected,
                onSelectedIndexChange = ::select,
                onActivate = { index ->
                    entries.getOrNull(index)?.let {
                        sound?.let(activateSound)
                        onActivate(it)
                    }
                },
                buttonMapping = buttonMapping,
                active = active,
                modifier = Modifier
                    .weight(1f)                 // ocupa todo el ancho entre las dos flechas
                    .fillMaxHeight()
                    .padding(vertical = 16.dp), // aire arriba y abajo (también para el cursor)
                onLayoutChanged = { layout = it },
            )
        } else {
            // Mientras tanto, el mismo espacio vacío, para que las flechas no salten de lugar.
            Spacer(Modifier.weight(1f))
        }

        PageArrow(
            ArrowDirection.Right,
            enabled = currentPage < pageCount - 1,
            onClick = { currentLayout?.let { select(GridNavigation.nextPage(selected, it, entries.size)) } },
        )
    }
}

/** "1 juego" / "N juegos". */
fun gamesLabel(count: Int): String = if (count == 1) "1 juego" else "$count juegos"
