package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.LocalGinnyPalette

/** Every "letter badge" in the new chrome is a solid [palette.ink] circle, EXCEPT the primary CTA
 * (the "Jugar"/A badge inside the gradient-filled play button), which uses the brand [palette.grad]. */
enum class BadgeStyle { SOLID, BRAND }

private val DefaultBadgeSize = 26.dp
private val PillPadding = 6.dp

/** Height of every pill-shaped touch target (badge + padding) — bars are sized to match this. */
val ButtonPillHeight = DefaultBadgeSize + PillPadding * 2

@Composable
fun ButtonBadge(letter: String, style: BadgeStyle, modifier: Modifier = Modifier, size: Dp = DefaultBadgeSize) {
    val palette = LocalGinnyPalette.current
    Box(
        modifier = modifier
            .size(size)
            .let { base ->
                when (style) {
                    BadgeStyle.SOLID -> base.background(palette.ink, CircleShape)
                    BadgeStyle.BRAND -> base.background(palette.grad, CircleShape)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The pill-shaped touch target used in the bottom bar: badge + label. Touch/physical-button only
 * per spec — never part of D-Pad/keyboard focus traversal, even when it's the only
 * focusable-looking thing on screen (e.g. an empty games grid).
 */
@Composable
fun ActionPill(
    letter: String,
    label: String,
    style: BadgeStyle,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    GlassSurface(
        modifier = modifier
            .focusProperties { canFocus = false }
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = PillPadding, end = 14.dp, top = PillPadding, bottom = PillPadding),
        ) {
            ButtonBadge(letter = letter, style = style)
            Spacer(Modifier.size(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = LocalGinnyPalette.current.ink)
        }
    }
}

/**
 * Icon-only glass circle — used for the top bar's direct-to-Settings hamburger button. Unlike the
 * old overflow trigger, the icon sits straight in [LocalGinnyPalette.current.ink], no nested
 * colored badge underneath it (matches the reference's plain 3-line glyph). Touch-only, same as
 * [ActionPill].
 */
@Composable
fun IconActionBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalGinnyPalette.current
    GlassSurface(
        shape = CircleShape,
        modifier = modifier
            .focusProperties { canFocus = false }
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.ink,
            modifier = Modifier
                .padding(PillPadding + 3.dp)
                .size(DefaultBadgeSize * 0.7f),
        )
    }
}
