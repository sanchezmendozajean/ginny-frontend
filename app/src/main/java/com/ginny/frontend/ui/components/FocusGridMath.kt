package com.ginny.frontend.ui.components

/**
 * Pure index arithmetic for D-Pad navigation over a row-major grid of [itemCount] items laid out
 * in [columns] columns. Kept free of Compose so the navigation rules can be reasoned about (and
 * unit-tested) in isolation from layout/animation concerns.
 *
 * Design decisions (left open by the spec):
 * - Left/Right does NOT wrap to the previous/next row — it clamps at the row's edges.
 * - Up/Down/L/R try to keep the same column, clamping to the last item of a shorter target row.
 */
object FocusGridMath {

    fun rowOf(index: Int, columns: Int): Int = index / columns
    fun columnOf(index: Int, columns: Int): Int = index % columns
    fun rowCount(itemCount: Int, columns: Int): Int = if (itemCount == 0) 0 else (itemCount - 1) / columns + 1

    private fun clampToRow(targetRow: Int, desiredColumn: Int, columns: Int, itemCount: Int): Int {
        val lastRow = rowCount(itemCount, columns) - 1
        val row = targetRow.coerceIn(0, lastRow)
        val rowStart = row * columns
        val rowEnd = minOf(rowStart + columns - 1, itemCount - 1)
        val column = desiredColumn.coerceIn(0, rowEnd - rowStart)
        return rowStart + column
    }

    fun moveUp(current: Int, columns: Int, itemCount: Int): Int {
        if (itemCount == 0) return current
        val row = rowOf(current, columns)
        if (row == 0) return current
        return clampToRow(row - 1, columnOf(current, columns), columns, itemCount)
    }

    fun moveDown(current: Int, columns: Int, itemCount: Int): Int {
        if (itemCount == 0) return current
        val row = rowOf(current, columns)
        val lastRow = rowCount(itemCount, columns) - 1
        if (row >= lastRow) return current
        return clampToRow(row + 1, columnOf(current, columns), columns, itemCount)
    }

    fun moveLeft(current: Int, columns: Int, itemCount: Int): Int {
        if (itemCount == 0) return current
        val row = rowOf(current, columns)
        val rowStart = row * columns
        return if (current > rowStart) current - 1 else current
    }

    fun moveRight(current: Int, columns: Int, itemCount: Int): Int {
        if (itemCount == 0) return current
        val row = rowOf(current, columns)
        val rowStart = row * columns
        val rowEnd = minOf(rowStart + columns - 1, itemCount - 1)
        return if (current < rowEnd) current + 1 else current
    }

    /** Used by both L/R (jump [rowsToJump] rows) with [rowsToJump] negative for a backward jump. */
    fun jumpRows(current: Int, rowsToJump: Int, columns: Int, itemCount: Int): Int {
        if (itemCount == 0) return current
        val targetRow = rowOf(current, columns) + rowsToJump
        return clampToRow(targetRow, columnOf(current, columns), columns, itemCount)
    }

    /**
     * If [current]'s row falls outside [windowTopRow, windowTopRow + visibleRows - 1], moves it
     * to the nearest edge of that window (same column where possible) — used after a touch-driven
     * scroll settles, so the focused item is always one of the rows the user can actually see,
     * instead of silently staying off-screen until the next D-Pad press yanks the view back to it.
     */
    fun clampRowToWindow(current: Int, windowTopRow: Int, visibleRows: Int, columns: Int, itemCount: Int): Int {
        if (itemCount == 0) return current
        val row = rowOf(current, columns)
        val windowBottomRow = windowTopRow + visibleRows - 1
        if (row in windowTopRow..windowBottomRow) return current
        val targetRow = row.coerceIn(windowTopRow, windowBottomRow)
        return clampToRow(targetRow, columnOf(current, columns), columns, itemCount)
    }

    /**
     * Smallest top-row index (0-based) such that scrolling the grid so this row is first keeps
     * [focusedIndex]'s row fully within the next [visibleRows] rows, moving the window by the
     * minimum amount necessary from [currentTopRow] (never further than needed).
     */
    fun nextTopRow(focusedIndex: Int, currentTopRow: Int, visibleRows: Int, columns: Int, itemCount: Int): Int {
        val focusedRow = rowOf(focusedIndex, columns)
        val lastRow = rowCount(itemCount, columns) - 1
        val maxTopRow = (lastRow - visibleRows + 1).coerceAtLeast(0)
        return when {
            focusedRow < currentTopRow -> focusedRow
            focusedRow > currentTopRow + visibleRows - 1 -> focusedRow - visibleRows + 1
            else -> currentTopRow
        }.coerceIn(0, maxTopRow)
    }
}
