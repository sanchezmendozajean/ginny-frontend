package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.ui.theme.GinnyColors
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A tile is allowed to shrink to at most this fraction of the height-driven ideal size in
 * exchange for fitting one more column — below this, the width gain isn't worth the tiles
 * looking noticeably smaller than what the configured row count implies.
 */
private const val WidthFitMinRatio = 0.75f

/**
 * When there's more content than fits in the configured rows, this much of a row's height is
 * always reserved so the row just before/after the visible window peeks in — cropped by the
 * viewport itself, same tiles, not a separate preview — clamped to a sane visible range. The
 * lower bound matters more than it looks: tiles have a 16.dp corner radius (see [FocusableTile]),
 * so a peek shorter than roughly twice that reads as a rounded blob instead of recognizable
 * cropped artwork.
 */
private const val PeekFraction = 0.4f
private val MinPeekHeight = 42.dp
private val MaxPeekHeight = 90.dp

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
    val edgePadding = 6.dp
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val usableHeight = maxHeight - edgePadding * 2
        val usableWidth = maxWidth - edgePadding * 2
        val rows = visibleRows.coerceAtLeast(1)

        // Height alone decides how tall a row can be (so exactly [rows] rows fit). But sizing
        // tiles from height alone can leave a lot of dead width on a wide/short screen once a
        // whole number of columns is fit at that size — see "grid gets disordered" regression
        // notes. So: start from the tallest tile that satisfies the row constraint, then see if
        // shrinking it a bit (never more than [WidthFitMinRatio] of that size, always measured
        // against [minAllowedTileSize] below — the *true* height-driven ideal, not whatever
        // possibly-already-shrunk size a given call starts from, or repeated calls would compound
        // the shrink) lets one more column — or several — fit the width.
        fun fitColumns(startSize: Dp, minAllowedTileSize: Dp): Pair<Int, Dp> {
            var cols = max(1, floor((usableWidth + gap) / (startSize + gap)).toInt())
            var size = startSize
            while (true) {
                val candidateColumns = cols + 1
                val candidateTileSize = (usableWidth - gap * (candidateColumns - 1)) / candidateColumns
                if (candidateTileSize < minAllowedTileSize) break
                cols = candidateColumns
                size = candidateTileSize
            }
            return cols to size
        }

        val naiveHeightTileSize = ((usableHeight - gap * (rows - 1).coerceAtLeast(0)) / rows).coerceAtLeast(1.dp)
        val minAllowedTileSize = naiveHeightTileSize * WidthFitMinRatio
        val (naiveColumns, _) = fitColumns(naiveHeightTileSize, minAllowedTileSize)
        val willScroll = FocusGridMath.rowCount(entries.size, naiveColumns) > rows

        // When there's more content than the [rows] visible ones, always carve out a peek strip
        // above/below (not just whatever's left over from the width fit above) — so scrolling
        // always shows a cropped hint of the previous/next row, not just occasionally when the
        // math happens to leave slack.
        val desiredPeek = if (willScroll) {
            (naiveHeightTileSize * PeekFraction).coerceIn(MinPeekHeight, MaxPeekHeight)
        } else {
            0.dp
        }
        // The reservation itself must never push the per-row height below [minAllowedTileSize] —
        // otherwise fitColumns' own floor-based column guess (not just its "add one more column"
        // loop) ends up starting from a size that's already past the shrink floor, and happily
        // finds even more columns to compensate, snowballing into much smaller tiles than
        // intended. Cap the peek instead of letting that spiral happen.
        val maxPeekWithinFloor = if (desiredPeek > 0.dp) {
            ((usableHeight - minAllowedTileSize * rows - gap * (rows - 1).coerceAtLeast(0)) / 2).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        val peek = minOf(desiredPeek, maxPeekWithinFloor)
        val heightTileSize = if (peek > 0.dp) {
            ((usableHeight - peek * 2 - gap * (rows - 1).coerceAtLeast(0)) / rows).coerceAtLeast(1.dp)
        } else {
            naiveHeightTileSize
        }
        val (columns, tileSize) = fitColumns(heightTileSize, minAllowedTileSize)

        val gridState = rememberLazyGridState()
        val focusRequester = remember { FocusRequester() }

        // Pixel form of the row step/peek, needed to talk to LazyGridState's pixel-based scroll
        // APIs. A single continuous LazyVerticalGrid renders the peeked rows too — they're the
        // same tiles as everywhere else, just clipped by the viewport's own bounds instead of a
        // separate preview — so "peeking" is purely a matter of *where* we scroll to, not extra
        // composables.
        val rowStepPx = with(density) { (tileSize + gap).roundToPx() }.coerceAtLeast(1)
        val peekPx = with(density) { peek.roundToPx() }
        val peekFraction = peekPx.toFloat() / rowStepPx.toFloat()

        // Reads the current (possibly mid-drag) scroll state as "how many rows have we scrolled
        // past" — a continuous value in our own row-based terms, e.g. 3.4 means row 4 is 40% of
        // the way to flush.
        fun currentScrollRows(): Float {
            val cols = columns.coerceAtLeast(1)
            val raw = gridState.firstVisibleItemIndex / cols
            return raw + gridState.firstVisibleItemScrollOffset.toFloat() / rowStepPx.toFloat()
        }

        fun topRowFromScrollRows(scrollRows: Float): Int {
            val estimate = if (peekPx > 0) (scrollRows + peekFraction).roundToInt() else scrollRows.roundToInt()
            return estimate.coerceAtLeast(0)
        }

        // [topRow]'s "ideal" scrollRows value is topRow - peekFraction (see topRowFromScrollRows):
        // that's what leaves exactly [peek] of the row above visible. Getting there in a single
        // animateScrollBy — one continuous motion covering both "move to the row" and "leave the
        // peek showing" at once — instead of an animated scrollToItem followed by a separate
        // instant scrollBy avoids the visible two-step "settles, then jumps" motion that produced
        // (see feedback: "el contenido se mueve" right after each D-Pad press).
        suspend fun scrollToWindow(topRow: Int) {
            val clamped = topRow.coerceAtLeast(0)
            val targetScrollRows = if (peekPx > 0 && clamped > 0) clamped - peekFraction else clamped.toFloat()
            val deltaPx = (targetScrollRows - currentScrollRows()) * rowStepPx
            if (deltaPx != 0f) {
                gridState.animateScrollBy(deltaPx)
            }
            // Debug finding: after landing with a large offset into the first visible row (most of
            // it scrolled past), LazyVerticalGrid's own "how many rows fit" estimate comes up short
            // and leaves the bottom peek's worth of viewport un-composed, even though there's
            // genuinely unused space there (confirmed via gridState.layoutInfo). A no-op nudge
            // forces a fresh measurement pass at the now-settled position, which fills it correctly.
            gridState.scroll { scrollBy(1f); scrollBy(-1f) }
        }

        // animateScrollBy toggles gridState.isScrollInProgress exactly like a real touch drag
        // does. The post-fling-correction effect below only makes sense for an actual
        // finger-driven scroll, so it needs a way to tell the two apart — otherwise it'd treat our
        // own corrective scroll as a user drag that just ended, "correct" it again, and repeat
        // forever (the infinite jitter reported previously). A real drag always emits a
        // DragInteraction.Start through the grid's interactionSource first; our own programmatic
        // scrolls never do, so this flag — set on a genuine drag start, consumed once the
        // resulting settle is handled — reliably distinguishes the two regardless of Compose's
        // recomposition timing.
        val pendingUserScroll = remember { mutableStateOf(false) }
        LaunchedEffect(gridState) {
            gridState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) {
                    pendingUserScroll.value = true
                }
            }
        }

        fun clampedFocus(index: Int) = index.coerceIn(0, (entries.size - 1).coerceAtLeast(0))

        fun move(next: Int) {
            if (entries.isEmpty()) return
            onFocusedIndexChange(clampedFocus(next))
        }

        // Keep the focused row inside the visible window, moving the scroll window the minimum
        // amount necessary — this is what "centrado" means in practice for keyboard/D-Pad nav.
        LaunchedEffect(focusedIndex, columns, visibleRows, entries.size, peekPx) {
            if (entries.isEmpty()) return@LaunchedEffect
            val currentTopRow = topRowFromScrollRows(currentScrollRows())
            val targetTopRow = FocusGridMath.nextTopRow(
                focusedIndex = focusedIndex,
                currentTopRow = currentTopRow,
                visibleRows = visibleRows,
                columns = columns,
                itemCount = entries.size,
            )
            if (targetTopRow != currentTopRow) {
                scrollToWindow(targetTopRow)
            }
        }

        // Post-fling correction: once a touch-driven scroll settles, snap so exactly [visibleRows]
        // full rows are shown (never a partially-cut row) rather than wherever momentum left it —
        // and if that scroll left the focused item outside this window, move focus to the nearest
        // visible row instead of leaving it off-screen (a later D-Pad press would otherwise yank
        // the view straight back to that stale, invisible focus).
        val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
        LaunchedEffect(isScrolling) {
            if (isScrolling || entries.isEmpty() || !pendingUserScroll.value) return@LaunchedEffect
            pendingUserScroll.value = false
            val lastRow = FocusGridMath.rowCount(entries.size, columns) - 1
            val maxTopRow = (lastRow - visibleRows + 1).coerceAtLeast(0)
            val snapRow = topRowFromScrollRows(currentScrollRows()).coerceIn(0, maxTopRow)
            scrollToWindow(snapRow)
            val visibleFocus = FocusGridMath.clampRowToWindow(focusedIndex, snapRow, visibleRows, columns, entries.size)
            if (visibleFocus != focusedIndex) {
                onFocusedIndexChange(visibleFocus)
            }
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // Center the grid's own content block within the panel: horizontally there's near-always
        // some leftover width even after the above widening pass; vertically too whenever there
        // are fewer full rows of items than [rows] (nothing to peek at either, in that case).
        val actualRows = FocusGridMath.rowCount(entries.size, columns)
        val needsScroll = actualRows > rows
        val rowsToShow = if (needsScroll) rows else actualRows.coerceAtLeast(1)
        val effectivePeek = if (needsScroll) peek else 0.dp
        val gridWidth = tileSize * columns + gap * (columns - 1).coerceAtLeast(0) + edgePadding * 2
        val gridContentHeight = tileSize * rowsToShow + gap * (rowsToShow - 1).coerceAtLeast(0)
        val panelMaxHeight = maxHeight
        val gridHeight = (gridContentHeight + effectivePeek * 2 + edgePadding * 2).coerceAtMost(panelMaxHeight)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Debug finding: when the first visible item is scrolled deep into its own bounds
            // (our peek anchor scrolls ~75% of it past), LazyVerticalGrid's own "how many rows
            // does the viewport need" estimate comes up one row short of what's actually needed
            // to fill it — confirmed via gridState.layoutInfo, and unrelated to contentPadding
            // (tested). Work around it by requesting one extra row of height (so the grid composes
            // enough to cover the shortfall) and clipping back down to the real [gridHeight] with
            // an outer Box, rather than trusting the grid's own height to drive composition 1:1.
            Box(
                modifier = Modifier
                    .width(gridWidth)
                    .height(gridHeight)
                    .clipToBounds(),
            ) {
                LazyVerticalGrid(
                    // Fixed(columns), not FixedSize(tileSize) — the latter lets Compose independently
                    // decide the column count from its own pixel-rounded math, which can disagree by
                    // one with the [columns] value the D-Pad navigation below assumes (Dp arithmetic
                    // here vs. actual pixel layout there), making "move down" land one item off from
                    // where it visually should. Fixed(columns) makes the two impossible to disagree.
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    userScrollEnabled = true,
                    contentPadding = PaddingValues(horizontal = edgePadding, vertical = edgePadding + effectivePeek),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(gridWidth)
                        .height(gridHeight + tileSize + gap)
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

            // TEMPORARY debug overlay — remove once the asymmetric top/bottom peek is diagnosed.
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            val lastVisible = visibleItems.lastOrNull()
            androidx.compose.material3.Text(
                text = "rows=$rows cols=$columns tile=${tileSize.value.toInt()} peek=${peek.value.toInt()} " +
                    "effPeek=${effectivePeek.value.toInt()} gridH=${gridHeight.value.toInt()} " +
                    "maxH=${panelMaxHeight.value.toInt()} contentH=${gridContentHeight.value.toInt()} " +
                    "topRow=${topRowFromScrollRows(currentScrollRows())}\n" +
                    "rowStepPx=$rowStepPx peekPx=$peekPx firstIdx=${gridState.firstVisibleItemIndex} " +
                    "firstOff=${gridState.firstVisibleItemScrollOffset} actualRows=$actualRows " +
                    "entries=${entries.size} edgePadPx=${with(density) { edgePadding.roundToPx() } }\n" +
                    "viewportH=${gridState.layoutInfo.viewportSize.height} visibleCount=${visibleItems.size} " +
                    "lastIdx=${lastVisible?.index} lastOffY=${lastVisible?.offset?.y} lastSizeH=${lastVisible?.size?.height} " +
                    "beforeContentPad=${gridState.layoutInfo.beforeContentPadding} afterContentPad=${gridState.layoutInfo.afterContentPadding}",
                color = androidx.compose.ui.graphics.Color.Red,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(2.dp),
            )
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
