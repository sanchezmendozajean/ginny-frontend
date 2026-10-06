package com.ginny.frontend.ui.layout

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.components.ButtonLegend
import com.ginny.frontend.ui.components.Container
import com.ginny.frontend.ui.components.FaceButton
import com.ginny.frontend.ui.components.PageIndicator
import com.ginny.frontend.ui.components.SelectionInfo

/**
 * Barra inferior: elemento enfocado a la izquierda; a la derecha, los puntos de página sobre la
 * leyenda de botones. [onBack]: si hay a dónde regresar (fuera de Sistemas), muestra "Regresar".
 */
@Composable
fun GinnyFooter(
    modifier: Modifier = Modifier,
    title: String = "",
    subtitle: String = "",
    pageCount: Int = 1,
    currentPage: Int = 0,
    onBack: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        // El elemento seleccionado en la grilla.
        SelectionInfo(title = title, subtitle = subtitle)

        // Columna a la derecha: en un Column, align solo acepta alineación horizontal (End).
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.End,                // ambos pegados a la derecha
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Alturas fijas: Container usa fillMaxHeight y, sin ellas, el primero se quedaría
            // con todo el alto.
            PageIndicator(pageCount = pageCount, currentPage = currentPage, modifier = Modifier.height(16.dp))

            Container(modifier = Modifier.height(28.dp), horizontalPadding = 12.dp) {
                ButtonLegend(FaceButton.A, "Abrir")
                // Regresar: solo fuera de Sistemas (sin [onBack] no hay a dónde volver). Tocable.
                if (onBack != null) {
                    ButtonLegend(
                        FaceButton.B,
                        "Regresar",
                        modifier = Modifier.pointerInput(onBack) { detectTapGestures { onBack() } },
                    )
                }
                ButtonLegend(FaceButton.Y, "Opciones")
            }
        }
    }
}
