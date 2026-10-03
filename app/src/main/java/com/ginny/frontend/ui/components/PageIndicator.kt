package com.ginny.frontend.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.GinnyThemes

/** Diámetro de cada punto (y alto de la píldora de la página actual). */
private val DotSize = 7.dp

/** Ancho de la píldora que marca la página actual. */
private val ActiveDotWidth = 20.dp

/** Separación entre puntos. */
private val DotSpacing = 4.dp

/**
 * Un punto por página de contenido dentro de un [Container]; el de [currentPage] (desde 0) se
 * alarga en una píldora del color de acento. El cambio de página se anima (ancho y color).
 */
@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val palette = GinnyThemes.current

    Container(
        modifier = modifier,
        roundedCorner = 12.dp,
        horizontalPadding = 8.dp,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DotSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { page ->
                val active = page == currentPage
                val width by animateDpAsState(if (active) ActiveDotWidth else DotSize, label = "dotWidth")
                val color by animateColorAsState(
                    if (active) palette.accent else Color.White,
                    label = "dotColor",
                )
                Box(
                    modifier = Modifier
                        .height(DotSize)
                        .width(width)
                        .background(color, CircleShape), // CircleShape en un rectángulo = píldora
                )
            }
        }
    }
}
