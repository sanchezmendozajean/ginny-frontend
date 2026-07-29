package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.GinnyColors

enum class BadgeStyle(val brush: @Composable () -> Brush) {
    A({ Brush.linearGradient(listOf(GinnyColors.AccentCeleste, GinnyColors.AccentBlue)) }),
    B({ Brush.linearGradient(listOf(GinnyColors.AccentRedSoft, GinnyColors.AccentRed)) }),
    X({ Brush.linearGradient(listOf(Color(0xFFB8C2CC), Color(0xFF8B98A4))) }),
    Y({ Brush.linearGradient(listOf(GinnyColors.AccentYellowSoft, GinnyColors.AccentYellow)) }),
}

private val DefaultBadgeSize = 26.dp
private val PillPadding = 6.dp

/** Height of every pill-shaped touch target (badge + padding) — bars are sized to match this. */
val ButtonPillHeight = DefaultBadgeSize + PillPadding * 2

@Composable
fun ButtonBadge(letter: String, style: BadgeStyle, modifier: Modifier = Modifier, size: Dp = DefaultBadgeSize) {
    Box(
        modifier = modifier
            .size(size)
            .background(style.brush(), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, color = GinnyColors.TextInverse, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The pill-shaped touch target used in the bottom bar: badge + label, "cuerpo sólido" per spec.
 * Touch/physical-button only, per spec — never part of D-Pad/keyboard focus traversal, even when
 * it's the only focusable-looking thing on screen (e.g. an empty games grid).
 */
@Composable
fun ActionPill(
    letter: String,
    label: String,
    style: BadgeStyle,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .focusProperties { canFocus = false }
            .glassShadow(999.dp)
            .background(Color.White, RoundedCornerShape(999.dp))
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(start = PillPadding, end = 14.dp, top = PillPadding, bottom = PillPadding),
    ) {
        ButtonBadge(letter = letter, style = style)
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = GinnyColors.TextPrimary)
    }
}

/**
 * Icon-only counterpart to [ActionPill]: same white "cuerpo sólido" pill background (here a
 * circle, since there's no label to widen it) wrapping a colored badge with an icon instead of a
 * letter. Used for the top bar's options button. Touch-only, same as [ActionPill].
 */
@Composable
fun IconActionBadge(
    icon: ImageVector,
    contentDescription: String?,
    style: BadgeStyle,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .focusProperties { canFocus = false }
            .glassShadow(999.dp)
            .background(Color.White, CircleShape)
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(PillPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(DefaultBadgeSize)
                .background(style.brush(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = GinnyColors.TextInverse,
                modifier = Modifier.size(DefaultBadgeSize * 0.55f),
            )
        }
    }
}
