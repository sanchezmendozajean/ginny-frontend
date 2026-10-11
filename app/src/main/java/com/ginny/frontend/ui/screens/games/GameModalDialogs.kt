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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.GinnyDropdown
import com.ginny.frontend.ui.components.settings.settingsFocus
import com.ginny.frontend.ui.screens.scrap.ScrapModalScrim
import com.ginny.frontend.ui.screens.scrap.rememberModalNav
import com.ginny.frontend.ui.screens.settings.SettingsModalCornerRadius
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/** Margen interior de la tarjeta de estos diálogos. */
private val DialogPadding = 24.dp

/**
 * Tarjeta blanca de un diálogo sobre el modal del juego. Tocar fuera ([onDismiss]) lo cierra.
 *
 * El contenido mide [contentWidth] y la tarjeta ese ancho más el margen: así no sobra espacio a la derecha
 * de los controles de ancho fijo (el desplegable). Con el mando, el cursor empieza en el último control
 * (el botón de la derecha) o, con [focusLast] = false, en el primero; A actúa sobre el que tiene el cursor y B cierra.
 */
@Composable
private fun DialogCard(
    onDismiss: () -> Unit,
    contentWidth: Dp,
    focusLast: Boolean = true,
    content: @Composable () -> Unit,
) {
    ScrapModalScrim(onDismiss = onDismiss, nav = rememberModalNav(), focusLast = focusLast) {
        val shape = RoundedCornerShape(SettingsModalCornerRadius)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .width(contentWidth + DialogPadding * 2)
                .shadow(24.dp, shape)
                .background(Color.White, shape)
                .pointerInput(Unit) { detectTapGestures { } } // tocar la tarjeta no la cierra
                .padding(DialogPadding),
        ) { content() }
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(text, fontFamily = GinnyFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = GinnyThemes.current.ink)
}

/** Ancho del contenido de "Opciones de Scrappeo": el del desplegable. */
private val ScrapDialogContentWidth = 292.dp

/**
 * "Opciones de Scrappeo" de un juego: qué se descarga. "Sólo lo que falte" completa lo que no tiene
 * (e ignora los "no encontrado" anteriores); "Todo" vuelve a pedir todo y reemplaza lo ya guardado.
 */
@Composable
fun ScrapGameDialog(onCancel: () -> Unit, onStart: (overwrite: Boolean) -> Unit) {
    var overwrite by remember { mutableStateOf(false) }
    DialogCard(onDismiss = onCancel, contentWidth = ScrapDialogContentWidth) {
        DialogTitle("Opciones de Scrappeo")
        GinnyDropdown(
            selected = overwrite,
            options = listOf("Sólo lo que falte" to false, "Todo" to true),
            onSelect = { overwrite = it },
            width = ScrapDialogContentWidth,
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
    DialogCard(onDismiss = onCancel, contentWidth = 392.dp, focusLast = false) {
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
            .settingsFocus(onActivate = onClick)
            .background(DeleteRed, CircleShape)
            .pointerInput(Unit) { detectTapGestures { currentOnClick() } }
            .padding(horizontal = 20.dp),
    ) {
        Text(text, fontFamily = GinnyFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White, maxLines = 1)
    }
}
