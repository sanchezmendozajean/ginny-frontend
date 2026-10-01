package com.ginny.frontend.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.components.ButtonLegend
import com.ginny.frontend.ui.components.Container
import com.ginny.frontend.ui.components.FaceButton
import com.ginny.frontend.ui.components.SelectionInfo

/** Barra inferior: elemento enfocado a la izquierda, leyenda de botones a la derecha. */
@Composable
fun GinnyFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        // Textos estáticos por ahora; vendrán del elemento enfocado en la grilla.
        SelectionInfo(title = "Super Nintendo", subtitle = "128 juegos")

        Container(modifier = Modifier.align(Alignment.CenterEnd)) {
            ButtonLegend(FaceButton.A, "Abrir")
            ButtonLegend(FaceButton.B, "Regresar")
            ButtonLegend(FaceButton.Y, "Opciones")
        }
    }
}
