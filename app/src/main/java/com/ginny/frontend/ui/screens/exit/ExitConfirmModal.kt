package com.ginny.frontend.ui.screens.exit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.ui.components.FaceButton
import com.ginny.frontend.ui.components.FaceButtonIcon
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.screens.scrap.ScrapModalScrim
import com.ginny.frontend.ui.screens.settings.SettingsModalCornerRadius
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/**
 * Confirmación para cerrar la aplicación (botón B en la lista de Sistemas): salir arrastrando los bordes de la pantalla
 * no es práctico en una consola. Sin cursor, como el aviso de las credenciales: B cancela (lo maneja MainActivity →
 * BackHandler de GinnyApp → [onCancel]) y A cierra ([onConfirm]); los botones también se pueden tocar.
 */
@Composable
fun ExitConfirmModal(visible: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val palette = GinnyThemes.current
        ScrapModalScrim(
            onDismiss = onCancel,
            onButton = { button -> if (button == LogicalButton.A) onConfirm() },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(SettingsModalCornerRadius))
                    .pointerInput(Unit) { detectTapGestures { } } // tocar la tarjeta no la cierra
                    .padding(24.dp),
            ) {
                Text(
                    text = "Cerrar Ginny",
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = palette.ink,
                )
                Text(
                    text = "¿Está seguro de que desea cerrar la aplicación?",
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = palette.ink,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    GinnyButton(
                        text = "Cancelar",
                        onClick = onCancel,
                        leadingIcon = { tint -> FaceButtonIcon(FaceButton.B, tint = tint, size = 18.dp) },
                    )
                    GinnyButton(
                        text = "Cerrar",
                        onClick = onConfirm,
                        primary = true,
                        leadingIcon = { tint -> FaceButtonIcon(FaceButton.A, tint = tint, size = 18.dp) },
                    )
                }
            }
        }
    }
}
