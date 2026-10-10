package com.ginny.frontend.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.GinnyDropdown
import com.ginny.frontend.ui.screens.scrap.ScrapModalScrim
import com.ginny.frontend.ui.screens.settings.SettingsModalCornerRadius
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/** Ancho de la tarjeta de estos diálogos: se ajusta al contenido, entre estos dos límites. */
private val DialogMinWidth = 340.dp
private val DialogMaxWidth = 440.dp

/** Tarjeta blanca de un diálogo sobre el modal del juego. Tocar fuera ([onDismiss]) lo cierra. */
@Composable
private fun DialogCard(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ScrapModalScrim(onDismiss = onDismiss) {
        val shape = RoundedCornerShape(SettingsModalCornerRadius)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(min = DialogMinWidth, max = DialogMaxWidth)
                .shadow(24.dp, shape)
                .background(Color.White, shape)
                .pointerInput(Unit) { detectTapGestures { } } // tocar la tarjeta no la cierra
                .padding(24.dp),
        ) { content() }
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(text, fontFamily = GinnyFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = GinnyThemes.current.ink)
}

/**
 * "Opciones de Scrappeo" de un juego: qué se descarga. "Sólo lo que falte" completa lo que no tiene
 * (e ignora los "no encontrado" anteriores); "Todo" vuelve a pedir todo y reemplaza lo ya guardado.
 */
@Composable
fun ScrapGameDialog(onCancel: () -> Unit, onStart: (overwrite: Boolean) -> Unit) {
    var overwrite by remember { mutableStateOf(false) }
    DialogCard(onDismiss = onCancel) {
        DialogTitle("Opciones de Scrappeo")
        GinnyDropdown(
            selected = overwrite,
            options = listOf("Sólo lo que falte" to false, "Todo" to true),
            onSelect = { overwrite = it },
            width = 292.dp,
        )
        if (overwrite) {
            Text(
                text = "Reemplazará las imágenes y los metadatos que ya tiene.",
                fontFamily = GinnyFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = GinnyThemes.current.muted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            GinnyButton("Cancelar", onClick = onCancel)
            GinnyButton("Iniciar", primary = true, onClick = { onStart(overwrite) })
        }
    }
}

/** Confirmación de "Eliminar": borra el archivo del juego y todo lo que se descargó de él. */
@Composable
fun ConfirmDeleteDialog(gameName: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val palette = GinnyThemes.current
    DialogCard(onDismiss = onCancel) {
        DialogTitle("Eliminar juego")
        Text(
            text = "¿Seguro que quieres continuar? Se eliminará \"$gameName\" y todo lo que se descargó de él. " +
                "Esto no se puede deshacer.",
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = palette.muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            GinnyButton("Cancelar", onClick = onCancel)
            DangerButton("Eliminar", onClick = onConfirm)
        }
    }
}

/** Botón de acción destructiva: misma forma que [GinnyButton], en rojo. */
@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(40.dp)
            .background(DeleteRed, CircleShape)
            .pointerInput(Unit) { detectTapGestures { currentOnClick() } }
            .padding(horizontal = 20.dp),
    ) {
        Text(text, fontFamily = GinnyFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White, maxLines = 1)
    }
}
