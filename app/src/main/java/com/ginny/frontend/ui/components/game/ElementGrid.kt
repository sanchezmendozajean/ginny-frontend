package com.ginny.frontend.ui.components.game

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max

/** Lo que la grilla necesita saber de cada elemento, venga de un sistema, juego o estado guardado. */
data class GridEntry(
    val key: String,       // identidad estable (ej. el id del sistema): Compose la usa para no confundirlos
    val title: String,
    val subtitle: String = "",
    val image: Uri? = null,
)

/**
 * Separación entre elementos. Deja lugar al cursor, que es más grande que el elemento y
 * sobresale ~10 dp por lado.
 */
val ElementGridGap = 8.dp

/**
 * Una página de la grilla: [rows] filas de elementos cuadrados, con todas las columnas que entren.
 *
 * Reglas del spec:
 * - Lado del elemento = (alto disponible − separaciones) ÷ filas → siempre caben exactamente
 *   [rows] filas, sin cortar ninguna.
 * - Columnas = las que entren en el ancho con ese lado.
 * - Elementos por página = columnas × filas. Si la página no se llena, el resto son huecos
 *   ([Element] con empty = true), para que la grilla conserve su forma.
 * - El orden es el de [entries] (la pantalla lo entrega ya ordenado alfabéticamente).
 *
 * [onPageCountChanged] informa cuántas páginas hay (cambia si cambia el tamaño o las filas), para
 * las flechas y los puntos de página.
 */
@Composable
fun ElementGrid(
    entries: List<GridEntry>,
    rows: Int,
    page: Int,
    modifier: Modifier = Modifier,
    gap: Dp = ElementGridGap,
    onPageCountChanged: (Int) -> Unit = {},
) {
    // BoxWithConstraints: da el tamaño disponible (maxWidth / maxHeight) para calcular el lado.
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val safeRows = rows.coerceIn(1, 4)
        val tileSize = (maxHeight - gap * (safeRows - 1)) / safeRows
        val columns = max(1, floor((maxWidth + gap) / (tileSize + gap)).toInt())
        val perPage = columns * safeRows
        val pageCount = max(1, (entries.size + perPage - 1) / perPage) // división redondeando hacia arriba

        // Se informa fuera de la composición (efecto), nunca mientras se dibuja.
        LaunchedEffect(pageCount) { onPageCountChanged(pageCount) }

        val pageEntries = entries.drop(page * perPage).take(perPage)

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until safeRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until columns) {
                        val entry = pageEntries.getOrNull(row * columns + col)
                        if (entry != null) {
                            // key: si los datos cambian, cada Element conserva su propio estado
                            // (ej. su fade) en lugar de heredar el del vecino.
                            key(entry.key) {
                                Element(
                                    size = tileSize,
                                    title = entry.title,
                                    subtitle = entry.subtitle,
                                    image = entry.image,
                                )
                            }
                        } else {
                            Element(size = tileSize, empty = true)
                        }
                    }
                }
            }
        }
    }
}
