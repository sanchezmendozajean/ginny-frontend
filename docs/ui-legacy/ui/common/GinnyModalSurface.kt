package com.ginny.frontend.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.LocalGinnyPalette

/**
 * Shared chrome for every centered modal in the new design (Configuración, Opciones de juego):
 * a full-screen tinted scrim (tap to dismiss) plus a rounded white card that swallows its own
 * taps so tapping the card body never dismisses. [cardModifier] controls the card's size —
 * Configuración fills most of the screen, Opciones de juego is a smaller centered card.
 */
@Composable
fun GinnyModalSurface(
    onDismissRequest: () -> Unit,
    cardModifier: Modifier = Modifier,
    cardCornerRadius: androidx.compose.ui.unit.Dp = 36.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalGinnyPalette.current
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(cardCornerRadius)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.scrim)
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismissRequest),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = cardModifier
                .clickable(interactionSource = cardInteraction, indication = null, onClick = {})
                .shadow(elevation = 30.dp, shape = shape, ambientColor = palette.shadow, spotColor = palette.shadow)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.97f)),
            content = content,
        )
    }
}
