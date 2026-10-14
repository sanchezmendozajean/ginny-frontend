package com.ginny.frontend.ui.screens.settings

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.ui.components.FaceButton
import com.ginny.frontend.ui.components.FaceButtonIcon
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.screens.scrap.ScrapModalScrim
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/**
 * Aviso previo a activar "Guardar credenciales en la Carpeta de Usuario" (Configuración > Scrapper): las
 * credenciales quedarán en `ginny.cfg` en **texto plano**. Sirve para compartirlas con Ginny Frontend Desktop,
 * pero es un riesgo si la Carpeta de Usuario se comparte con alguien, y se recomienda no hacerlo.
 *
 * Sin cursor: Cancelar = B (lo maneja MainActivity → BackHandler de GinnyApp → [onCancel]) y Aceptar = A, directos, o
 * tocando el botón.
 */
@Composable
fun SaveCredentialsModal(visible: Boolean, onCancel: () -> Unit, onAccept: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val palette = GinnyThemes.current
        // Sin cursor del mando: A acepta y B cancela directamente (son solo dos opciones). Se ignora la pulsación de
        // los primeros 400 ms: el mismo A con que se abrió el modal, si se mantiene, repite y lo aceptaría sin querer.
        val shownAt = remember { System.currentTimeMillis() }
        ScrapModalScrim(
            onDismiss = onCancel,
            onButton = { button ->
                if (button == LogicalButton.A && System.currentTimeMillis() - shownAt > 400) onAccept()
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(SettingsModalCornerRadius))
                    .pointerInput(Unit) { detectTapGestures { } } // tocar la tarjeta no la cierra
                    .padding(24.dp),
            ) {
                Text(
                    text = "Guardar credenciales en la Carpeta de Usuario",
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = palette.ink,
                )
                Text(
                    text = "Sus credenciales de ScreenScraper y SteamGridDB se guardarán en el archivo ginny.cfg, " +
                        "en texto plano. Es útil para usar los mismos datos en Ginny Frontend Desktop.",
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = palette.ink,
                )
                Text(
                    text = "Pero es un riesgo: cualquiera que reciba su Carpeta de Usuario podrá leerlas. " +
                        "Se recomienda no compartir esa carpeta ni su ginny.cfg.",
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.Bold,
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
                        text = "Aceptar",
                        onClick = onAccept,
                        primary = true,
                        leadingIcon = { tint -> FaceButtonIcon(FaceButton.A, tint = tint, size = 18.dp) },
                    )
                }
            }
        }
    }
}
