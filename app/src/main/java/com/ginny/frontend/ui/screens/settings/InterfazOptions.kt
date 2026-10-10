package com.ginny.frontend.ui.screens.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.data.model.BundledMusicTrack
import com.ginny.frontend.data.model.ButtonLayout
import com.ginny.frontend.data.model.MusicSelection
import com.ginny.frontend.ui.components.settings.GinnyDropdown
import com.ginny.frontend.ui.components.settings.GinnySlider
import com.ginny.frontend.ui.components.settings.SegmentedPicker
import com.ginny.frontend.ui.components.settings.SettingsDivider
import com.ginny.frontend.ui.components.settings.SettingsGroup
import com.ginny.frontend.ui.components.settings.SettingsRow
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import com.ginny.frontend.ui.theme.ThemeMode
import kotlin.math.roundToInt

/** Ancho de los controles de la derecha (deslizadores, desplegable), para que queden alineados. */
private val ControlWidth = 220.dp

/**
 * Opciones de la sección Interfaz: grilla (filas visibles), tema, botones (distribución) y audio (volúmenes y
 * pista de fondo). Lee y guarda a través de [viewModel], que además sincroniza ginny.cfg.
 */
@Composable
fun InterfazOptions(viewModel: SettingsViewModel) {
    val settings = viewModel.settings
    val gridRows by settings.gridRows.collectAsState(initial = 2)
    val buttonLayout by settings.buttonLayout.collectAsState(initial = ButtonLayout.YX_BA)
    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.MENTA)
    val musicVolume by settings.musicVolume.collectAsState(initial = 0.5f)
    val uiVolume by settings.uiSoundVolume.collectAsState(initial = 0.7f)
    val videoVolume by settings.videoVolume.collectAsState(initial = 0.5f)
    val storedTrack by settings.backgroundMusicTrack.collectAsState(initial = null)
    val userFolder by settings.userFolderUri.collectAsState(initial = null)

    // Pistas propias de Interface/Background Music en la Carpeta de Usuario (búsqueda de archivos,
    // por eso en segundo plano; se repite si cambia la carpeta).
    val customTracks by produceState(initialValue = emptyList<MusicSelection.Custom>(), userFolder) {
        value = userFolder?.let { viewModel.musicRepository.listCustomTracks(Uri.parse(it)) }.orEmpty()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsGroup("Grilla") {
            SettingsRow(title = "Filas visibles") {
                // 1–4: regla del spec (el diseño de referencia muestra 2–4).
                SegmentedPicker(
                    options = listOf(1, 2, 3, 4),
                    selected = gridRows,
                    onSelect = { viewModel.setGridRows(it) },
                )
            }
        }

        SettingsGroup("Tema") {
            SettingsRow(title = "Paleta de color") {
                // Temas fijos y, al final, "Cambio cíclico" (pasa por todos ellos).
                GinnyDropdown(
                    selected = themeMode,
                    options = ThemeMode.entries.map { it.displayName to it },
                    onSelect = { viewModel.setThemeMode(it) },
                    width = ControlWidth + 52.dp,
                )
            }
        }

        SettingsGroup("Botones") {
            SettingsRow(title = "Distribución") {
                // Solo cambia qué botón señalan los íconos de la leyenda (ver ButtonLayout).
                GinnyDropdown(
                    selected = buttonLayout,
                    options = ButtonLayout.entries.map { it.label to it },
                    onSelect = { viewModel.setButtonLayout(it) },
                    width = ControlWidth + 52.dp, // mismo ancho que el desplegable de pista
                )
            }
        }

        SettingsGroup("Audio") {
            SettingsRow(title = "Música de fondo") {
                VolumeControl(musicVolume) { viewModel.setMusicVolume(it) }
            }
            SettingsDivider()
            SettingsRow(title = "Sonidos de interfaz") {
                VolumeControl(uiVolume) { viewModel.setUiSoundVolume(it) }
            }
            SettingsDivider()
            SettingsRow(title = "Videos de los juegos") {
                VolumeControl(videoVolume) { viewModel.setVideoVolume(it) }
            }
            SettingsDivider()
            SettingsRow(title = "Pista de fondo") {
                val tracks = BundledMusicTrack.entries.map { MusicSelection.Bundled(it) } + customTracks
                GinnyDropdown(
                    selected = MusicSelection.decode(storedTrack, customTracks),
                    options = tracks.map { it.label() to it },
                    onSelect = { viewModel.setBackgroundMusicTrack(it) },
                    width = ControlWidth + 52.dp, // = deslizador + porcentaje
                )
            }
        }
    }
}

/** Deslizador + porcentaje a su derecha. */
@Composable
private fun VolumeControl(value: Float, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GinnySlider(value = value, onValueChange = onValueChange, modifier = Modifier.width(ControlWidth))
        Text(
            text = "${(value * 100).roundToInt()}%",
            fontFamily = GinnyFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            color = GinnyThemes.current.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}

/** Nombre a mostrar de una pista. */
private fun MusicSelection.label(): String = when (this) {
    is MusicSelection.Bundled -> track.displayName
    is MusicSelection.Custom -> displayName
}
