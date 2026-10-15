package com.ginny.frontend.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.ginny.frontend.ui.components.settings.FocusMove
import com.ginny.frontend.ui.components.settings.LocalSettingsNav
import com.ginny.frontend.ui.components.settings.settingsFocus
import com.ginny.frontend.ui.theme.lighten
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.GinnyDropdown
import com.ginny.frontend.ui.components.settings.GinnyTextField
import com.ginny.frontend.ui.components.settings.SettingsDivider
import com.ginny.frontend.ui.components.settings.SettingsGroup
import com.ginny.frontend.ui.components.settings.SettingsRow
import com.ginny.frontend.ui.components.settings.SettingsStackedRow
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import kotlinx.coroutines.flow.map

/** Ancho de los desplegables y campos de esta sección (igual que en Interfaz). */
private val SystemControlWidth = 208.dp

/** Ancho de la lista de sistemas (panel izquierdo). */
private val SystemListWidth = 200.dp

/** Alto de cada sistema en la lista y separación entre ellos. */
private val SystemItemHeight = 40.dp
private val SystemItemGap = 4.dp

/**
 * Sección Sistemas (antes "Emuladores"): a la izquierda la lista de sistemas del catálogo, en orden alfabético, y a la
 * derecha las opciones del elegido:
 * - Carpeta de ROMs: `<ROMS>/<carpeta>` (la subcarpeta de la carpeta de ROMs de Carpetas) o la
 *   que el usuario eligió para ese sistema. "Cambiar" elige otra; "Restablecer" vuelve a la de
 *   siempre. Al cambiarla, ese sistema se reescanea.
 * - Emulador (si el sistema ofrece más de uno; si no, se muestra cuál usa).
 * - Core, si el emulador es RetroArch y el sistema tiene cores.
 * - Package alternativo (PS2, Switch), si el emulador no es RetroArch.
 *
 * Con la cruceta, arriba / abajo recorren la lista y las opciones se muestran al instante (la elección sigue al foco);
 * derecha o A entran a las opciones; izquierda vuelve a la lista (y, desde ella, a las secciones).
 * Arranca con [initialSystem] elegido (el sistema que se estaba viendo), o el primero.
 */
@Composable
fun SistemasOptions(viewModel: SettingsViewModel, initialSystem: SystemId?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val romFolder by viewModel.settings.romFolderUri.collectAsState(initial = null)
    val nav = LocalSettingsNav.current
    val palette = GinnyThemes.current

    // Un solo selector de carpetas para todos los sistemas: se recuerda para cuál se abrió.
    // rememberSaveable: sobrevive si Android recrea la Activity mientras el selector está abierto.
    var pickingFor by rememberSaveable { mutableStateOf<SystemId?>(null) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val system = pickingFor
        if (uri != null && system != null) viewModel.onSystemRomFolderPicked(context, system, uri)
        pickingFor = null
    }

    val systems = viewModel.systems
    var selectedId by rememberSaveable { mutableStateOf(initialSystem ?: systems.first().id) }
    val selected = systems.firstOrNull { it.id == selectedId } ?: systems.first()

    // La lista empieza desplazada hasta el sistema elegido, para que no quede fuera de la vista.
    val listScroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        val index = systems.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        listScroll.scrollTo(with(density) { ((SystemItemHeight + SystemItemGap) * index).roundToPx() })
    }

    Row(modifier = modifier) {
        SettingsListPane(modifier = Modifier.width(SystemListWidth).fillMaxHeight(), scroll = listScroll) {
            Column(verticalArrangement = Arrangement.spacedBy(SystemItemGap)) {
                systems.forEach { system ->
                    val isSelected = system.id == selected.id
                    val pill = CircleShape
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SystemItemHeight)
                            .settingsFocus(
                                onFocused = { selectedId = system.id },   // la elección sigue al foco
                                focusOnTap = true,
                                anchor = isSelected,
                                onActivate = { nav?.step(FocusMove.Right) }, // A: entrar a sus opciones
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.background(Brush.horizontalGradient(listOf(palette.accent.lighten(0.08f), palette.accent)), pill)
                                } else {
                                    Modifier
                                },
                            )
                            .pointerInput(system.id) { detectTapGestures { selectedId = system.id } }
                            .padding(horizontal = 16.dp),
                    ) {
                        EllipsisText(
                            text = system.displayName,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.White else palette.ink,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // Línea divisoria entre la lista y las opciones.
        Box(Modifier.fillMaxHeight().width(1.dp).background(palette.ink.copy(alpha = 0.08f)))

        // key: cada sistema empieza con sus opciones desde arriba.
        key(selected.id) {
            SettingsContent(modifier = Modifier.weight(1f)) {
                SystemGroup(
                    system = selected,
                    viewModel = viewModel,
                    romFolder = romFolder,
                    onPickFolder = {
                        pickingFor = selected.id
                        pickFolder.launch(null)
                    },
                    onResetFolder = { viewModel.resetSystemRomFolder(context, selected.id) },
                )
            }
        }
    }
}

@Composable
private fun SystemGroup(
    system: SystemDefinition,
    viewModel: SettingsViewModel,
    romFolder: String?,
    onPickFolder: () -> Unit,
    onResetFolder: () -> Unit,
) {
    val settings = viewModel.settings
    val override by settings.romFolderOverrideFor(system.id).collectAsState(initial = null)
    val emulator by settings.emulatorFor(system.id).collectAsState(initial = system.defaultEmulator)
    val coreId by settings.retroArchCoreFor(system.id).collectAsState(initial = system.defaultRetroArchCoreId)

    SettingsGroup(system.displayName) {
        // Dos celdas en columna: el texto con la ruta arriba y los botones debajo (al lado, la ruta dejaba sin sitio a la etiqueta).
        SettingsStackedRow(
            title = "Carpeta de ROMs",
            subtitle = systemFolderLabel(override, romFolder, system.id.folderName),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Restablecer: siempre a la vista; apagado si ya usa la carpeta por defecto.
                GinnyButton("Restablecer", onClick = onResetFolder, enabled = override != null)
                GinnyButton("Cambiar", onClick = onPickFolder)
            }
        }

        SettingsDivider()
        SettingsRow(title = "Emulador") {
            if (system.allowsEmulatorChoice) {
                GinnyDropdown(
                    selected = emulator,
                    options = system.availableEmulators.map { it.displayName to it },
                    onSelect = { viewModel.setEmulator(system.id, it) },
                    width = SystemControlWidth,
                )
            } else {
                // Un solo emulador posible: solo se informa cuál.
                Text(
                    text = emulator.displayName,
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GinnyThemes.current.muted,
                )
            }
        }

        val isRetroArch = emulator in EmulatorTarget.RETROARCH_VARIANTS
        if (isRetroArch && system.retroArchCores.isNotEmpty()) {
            SettingsDivider()
            SettingsRow(title = "Core") {
                GinnyDropdown(
                    // Si el core guardado ya no existe, se muestra el primero (el que se usaría).
                    selected = system.retroArchCores.firstOrNull { it.id == coreId }?.id ?: system.retroArchCores.first().id,
                    options = system.retroArchCores.map { it.label to it.id },
                    onSelect = { viewModel.setCore(system.id, it) },
                    width = SystemControlWidth,
                )
            }
        } else if (!isRetroArch && system.supportsPackageOverride) {
            // "" = sin package guardado; null = todavía leyendo. El campo toma su valor inicial una
            // sola vez, así que se muestra recién cuando se sabe cuál es.
            val packageFlow = remember(system.id) { settings.packageOverrideFor(system.id).map { it.orEmpty() } }
            val packageOverride by packageFlow.collectAsState(initial = null)
            SettingsDivider()
            SettingsRow(title = "Package alternativo", subtitle = "Para otra app o fork del emulador") {
                packageOverride?.let { stored ->
                    GinnyTextField(
                        value = stored,
                        onValueChange = { viewModel.setPackageOverride(system.id, it) },
                        placeholder = "com.ejemplo.emulador",
                        width = SystemControlWidth,
                    )
                }
            }
        }
    }
}

/**
 * Texto de una línea que, si no cabe, termina en "..." (tres puntos normales). `TextOverflow.Ellipsis` usa el carácter
 * "…" de la fuente, que en M PLUS Rounded queda centrado en vertical y no sobre la base del texto.
 */
@Composable
private fun EllipsisText(text: String, fontSize: TextUnit, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = remember(fontSize) { TextStyle(fontFamily = GinnyFont, fontWeight = FontWeight.ExtraBold, fontSize = fontSize) }
    BoxWithConstraints(modifier = modifier) {
        val maxWidth = constraints.maxWidth
        val shown = remember(text, maxWidth, style) {
            fun fits(s: String) = measurer.measure(s, style, softWrap = false, maxLines = 1).size.width <= maxWidth
            if (fits(text)) {
                text
            } else {
                // El prefijo más largo que, con los puntos, todavía cabe.
                var low = 0
                var high = text.length
                while (low < high) {
                    val mid = (low + high + 1) / 2
                    if (fits(text.take(mid).trimEnd() + "...")) low = mid else high = mid - 1
                }
                text.take(low).trimEnd() + "..."
            }
        }
        Text(text = shown, style = style, color = color, maxLines = 1, softWrap = false)
    }
}
