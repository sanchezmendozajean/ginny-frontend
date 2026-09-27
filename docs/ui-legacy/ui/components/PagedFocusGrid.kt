package com.ginny.frontend.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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

/** Snapshot of [PagedFocusGrid]'s paging state, handed up via `onPagingChanged` so the owning
 * screen can render page dots ([com.ginny.frontend.ui.components.GinnyBottomBar]) and jump pages
 * from outside the grid (e.g. a dot tap) without needing to know `columns` itself. */
data class PagingInfo(val page: Int, val count: Int, val goTo: (Int) -> Unit)

/**
 * Grid used by the systems/games/save-states screens alike: square tiles, N configurable visible
 * rows, laid out in discrete same-size PAGES that slide horizontally (not the old continuous
 * vertical scroll — see docs/diseño and docs/architecture.md's paging note). Built on Compose
 * Foundation's [HorizontalPager] (with swipe disabled — paging here is D-Pad/L-R/dot-driven, not a
 * touch gesture) rather than a hand-rolled `Row` + `Modifier.offset`: the latter had to fight the
 * pager's own "only compose nearby pages, correctly centered" bookkeeping and was landing later
 * pages fully off-screen in practice. D-Pad Up/Down/Left/Right moves focus one cell at a time (no
 * row/column wrap); L/R (and the on-screen [PageJumpButton]s this renders at the edges) jump a
 * whole page. Items must already be sorted by the caller.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> PagedFocusGrid(
    entries: List<T>,
    visibleRows: Int,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    onActivate: (T) -> Unit,
    buttonMapping: ButtonMapping,
    modifier: Modifier = Modifier,
    gap: Dp = 14.dp,
    onSecondaryAction: ((T) -> Unit)? = null,
    onPagingChanged: (currentPage: Int, pageCount: Int, goToPage: (Int) -> Unit) -> Unit = { _, _, _ -> },
    itemContent: @Composable (item: T, index: Int, focused: Boolean, tileSize: Dp) -> Unit,
) {
    val edgePadding = 10.dp
    val focusRequester = remember { FocusRequester() }

    BoxWithConstraints(modifier = modifier) {
        val usableHeight = maxHeight - edgePadding * 2
        val usableWidth = maxWidth - edgePadding * 2
        val tileSize = ((usableHeight - gap * (visibleRows - 1).coerceAtLeast(0)) / visibleRows.coerceAtLeast(1))
            .coerceAtLeast(1.dp)
        val columns = max(1, floor((usableWidth + gap) / (tileSize + gap)).toInt())

        fun clampedFocus(index: Int) = index.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        fun move(next: Int) {
            if (entries.isNotEmpty()) onFocusedIndexChange(clampedFocus(next))
        }

        val pageCount = FocusGridMath.pageCount(entries.size, columns, visibleRows).coerceAtLeast(1)
        val currentPage = FocusGridMath.pageOf(focusedIndex, columns, visibleRows).coerceIn(0, pageCount - 1)

        fun goToPage(page: Int) {
            if (entries.isEmpty()) return
            val target = page.coerceIn(0, pageCount - 1)
            val rowsToJump = target * visibleRows - FocusGridMath.rowOf(focusedIndex, columns)
            move(FocusGridMath.jumpRows(focusedIndex, rowsToJump, columns, entries.size))
        }

        SideEffect { onPagingChanged(currentPage, pageCount, ::goToPage) }

        val pagerState = rememberPagerState(initialPage = currentPage) { pageCount }

        // focusedIndex (via currentPage) is the single source of truth for which page is showing —
        // userScrollEnabled=false below means the pager itself never changes currentPage on its
        // own, only ever in response to this effect.
        LaunchedEffect(currentPage) {
            if (pagerState.currentPage != currentPage) {
                pagerState.animateScrollToPage(
                    page = currentPage,
                    animationSpec = tween(500, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
                )
            }
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusTarget()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (buttonMapping.buttonForKeyCode(keyEvent.nativeKeyEvent.keyCode)) {
                            LogicalButton.DPAD_UP -> { move(FocusGridMath.moveUp(focusedIndex, columns, entries.size)); true }
                            LogicalButton.DPAD_DOWN -> { move(FocusGridMath.moveDown(focusedIndex, columns, entries.size)); true }
                            LogicalButton.DPAD_LEFT -> { move(FocusGridMath.moveLeft(focusedIndex, columns, entries.size)); true }
                            LogicalButton.DPAD_RIGHT -> { move(FocusGridMath.moveRight(focusedIndex, columns, entries.size)); true }
                            LogicalButton.L -> { goToPage(currentPage - 1); true }
                            LogicalButton.R -> { goToPage(currentPage + 1); true }
                            LogicalButton.A -> { entries.getOrNull(focusedIndex)?.let(onActivate); true }
                            LogicalButton.Y -> {
                                if (onSecondaryAction != null) {
                                    entries.getOrNull(focusedIndex)?.let(onSecondaryAction); true
                                } else false
                            }
                            else -> false
                        }
                    },
            ) { page ->
                // Hidden until this page is the pager's *settled* page (i.e. a full page-change has
                // actually finished, not merely started) — settledPage holds the OLD page for the
                // whole 500ms slide and only flips once it completes, so the incoming page's tiles
                // stay invisible for that whole window instead of popping in mid-slide still showing
                // short-code placeholders while their real artwork is still being looked up.
                val revealed = pagerState.settledPage == page
                val revealAlpha by animateFloatAsState(if (revealed) 1f else 0f, animationSpec = tween(220), label = "pageRevealAlpha")
                Box(modifier = Modifier.fillMaxSize().alpha(revealAlpha), contentAlignment = Alignment.Center) {
                    PageContent(
                        entries = entries,
                        page = page,
                        columns = columns,
                        visibleRows = visibleRows,
                        tileSize = tileSize,
                        gap = gap,
                        edgePadding = edgePadding,
                        focusedIndex = focusedIndex,
                        onTileClick = { index, item ->
                            if (index == focusedIndex) onActivate(item) else onFocusedIndexChange(index)
                        },
                        itemContent = itemContent,
                    )
                }
            }

            if (pageCount > 1) {
                PageJumpButton(
                    direction = PageJumpDirection.PREVIOUS,
                    enabled = currentPage > 0,
                    onClick = { goToPage(currentPage - 1) },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                PageJumpButton(
                    direction = PageJumpDirection.NEXT,
                    enabled = currentPage < pageCount - 1,
                    onClick = { goToPage(currentPage + 1) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun <T> PageContent(
    entries: List<T>,
    page: Int,
    columns: Int,
    visibleRows: Int,
    tileSize: Dp,
    gap: Dp,
    edgePadding: Dp,
    focusedIndex: Int,
    onTileClick: (Int, T) -> Unit,
    itemContent: @Composable (item: T, index: Int, focused: Boolean, tileSize: Dp) -> Unit,
) {
    val perPage = columns * visibleRows
    val pageStart = page * perPage
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.padding(edgePadding),
    ) {
        for (rowIndex in 0 until visibleRows) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (colIndex in 0 until columns) {
                    val index = pageStart + rowIndex * columns + colIndex
                    if (index < entries.size) {
                        val item = entries[index]
                        Box(
                            modifier = Modifier
                                .size(tileSize)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onTileClick(index, item) },
                        ) {
                            itemContent(item, index, index == focusedIndex, tileSize)
                        }
                    } else {
                        Box(modifier = Modifier.size(tileSize))
                    }
                }
            }
        }
    }
}
