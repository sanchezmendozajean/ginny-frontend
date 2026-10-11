package com.ginny.frontend.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.SettingsDivider
import com.ginny.frontend.ui.components.settings.SettingsGroup
import com.ginny.frontend.ui.components.settings.SettingsRow
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opciones de la sección Carpetas: Carpeta de Usuario (assets y ginny.cfg), biblioteca (carpeta de
 * ROMs + escanear) y estados guardados de RetroArch. Cada carpeta se elige con el selector de
 * carpetas del sistema (SAF); el ViewModel guarda el permiso persistente.
 */
@Composable
fun CarpetasOptions(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val settings = viewModel.settings
    val userFolder by settings.userFolderUri.collectAsState(initial = null)
    val romFolder by settings.romFolderUri.collectAsState(initial = null)
    val romOverrides by settings.romFolderOverrides.collectAsState(initial = emptyMap())
    val savesFolder by settings.retroArchSavesUri.collectAsState(initial = null)
    val lastScanAt by settings.lastScanAt.collectAsState(initial = null)
    val scanning by viewModel.isScanningLibrary.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()

    // El progreso se muestra desde que se toca "Escanear" hasta salir de la sección (queda visible
    // el resultado final). Si el escaneo ya estaba en curso al entrar, también.
    var showProgress by remember { mutableStateOf(false) }

    val pickUserFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onUserFolderPicked(context, it) }
    }
    val pickRomFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onRomFolderPicked(context, it) }
    }
    val pickSavesFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onSavesFolderPicked(context, it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Orden de configuración: Carpeta de Usuario → ROMs (necesita la de Usuario) → Escanear
        // (necesita las ROMs).
        SettingsGroup("Carpeta de Usuario") {
            SettingsRow(title = "Assets y configuración", subtitle = folderLabel(userFolder)) {
                GinnyButton("Elegir", primary = userFolder == null, onClick = { pickUserFolder.launch(null) })
            }
        }

        SettingsGroup("Biblioteca") {
            SettingsRow(
                title = "Carpeta de ROMs",
                subtitle = if (userFolder == null) "Elige primero la Carpeta de Usuario" else folderLabel(romFolder),
            ) {
                GinnyButton(
                    "Elegir",
                    enabled = userFolder != null,
                    onClick = { pickRomFolder.launch(null) },
                )
            }
            SettingsDivider()
            SettingsRow(
                title = "Escanear biblioteca",
                // La navegación siempre lee del último escaneo, no del disco.
                subtitle = when {
                    userFolder == null -> "Elige primero la Carpeta de Usuario"
                    romFolder == null && romOverrides.isEmpty() -> "Elige primero la carpeta de ROMs"
                    lastScanAt != null -> "Último escaneo: ${formatScanTimestamp(lastScanAt!!)}"
                    else -> "Todavía no se escaneó: los sistemas aparecen tras el primer escaneo"
                },
            ) {
                if (scanning) {
                    GinnyButton("Detener", onClick = { viewModel.stopScanning() })
                } else {
                    GinnyButton(
                        "Escanear",
                        primary = true,
                        enabled = userFolder != null && (romFolder != null || romOverrides.isNotEmpty()),
                        onClick = {
                            showProgress = true
                            viewModel.scanLibrary(context)
                        },
                    )
                }
            }
            if (showProgress || scanning) {
                SettingsDivider()
                ScanProgress(scanning = scanning, entries = scanProgress)
            }
        }

        SettingsGroup("RetroArch") {
            SettingsRow(
                title = "Estados guardados (opcional)",
                subtitle = folderLabel(savesFolder) +
                    "\nActiva \"savestate_thumbnail_enable\" en RetroArch para ver miniaturas",
            ) {
                GinnyButton("Elegir", onClick = { pickSavesFolder.launch(null) })
            }
        }
    }
}

/**
 * Progreso del escaneo, dentro del grupo Biblioteca: un renglón por sistema terminado (con su
 * cantidad de juegos), que aparece con un fade al llegar. Reemplaza al diálogo del diseño anterior.
 */
@Composable
private fun ScanProgress(scanning: Boolean, entries: List<ScanProgressEntry>) {
    val palette = GinnyThemes.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (scanning) {
                CircularProgressIndicator(
                    color = palette.accent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = (if (scanning) "Escaneando… " else "Escaneo completo · ") +
                    "${entries.size} ${if (entries.size == 1) "sistema" else "sistemas"}",
                fontFamily = GinnyFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = palette.ink,
            )
        }
        entries.forEach { entry ->
            AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                Text(
                    text = "${entry.systemDisplayName}: ${entry.gameCount} ${if (entry.gameCount == 1) "juego" else "juegos"}",
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = palette.muted,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
        }
    }
}

private fun formatScanTimestamp(epochMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMillis))
