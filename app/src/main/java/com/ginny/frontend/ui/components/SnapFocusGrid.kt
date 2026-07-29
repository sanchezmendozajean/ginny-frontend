package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ginny.frontend.ui.theme.GinnyColors
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

        // Rounds up to the next row whenever the grid is mid-scroll into one (any nonzero
        // firstVisibleItemScrollOffset) rather than truncating down to the row that's only
        // partially out of view — used by both effects below so they agree on "current top row"
        // whether or not a touch scroll has settled yet. Without this, the D-Pad-driven effect
        // (which used to read firstVisibleItemIndex directly) could disagree with the settled
        // post-scroll snap by one row, producing a visible extra shift ("push") on the next D-Pad
        // press right after a touch scroll.
        fun currentTopRowRounded(): Int {
            val cols = columns.coerceAtLeast(1)
            val approxRow = gridState.firstVisibleItemIndex / cols
            return if (gridState.firstVisibleItemScrollOffset > 0) approxRow + 1 else approxRow
        }

        // Keep the focused row inside the visible window, moving the scroll window the minimum
        // amount necessary — this is what "centrado" means in practice for keyboard/D-Pad nav.
        LaunchedEffect(focusedIndex, columns, visibleRows, entries.size) {
            if (entries.isEmpty()) return@LaunchedEffect
            val currentTopRow = currentTopRowRounded()
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
        // full rows are shown (never a partially-cut row) rather than wherever momentum left it —
        // and if that scroll left the focused item outside this window, move focus to the nearest
        // visible row instead of leaving it off-screen (a later D-Pad press would otherwise yank
        // the view straight back to that stale, invisible focus).
        val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
        LaunchedEffect(isScrolling) {
            if (isScrolling || entries.isEmpty()) return@LaunchedEffect
            val lastRow = FocusGridMath.rowCount(entries.size, columns) - 1
            val maxTopRow = (lastRow - visibleRows + 1).coerceAtLeast(0)
            val snapRow = currentTopRowRounded().coerceIn(0, maxTopRow)
            if (snapRow * columns != gridState.firstVisibleItemIndex || gridState.firstVisibleItemScrollOffset != 0) {
                gridState.animateScrollToItem(snapRow * columns)
            }
            val visibleFocus = FocusGridMath.clampRowToWindow(focusedIndex, snapRow, visibleRows, columns, entries.size)
            if (visibleFocus != focusedIndex) {
                onFocusedIndexChange(visibleFocus)
            }
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // Center the grid's own content block within the panel: horizontally there's near-always
        // leftover width once a whole number of columns is fit; vertically too whenever there are
        // fewer full rows of items than [visibleRows] (no scrolling needed in that case either).
        val actualRows = FocusGridMath.rowCount(entries.size, columns)
        val needsScroll = actualRows > visibleRows
        val gridWidth = tileSize * columns + gap * (columns - 1).coerceAtLeast(0) + edgePadding * 2
        // Always exactly [rowsToShow] rows tall — never the full panel — so only the configured
        // row count is ever visible, even mid-drag before a touch scroll settles and snaps back.
        val rowsToShow = if (needsScroll) visibleRows else actualRows.coerceAtLeast(1)
        val gridHeight = (tileSize * rowsToShow + gap * (rowsToShow - 1).coerceAtLeast(0) + edgePadding * 2)
            .coerceAtMost(maxHeight)

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

            if (needsScroll) {
                // Vertical inset is independent of edgePadding (which sizes tiles) — it just needs
                // to clear the GridPanel's own rounded corners (GinnyShapes.radiusLarge) so the
                // track doesn't visually poke into the curve.
                RowPositionIndicator(
                    gridState = gridState,
                    columns = columns,
                    totalRows = actualRows,
                    visibleRows = visibleRows,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 24.dp),
                )
            }
        }
    }
}

/**
 * Visual-only (non-touch) indicator of which row window is currently in view — per spec, a
 * scrollbar-like affordance for the D-Pad-driven grid, which has no draggable/touch scrollbar.
 */
@Composable
private fun RowPositionIndicator(
    gridState: LazyGridState,
    columns: Int,
    totalRows: Int,
    visibleRows: Int,
    modifier: Modifier = Modifier,
) {
    val safeColumns = columns.coerceAtLeast(1)
    val safeTotalRows = totalRows.coerceAtLeast(1)
    val firstVisibleRow = gridState.firstVisibleItemIndex / safeColumns
    val maxTopRow = (safeTotalRows - visibleRows).coerceAtLeast(1)
    val thumbFraction = (visibleRows.toFloat() / safeTotalRows.toFloat()).coerceIn(0.08f, 1f)
    val progress = (firstVisibleRow.toFloat() / maxTopRow.toFloat()).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .width(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(GinnyColors.TextPrimary.copy(alpha = 0.15f)),
    ) {
        val thumbHeight = maxHeight * thumbFraction
        Box(
            modifier = Modifier
                .offset(y = (maxHeight - thumbHeight) * progress)
                .width(4.dp)
                .height(thumbHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(GinnyColors.AccentBlue),
        )
    }
}
