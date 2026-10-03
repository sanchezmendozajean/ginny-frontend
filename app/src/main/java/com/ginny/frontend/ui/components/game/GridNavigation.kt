package com.ginny.frontend.ui.components.game

/**
 * Forma de la grilla: cuántas columnas y filas entran por página. La calcula [ElementGrid] según
 * el espacio disponible.
 */
data class GridLayout(val columns: Int, val rows: Int) {
    val perPage: Int get() = columns * rows

    fun pageCount(itemCount: Int): Int = maxOf(1, (itemCount + perPage - 1) / perPage)

    /** Página en la que está el elemento [index]. */
    fun pageOf(index: Int): Int = index / perPage
}

/**
 * Hacia dónde se mueve la selección con cada dirección. Solo aritmética (sin Compose), para poder
 * razonar las reglas por separado del dibujo.
 *
 * Los elementos se reparten página por página; dentro de cada página, fila por fila:
 * página 0 = índices 0 … perPage−1, página 1 = perPage … 2·perPage−1, etc.
 *
 * - Arriba / Abajo: fila anterior / siguiente de la misma página, misma columna. En el borde no
 *   se mueve. Si en la fila de abajo no hay elemento en esa columna, va al último de esa fila.
 * - Izquierda / Derecha: elemento anterior / siguiente de la fila. Desde la primera / última
 *   columna pasa a la página anterior / siguiente, en la misma fila (como las flechas laterales).
 * - L / R: página anterior / siguiente, en la misma posición (o la más cercana que exista).
 */
object GridNavigation {

    private fun pageOf(i: Int, g: GridLayout) = i / g.perPage
    private fun rowOf(i: Int, g: GridLayout) = (i % g.perPage) / g.columns
    private fun colOf(i: Int, g: GridLayout) = (i % g.perPage) % g.columns
    private fun indexAt(page: Int, row: Int, col: Int, g: GridLayout) = page * g.perPage + row * g.columns + col

    fun up(i: Int, g: GridLayout, count: Int): Int =
        if (rowOf(i, g) > 0) i - g.columns else i

    fun down(i: Int, g: GridLayout, count: Int): Int {
        val row = rowOf(i, g)
        if (row >= g.rows - 1) return i                                 // ya en la última fila
        val rowStart = indexAt(pageOf(i, g), row + 1, 0, g)
        if (rowStart >= count) return i                                 // la fila de abajo está vacía
        return minOf(i + g.columns, count - 1)                          // misma columna, o la última que exista
    }

    fun left(i: Int, g: GridLayout, count: Int): Int {
        val page = pageOf(i, g)
        return when {
            colOf(i, g) > 0 -> i - 1
            page > 0 -> indexAt(page - 1, rowOf(i, g), g.columns - 1, g) // última columna de la página anterior
            else -> i
        }
    }

    fun right(i: Int, g: GridLayout, count: Int): Int {
        val page = pageOf(i, g)
        return when {
            colOf(i, g) < g.columns - 1 -> if (i + 1 < count) i + 1 else i
            (page + 1) * g.perPage < count ->                            // existe la página siguiente
                minOf(indexAt(page + 1, rowOf(i, g), 0, g), count - 1)
            else -> i
        }
    }

    fun previousPage(i: Int, g: GridLayout, count: Int): Int =
        if (pageOf(i, g) > 0) i - g.perPage else i

    fun nextPage(i: Int, g: GridLayout, count: Int): Int =
        if ((pageOf(i, g) + 1) * g.perPage < count) minOf(i + g.perPage, count - 1) else i
}
