package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.R
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/**
 * Botones frontales del mando, con la rotación que necesita ic_buttons para señalarlos.
 *
 * En ic_buttons el círculo distinto (sin relleno) está a la izquierda. Girando el ícono en sentido
 * horario, ese círculo pasa a la posición del botón, según la distribución tipo Nintendo:
 * A derecha, B abajo, X arriba, Y izquierda.
 */
enum class FaceButton(val rotation: Float) {
    A(180f), // izquierda → derecha
    B(270f), // izquierda → abajo
    X(90f),  // izquierda → arriba
    Y(0f),   // ya está a la izquierda
}

/** Ícono del mando señalando [button] + su acción ("Abrir", "Regresar"…). */
@Composable
fun ButtonLegend(button: FaceButton, label: String, modifier: Modifier = Modifier) {
    val palette = GinnyThemes.current

    Row(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(id = R.drawable.ic_buttons, degrees = button.rotation, size = 16.dp)
        Text(
            text = label,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = palette.ink,
        )
    }
}
