package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.R
import com.ginny.frontend.data.model.ButtonLayout
import com.ginny.frontend.data.model.FacePosition
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/** Botones frontales del mando. */
enum class FaceButton { A, B, X, Y }

/**
 * Distribución de botones elegida en Configuración > Botones. La provee MainActivity; por
 * defecto, tipo Nintendo (YX/BA).
 */
val LocalButtonLayout = staticCompositionLocalOf { ButtonLayout.YX_BA }

/** Dónde está [this] botón en la distribución [layout]. */
private fun FaceButton.positionIn(layout: ButtonLayout): FacePosition = when (this) {
    FaceButton.A -> layout.a
    FaceButton.B -> layout.b
    FaceButton.X -> layout.x
    FaceButton.Y -> layout.y
}

/**
 * Rotación de ic_buttons para señalar una posición. En el ícono, el círculo distinto (sin relleno)
 * está a la izquierda; girándolo en sentido horario pasa a la posición pedida.
 */
private fun FacePosition.iconRotation(): Float = when (this) {
    FacePosition.Left -> 0f
    FacePosition.Top -> 90f
    FacePosition.Right -> 180f
    FacePosition.Bottom -> 270f
}

/**
 * Ícono del mando señalando [button] + su acción ("Abrir", "Regresar"…). El círculo resaltado
 * sigue la distribución elegida en Configuración ([LocalButtonLayout]).
 */
@Composable
fun ButtonLegend(button: FaceButton, label: String, modifier: Modifier = Modifier) {
    val palette = GinnyThemes.current
    val rotation = button.positionIn(LocalButtonLayout.current).iconRotation()

    Row(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(id = R.drawable.ic_buttons, degrees = rotation, size = 16.dp)
        Text(
            text = label,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = palette.ink,
        )
    }
}
