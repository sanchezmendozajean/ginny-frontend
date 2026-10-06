package com.ginny.frontend.ui.screens.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Alto del desvanecimiento en los bordes superior e inferior del panel de opciones. */
val SettingsFadeHeight = 28.dp

/**
 * Panel derecho del modal: las opciones de la sección elegida ([options]), apiladas una debajo de
 * otra. Si no entran en la pantalla, se desplazan, y se desvanecen en el borde por el que siguen.
 */
@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    options: @Composable ColumnScope.() -> Unit = {},
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            // Antes que verticalScroll: así el desvanecimiento queda fijo en los bordes del panel
            // y el contenido pasa por debajo.
            .fadingEdges(scroll, SettingsFadeHeight)
            // verticalScroll antes que padding: el margen se desplaza junto con las opciones, así
            // al llegar al final queda el mismo aire que arriba.
            .verticalScroll(scroll)
            .padding(20.dp),
        content = options,
    )
}

/**
 * Desvanece el contenido en el borde superior y en el inferior, solo si por ese lado queda algo
 * por desplazar. Cada borde aparece de a poco: con los primeros [height] de recorrido.
 *
 * Cómo: el contenido se dibuja en una capa aparte (Offscreen) y encima se pinta un degradado con
 * BlendMode.DstIn, que conserva el contenido solo donde el degradado es opaco. Todo se lee en la
 * fase de dibujo, así desplazar no recompone nada.
 */
private fun Modifier.fadingEdges(scroll: ScrollState, height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = height.toPx()
        val top = (scroll.value / fade).coerceIn(0f, 1f)
        val bottom = ((scroll.maxValue - scroll.value) / fade).coerceIn(0f, 1f)
        if (top > 0f) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - top), Color.Black), 0f, fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottom > 0f) {
            val y = size.height - fade
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - bottom)), y, size.height),
                topLeft = Offset(0f, y),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }
