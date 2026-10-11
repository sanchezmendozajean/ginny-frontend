package com.ginny.frontend.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.ui.components.settings.SettingsDivider
import com.ginny.frontend.ui.components.settings.SettingsGroup
import com.ginny.frontend.ui.components.settings.SettingsRow
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/** Una línea de créditos: [title] y su origen a la izquierda, el [author] a la derecha. */
private data class Credit(val title: String, val source: String, val author: String)

private val Music = listOf(
    Credit("Monday Marimba", "Pixabay", "faespencer"),
    Credit("Calm Bay", "Pixabay", "danielhren"),
)

private val SoundEffects = listOf(
    Credit("System Notification 02", "Pixabay", "Universfield"),
    Credit("Interface Sounds", "kenney.nl · CC0", "Kenney"),
)

private val DataAndArt = listOf(
    Credit("ScreenScraper", "screenscraper.fr · metadatos, wheel, cajas y video", "Comunidad de ScreenScraper"),
    Credit("SteamGridDB", "steamgriddb.com · íconos", "Comunidad de SteamGridDB"),
)

private val Fonts = listOf(
    Credit("M PLUS Rounded 1c", "SIL Open Font License 1.1", "The Rounded M+ Project Authors"),
)

private val Libraries = listOf(
    Credit("Jetpack Compose, Room y DataStore", "Apache License 2.0", "Google / AndroidX"),
    Credit("Coil", "Apache License 2.0", "Coil Contributors"),
    Credit("OkHttp", "Apache License 2.0", "Square"),
    Credit("Haze", "Apache License 2.0", "Chris Banes"),
)

/**
 * Sección Créditos: a quién se debe la música, los efectos, los datos y el arte descargados, la
 * tipografía y las bibliotecas. Es solo lectura. Al agregar un recurso de terceros, se agrega aquí y
 * en el README.
 */
@Composable
fun CreditosOptions() {
    val palette = GinnyThemes.current
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CreditGroup("Música", Music)
        CreditGroup("Efectos de sonido", SoundEffects)
        CreditGroup("Datos y arte de los juegos", DataAndArt)
        CreditGroup("Tipografía", Fonts)
        CreditGroup("Bibliotecas", Libraries)
        Text(
            text = "Gracias a quienes mantienen estos recursos y servicios abiertos para la comunidad.",
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = palette.muted,
        )
    }
}

@Composable
private fun CreditGroup(label: String, credits: List<Credit>) {
    val palette = GinnyThemes.current
    SettingsGroup(label) {
        credits.forEachIndexed { index, credit ->
            if (index > 0) SettingsDivider()
            SettingsRow(title = credit.title, subtitle = credit.source, subtitleMaxLines = 2) {
                Text(
                    text = credit.author,
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = palette.ink,
                )
            }
        }
    }
}
