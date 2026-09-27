package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.ui.theme.LocalGinnyPalette
import com.ginny.frontend.ui.theme.diagonalGradient
import com.ginny.frontend.ui.theme.oklch

/** Bar is sized to match the height of the pill-shaped buttons that sit next to it. */
private val BottomBarHeight = ButtonPillHeight

/** One hint pill ("A Jugar", "B Atrás", "Y Opciones", ...) shown at the bottom-right. Touch/physical
 * -button only, per [ActionPill] — never part of D-Pad focus traversal. */
data class BottomBarHint(val letter: String, val label: String, val onClick: () -> Unit)

/**
 * Bottom bar: a leading glass pill (small hue-tinted square + selected item name, + optional
 * smaller secondary line), an optional centered page-dots pill (only when [pageCount] > 1), and a
 * glass pill of [hints] on the right.
 */
@Composable
fun GinnyBottomBar(
    selectedName: String,
    leadingHue: Float,
    selectedSecondaryText: String? = null,
    pageCount: Int = 0,
    currentPage: Int = 0,
    onPageSelected: (Int) -> Unit = {},
    hints: List<BottomBarHint>,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGinnyPalette.current

    Box(modifier = modifier.fillMaxWidth()) {
        GlassSurface(modifier = Modifier.align(Alignment.CenterStart).height(BottomBarHeight)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 22.dp)) {
                Box(
                    modifier = Modifier.size(28.dp).background(
                        diagonalGradient(oklch(0.78f, 0.13f, leadingHue), oklch(0.83f, 0.11f, leadingHue + 50f)),
                        RoundedCornerShape(9.dp),
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = selectedName,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp,
                    )
                    if (!selectedSecondaryText.isNullOrBlank()) {
                        Text(
                            text = selectedSecondaryText,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 13.sp,
                        )
                    }
                }
            }
        }

        if (pageCount > 1) {
            GlassSurface(modifier = Modifier.align(Alignment.Center).height(34.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    repeat(pageCount) { page ->
                        PageDot(active = page == currentPage, onClick = { onPageSelected(page) })
                    }
                }
            }
        }

        GlassSurface(modifier = Modifier.align(Alignment.CenterEnd).height(BottomBarHeight)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(start = 20.dp, end = 12.dp),
            ) {
                hints.forEach { hint ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .clickable(onClick = hint.onClick),
                    ) {
                        ButtonBadge(letter = hint.letter, style = BadgeStyle.SOLID, size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(text = hint.label, style = MaterialTheme.typography.labelLarge, color = palette.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDot(active: Boolean, onClick: () -> Unit) {
    val palette = LocalGinnyPalette.current
    val width by animateDpAsState(if (active) 24.dp else 8.dp, label = "pageDotWidth")
    Box(
        modifier = Modifier
            .width(width)
            .height(8.dp)
            .focusProperties { canFocus = false }
            .background(if (active) palette.accent else palette.line, CircleShape)
            .clickable(onClick = onClick),
    )
}
