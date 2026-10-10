package com.ginny.frontend.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.data.scrap.ScrapMedia
import com.ginny.frontend.data.scrap.ScrapProgress
import com.ginny.frontend.data.scrap.ScrapProvider
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.GinnyProgressBar
import com.ginny.frontend.ui.components.settings.GinnySwitch
import com.ginny.frontend.ui.components.settings.GinnyTextField
import com.ginny.frontend.ui.components.settings.SettingsDivider
import com.ginny.frontend.ui.components.settings.SettingsGroup
import com.ginny.frontend.ui.components.settings.SettingsRow
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import kotlinx.coroutines.flow.Flow

/** Ancho de los campos de esta sección (igual que en Sistemas). */
private val ScrapperFieldWidth = 272.dp

/**
 * Opciones de la sección Scrapper: las credenciales de cada proveedor (un grupo por proveedor),
 * qué se descarga y cómo.
 *
 * Las credenciales **no** se sincronizan a ginny.cfg ni a las copias de seguridad (ver
 * [com.ginny.frontend.data.scrap.ScrapperSettingsRepository]). Se muestran como puntos; "Mostrar
 * claves" las deja a la vista para poder revisarlas al escribirlas con el teclado del dispositivo.
 */
@Composable
fun ScrapperOptions(viewModel: SettingsViewModel) {
    val scrapper = viewModel.scrapper
    val enabledMedia by scrapper.enabledMedia.collectAsState(initial = ScrapMedia.entries.filter { it.defaultEnabled }.toSet())
    val onlyOnWifi by scrapper.onlyOnWifi.collectAsState(initial = true)
    // No se recuerda entre aperturas: las claves vuelven a ocultarse al salir de la sección.
    var reveal by remember { mutableStateOf(false) }

    val progress by viewModel.scrapProgress.collectAsState()
    val scrapping by viewModel.isScrapping.collectAsState()
    val verify by viewModel.verify.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsGroup("Biblioteca") {
            SettingsRow(
                title = "Scrapear biblioteca",
                subtitle = when {
                    scrapping -> progress?.let { "Procesando ${(it.processed + 1).coerceAtMost(it.total)} de ${it.total}: ${it.current.orEmpty()}" }
                        ?: "Preparando…"
                    else -> "Descarga metadatos y arte de los juegos escaneados. Reanuda donde quedó; " +
                        "\"Reintentar\" vuelve a consultar lo que no se encontró."
                },
                subtitleMaxLines = 3,
            ) {
                if (scrapping) {
                    GinnyButton("Detener", onClick = viewModel::stopScrap)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GinnyButton("Reintentar", onClick = { viewModel.startScrap(force = true) })
                        GinnyButton("Scrapear", primary = true, onClick = { viewModel.startScrap(force = false) })
                    }
                }
            }
            progress?.let { current ->
                SettingsDivider()
                ScrapProgressView(current, running = scrapping)
            }
        }

        SettingsGroup("ScreenScraper") {
            SettingsRow(title = "Usuario", subtitle = "Tu cuenta de screenscraper.fr") {
                CredentialField(scrapper.screenScraperUser, viewModel::setScreenScraperUser, placeholder = "usuario")
            }
            SettingsDivider()
            SettingsRow(title = "Contraseña") {
                CredentialField(scrapper.screenScraperPassword, viewModel::setScreenScraperPassword, "contraseña", secret = true, masked = !reveal)
            }
            SettingsDivider()
            VerifyRow(verify[ScrapProvider.SCREENSCRAPER], "Comprueba usuario, contraseña y cupo diario", viewModel::verifyScreenScraper)
        }

        SettingsGroup("SteamGridDB") {
            SettingsRow(title = "Clave de API", subtitle = "Se genera en steamgriddb.com > Preferencias > API") {
                CredentialField(scrapper.steamGridDbApiKey, viewModel::setSteamGridDbApiKey, "clave", secret = true, masked = !reveal)
            }
            SettingsDivider()
            VerifyRow(verify[ScrapProvider.STEAMGRIDDB], "Comprueba que la clave sirve", viewModel::verifySteamGridDb)
        }

        SettingsGroup("Qué descargar") {
            ScrapMedia.entries.forEachIndexed { index, media ->
                if (index > 0) SettingsDivider()
                SettingsRow(title = media.label, subtitle = media.description) {
                    GinnySwitch(
                        checked = media in enabledMedia,
                        onCheckedChange = { viewModel.setScrapMediaEnabled(media, it) },
                    )
                }
            }
        }

        SettingsGroup("General") {
            SettingsRow(title = "Solo con Wi-Fi", subtitle = "No descargar con datos móviles") {
                GinnySwitch(checked = onlyOnWifi, onCheckedChange = viewModel::setScrapOnlyOnWifi)
            }
            SettingsDivider()
            SettingsRow(title = "Mostrar claves", subtitle = "Ver las contraseñas y la clave de API mientras se escriben") {
                GinnySwitch(checked = reveal, onCheckedChange = { reveal = it })
            }
        }
    }
}

/** Fila "Verificar": el botón y, debajo del título, el resultado de la última comprobación. */
@Composable
private fun VerifyRow(state: SettingsViewModel.VerifyState?, hint: String, onVerify: () -> Unit) {
    val running = state?.running == true
    SettingsRow(
        title = "Verificar",
        subtitle = when {
            running -> "Comprobando…"
            state?.message != null -> (if (state.ok) "✓ " else "✗ ") + state.message
            else -> hint
        },
        subtitleMaxLines = 3,
    ) {
        GinnyButton(if (running) "Comprobando…" else "Verificar", enabled = !running, onClick = onVerify)
    }
}

/**
 * Avance y resultado de un lote: barra, contadores, avisos de proveedores que se dejaron de usar y, si
 * se cortó, el motivo. Se queda visible al terminar para poder leer el resultado.
 */
@Composable
private fun ScrapProgressView(progress: ScrapProgress, running: Boolean) {
    val palette = GinnyThemes.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        if (progress.total > 0) {
            GinnyProgressBar(fraction = progress.processed.toFloat() / progress.total)
        }
        ProgressLine(
            text = "Con datos nuevos: ${progress.scraped} · Sin coincidencia: ${progress.notFound} · " +
                "Ya completos: ${progress.skipped} · Con error: ${progress.failed}",
            color = palette.ink,
            bold = true,
        )
        progress.warnings.forEach { ProgressLine(text = "⚠ $it", color = palette.muted) }
        when {
            progress.stopReason != null -> ProgressLine(text = "Se detuvo: ${progress.stopReason}", color = palette.muted)
            progress.finished && !running -> ProgressLine(
                text = "Terminado: ${progress.processed} ${if (progress.processed == 1) "juego revisado" else "juegos revisados"}.",
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun ProgressLine(text: String, color: Color, bold: Boolean = false) {
    Text(
        text = text,
        fontFamily = GinnyFont,
        fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
        fontSize = 12.sp,
        color = color,
    )
}

/**
 * Campo de una credencial. [GinnyTextField] toma su valor inicial una sola vez, así que el campo
 * recién se muestra cuando ya se leyó el valor guardado (null = todavía leyendo).
 */
@Composable
private fun CredentialField(
    stored: Flow<String>,
    onChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean = false,
    masked: Boolean = secret,
) {
    val value by stored.collectAsState(initial = null)
    value?.let { initial ->
        GinnyTextField(
            value = initial,
            onValueChange = onChange,
            placeholder = placeholder,
            width = ScrapperFieldWidth,
            secret = secret,
            masked = masked,
        )
    }
}
