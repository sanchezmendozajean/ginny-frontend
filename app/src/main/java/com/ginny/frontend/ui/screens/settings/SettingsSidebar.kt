package com.ginny.frontend.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.ui.components.ButtonLegend
import com.ginny.frontend.ui.components.FaceButton
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import com.ginny.frontend.ui.theme.lighten

/** Secciones de Configuración, en el orden en que aparecen. */
enum class SettingsSection(val label: String) {
    Interfaz("Interfaz"),
    Carpetas("Carpetas"),
    Sistemas("Sistemas"), // antes "Emuladores": emulador, core y carpeta de ROMs de cada sistema
    Scrapper("Scrapper"), // credenciales de SteamGridDB / ScreenScraper y qué descargar
    Creditos("Créditos"), // a quién se deben la música, los datos, el arte y las bibliotecas
}

/** Ancho de la barra lateral. */
val SettingsSidebarWidth = 160.dp

/**
 * Barra lateral del modal: título "Configuración", la lista de secciones (la elegida como píldora
 * con el degradado del acento) y, abajo, la indicación de que B cierra el modal.
 *
 * Tocar una sección la elige ([onSelect]); tocar "Cerrar" cierra el modal ([onClose]).
 * La navegación con la cruceta la maneja el modal.
 */
@Composable
fun SettingsSidebar(
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = GinnyThemes.current

    Column(
        modifier = modifier
            .width(SettingsSidebarWidth)
            .fillMaxHeight()
            .background(palette.background.copy(alpha = 0.7f)) // tono suave del tema
            .padding(horizontal = 16.dp, vertical = 22.dp),
    ) {
        Text(
            text = "Configuración",
            fontFamily = GinnyFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = palette.ink,
            modifier = Modifier.padding(start = 8.dp),
        )

        Spacer(Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsSection.entries.forEach { section ->
                SectionItem(
                    label = section.label,
                    selected = section == selected,
                    onClick = { onSelect(section) },
                )
            }
        }

        // Empuja la indicación de cierre hasta abajo.
        Spacer(Modifier.weight(1f))

        ButtonLegend(
            FaceButton.B,
            "Cerrar",
            modifier = Modifier
                .padding(start = 8.dp)
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
    }
}

/** Una sección de la lista: punto + nombre. Elegida = píldora con el degradado del acento. */
@Composable
private fun SectionItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = GinnyThemes.current
    val pill = CircleShape // en un rectángulo, CircleShape da una píldora

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .then(
                if (selected) {
                    Modifier
                        .shadow(8.dp, pill, ambientColor = palette.accent, spotColor = palette.accent)
                        .background(
                            Brush.horizontalGradient(listOf(palette.accent.lighten(0.08f), palette.accent)),
                            pill,
                        )
                } else {
                    Modifier
                },
            )
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp),
    ) {
        // Punto: blanco en la elegida; tenue en las demás.
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (selected) Color.White else palette.ink.copy(alpha = 0.12f), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = if (selected) Color.White else palette.ink,
        )
    }
}
