package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import kotlin.math.floor
import kotlin.math.max

/**
 * Reusable grid used by the systems, games and save-states screens alike: square tiles, N
 * configurable visible rows, vertical scroll with a centered/kept-in-view snap, and D-Pad
 * navigation (Up/Down/Left/Right, L/R page jumps). Items must already be sorted by the caller.
 */
@Composable
fun <T> SnapFocusGrid(
    entries: List<T>,
    visibleRows: Int,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    onActivate: (T) -> Unit,
    buttonMapping: ButtonMapping,
    modifier: Modifier = Modifier,
    gap: Dp = 10.dp,
    itemKey: (T) -> Any,
    itemContent: @Composable (item: T, focused: Boolean, tileSize: Dp) -> Unit,
) {
    // Reserved so a focused edge tile's outward glow (see FocusableTile's shadow) has room to
    // render before hitting the LazyVerticalGrid's own scroll-clipping bounds — without this,
    // the glow gets hard-cut in a visible rectangle instead of fading out.
    val edgePadding = 10.dp

    BoxWithConstraints(modifier = modifier) {
        val usableHeight = maxHeight - edgePadding * 2
        val usableWidth = maxWidth - edgePadding * 2
        val tileSize = ((usableHeight - gap * (visibleRows - 1).coerceAtLeast(0)) / visibleRows.coerceAtLeast(1))
            .coerceAtLeast(1.dp)
        val columns = max(1, floor((usableWidth + gap) / (tileSize + gap)).toInt())

        val gridState = rememberLazyGridState()
        val focusRequester = remember { FocusRequester() }

        fun clampedFocus(index: Int) = index.coerceIn(0, (entries.size - 1).coerceAtLeast(0))

        fun move(next: Int) {
            if (entries.isEmpty()) return
            onFocusedIndexChange(clampedFocus(next))
        }

        // Keep the focused row inside the visible window, moving the scroll window the minimum
        // amount necessary — this is what "centrado" means in practice for keyboard/D-Pad nav.
        LaunchedEffect(focusedIndex, columns, visibleRows, entries.size) {
            if (entries.isEmpty()) return@LaunchedEffect
            val currentTopRow = gridState.firstVisibleItemIndex / columns.coerceAtLeast(1)
            val targetTopRow = FocusGridMath.nextTopRow(
                focusedIndex = focusedIndex,
                currentTopRow = currentTopRow,
                visibleRows = visibleRows,
                columns = columns,
                itemCount = entries.size,
            )
            if (targetTopRow != currentTopRow || gridState.firstVisibleItemScrollOffset != 0) {
                gridState.animateScrollToItem(targetTopRow * columns)
            }
        }

        // Post-fling correction: once a touch-driven scroll settles, snap so exactly [visibleRows]
        // full rows are shown (never a partially-cut row) rather than wherever momentum left it.
        val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
        LaunchedEffect(isScrolling) {
            if (isScrolling || entries.isEmpty()) return@LaunchedEffect
            val approxRow = gridState.firstVisibleItemIndex / columns.coerceAtLeast(1)
            val roundedRow = if (gridState.firstVisibleItemScrollOffset > 0) approxRow + 1 else approxRow
            val lastRow = FocusGridMath.rowCount(entries.size, columns) - 1
            val maxTopRow = (lastRow - visibleRows + 1).coerceAtLeast(0)
            val snapRow = roundedRow.coerceIn(0, maxTopRow)
            if (snapRow * columns != gridState.firstVisibleItemIndex || gridState.firstVisibleItemScrollOffset != 0) {
                gridState.animateScrollToItem(snapRow * columns)
            }
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // Center the grid's own content block within the panel: horizontally there's near-always
        // leftover width once a whole number of columns is fit; vertically too whenever there are
        // fewer full rows of items than [visibleRows] (no scrolling needed in that case either).
        val actualRows = FocusGridMath.rowCount(entries.size, columns)
        val needsScroll = actualRows > visibleRows
        val gridWidth = tileSize * columns + gap * (columns - 1).coerceAtLeast(0) + edgePadding * 2
        val gridHeight = if (needsScroll) {
            maxHeight
        } else {
            (tileSize * actualRows.coerceAtLeast(1) + gap * (actualRows - 1).coerceAtLeast(0) + edgePadding * 2)
                .coerceAtMost(maxHeight)
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LazyVerticalGrid(
                columns = GridCells.FixedSize(tileSize),
                state = gridState,
                userScrollEnabled = true,
                contentPadding = PaddingValues(horizontal = edgePadding, vertical = edgePadding),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .width(gridWidth)
                    .height(gridHeight)
                    .focusRequester(focusRequester)
                    .focusTarget()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val code = keyEvent.nativeKeyEvent.keyCode
                        when (buttonMapping.buttonForKeyCode(code)) {
                            LogicalButton.DPAD_UP -> {
                                move(FocusGridMath.moveUp(focusedIndex, columns, entries.size)); true
                            }
                            LogicalButton.DPAD_DOWN -> {
                                move(FocusGridMath.moveDown(focusedIndex, columns, entries.size)); true
                            }
                            LogicalButton.DPAD_LEFT -> {
                                move(FocusGridMath.moveLeft(focusedIndex, columns, entries.size)); true
                            }
                            LogicalButton.DPAD_RIGHT -> {
                                move(FocusGridMath.moveRight(focusedIndex, columns, entries.size)); true
                            }
                            LogicalButton.L -> {
                                move(FocusGridMath.jumpRows(focusedIndex, -visibleRows, columns, entries.size)); true
                            }
                            LogicalButton.R -> {
                                move(FocusGridMath.jumpRows(focusedIndex, visibleRows, columns, entries.size)); true
                            }
                            LogicalButton.A -> {
                                entries.getOrNull(focusedIndex)?.let(onActivate); true
                            }
                            else -> false
                        }
                    },
            ) {
                items(entries.size, key = { itemKey(entries[it]) }) { index ->
                    itemContent(entries[index], index == focusedIndex, tileSize)
                }
            }
        }
    }
}
