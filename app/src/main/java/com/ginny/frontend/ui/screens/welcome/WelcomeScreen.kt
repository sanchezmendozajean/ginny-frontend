package com.ginny.frontend.ui.screens.welcome

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.ui.components.Container
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.LocalSettingsNav
import com.ginny.frontend.ui.components.settings.SettingsCursorOverlay
import com.ginny.frontend.ui.screens.scrap.rememberModalNav
import com.ginny.frontend.ui.screens.settings.SettingsViewModel
import com.ginny.frontend.ui.screens.settings.folderLabel
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import com.ginny.frontend.ui.util.LocalSoundManager
import com.ginny.frontend.ui.util.appContainer
import kotlinx.coroutines.delay

/** En qué paso de la bienvenida se está. */
private enum class WelcomePhase {
    /** Elegir las carpetas y pulsar Continuar. */
    Folders,

    /** Escaneando: se limpia el contenido y se van mostrando los sistemas encontrados. */
    Scanning,

    /** El escaneo terminó sin encontrar ningún juego. */
    NoGames,
}

/**
 * Pantalla de bienvenida del primer arranque: ocupa el lugar de la interfaz de tres piezas
 * (cabecera / contenido / pie) con un único [Container].
 *
 * 1. **Carpetas**: pide las dos sin las que Ginny no muestra nada —Usuario y ROMs— con los mismos
 *    selectores de Configuración > Carpetas, y un botón **Continuar** que se habilita al tenerlas.
 * 2. **Escaneo**: Continuar limpia el contenido, escanea la biblioteca y va mostrando los sistemas
 *    que encuentra. Al terminar con juegos, la bienvenida se retira y aparece la interfaz de siempre.
 *
 * Se muestra mientras falten carpetas o el escaneo inicial no haya terminado (ver `GinnyApp` y
 * [com.ginny.frontend.data.SettingsRepository.welcomePending]). Con el mando: la cruceta mueve el
 * cursor y A actúa. Menú / Y siguen abriendo Configuración.
 *
 * [active] = false mientras hay un modal encima (Configuración): no atiende teclas.
 */
@Composable
fun WelcomeScreen(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer = appContainer()
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(appContainer) } },
    )
    val settings = viewModel.settings
    val userFolder by settings.userFolderUri.collectAsState(initial = null)
    val romFolder by settings.romFolderUri.collectAsState(initial = null)
    val romOverrides by settings.romFolderOverrides.collectAsState(initial = emptyMap())
    val scanProgress by viewModel.scanProgress.collectAsState()

    var phase by remember { mutableStateOf(WelcomePhase.Folders) }
    // Mismo criterio que "Escanear" en Configuración > Carpetas: la de Usuario y alguna carpeta de ROMs.
    val canContinue = userFolder != null && (romFolder != null || romOverrides.isNotEmpty())

    // Marca la bienvenida como en curso: si la app se cierra tras elegir las carpetas y antes de escanear,
    // la próxima vez vuelve a aparecer en vez de dejar una grilla vacía.
    LaunchedEffect(Unit) { settings.setWelcomePending(true) }

    // Continuar: escanea y, al terminar con juegos, retira la bienvenida (GinnyApp muestra entonces la interfaz).
    LaunchedEffect(phase) {
        if (phase != WelcomePhase.Scanning) return@LaunchedEffect
        val job = viewModel.scanLibrary(context)
        job.join()
        when {
            job.isCancelled -> phase = WelcomePhase.Folders // "Detener" desde Configuración
            viewModel.scanProgress.value.isEmpty() -> phase = WelcomePhase.NoGames
            else -> settings.setWelcomePending(false)
        }
    }

    val nav = rememberModalNav()
    val buttonMapping by settings.buttonMapping.collectAsState(initial = ButtonMapping.DEFAULT)
    nav.buttonMapping = buttonMapping
    nav.sound = LocalSoundManager.current

    // El selector de carpetas del sistema es otra Activity: al volver, el teclado hay que reclamarlo.
    val pickUserFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        nav.reclaimFocus()
        uri?.let { viewModel.onUserFolderPicked(context, it) }
    }
    val pickRomFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        nav.reclaimFocus()
        uri?.let { viewModel.onRomFolderPicked(context, it) }
    }

    // Se queda con el foco de teclas y consume la cruceta (no hay nada detrás que moverle).
    val focusRequester = remember { FocusRequester() }
    nav.reclaimFocus = { runCatching { focusRequester.requestFocus() } }
    LaunchedEffect(active) {
        if (active) nav.reclaimFocus()
    }
    // Con los botones ya colocados, el cursor empieza en el primero. Al escanear no hay controles.
    LaunchedEffect(phase) {
        if (phase == WelcomePhase.Scanning) {
            nav.leave()
        } else {
            delay(150)
            nav.enter()
        }
    }

    Box(
        modifier = modifier
            // Cualquier toque apaga el cursor del mando, sin consumirlo (la cruceta lo vuelve a mostrar).
            .pointerInput(nav) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) nav.hideCursor()
                    }
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (!active) return@onKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    val button = buttonMapping.buttonForKeyCode(event.nativeKeyEvent.keyCode)
                    // Con el cursor apagado, la primera tecla solo lo vuelve a mostrar (A no actúa a ciegas).
                    val wasHidden = !nav.cursorVisible
                    if (wasHidden) nav.showCursor()
                    if (nav.active && !(wasHidden && button == LogicalButton.A)) nav.handleKey(button)
                }
                true
            },
    ) {
        CompositionLocalProvider(LocalSettingsNav provides nav) {
            Container(modifier = Modifier.fillMaxSize(), horizontalPadding = 32.dp) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (phase) {
                        WelcomePhase.Folders -> FoldersStep(
                            userFolder = userFolder,
                            romFolder = romFolder,
                            canContinue = canContinue,
                            onPickUser = { pickUserFolder.launch(null) },
                            onPickRom = { pickRomFolder.launch(null) },
                            onContinue = { phase = WelcomePhase.Scanning },
                        )
                        WelcomePhase.Scanning -> ScanningStep(entries = scanProgress)
                        WelcomePhase.NoGames -> NoGamesStep(onBack = { phase = WelcomePhase.Folders })
                    }
                }
            }
            // El cursor del mando, encima del Container.
            SettingsCursorOverlay(nav = nav, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun FoldersStep(
    userFolder: String?,
    romFolder: String?,
    canContinue: Boolean,
    onPickUser: () -> Unit,
    onPickRom: () -> Unit,
    onContinue: () -> Unit,
) {
    val palette = GinnyThemes.current
    Text(
        text = "¡Bienvenido a Ginny!",
        fontFamily = GinnyFont,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        color = palette.ink,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Antes de comenzar, necesitamos saber dónde están sus archivos.",
        fontFamily = GinnyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = palette.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 520.dp),
    )

    Spacer(Modifier.height(24.dp))
    Text(
        text = "Especifique las siguientes carpetas:",
        fontFamily = GinnyFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp,
        color = palette.ink,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
        StepChoice(
            buttonText = "Usuario",
            caption = folderLabel(userFolder),
            primary = userFolder == null,
            onClick = onPickUser,
        )
        StepChoice(
            buttonText = "ROMs",
            // Como en Configuración > Carpetas: la de ROMs necesita la de Usuario primero.
            caption = if (userFolder == null) "Primero la de Usuario" else folderLabel(romFolder),
            primary = userFolder != null && romFolder == null,
            enabled = userFolder != null,
            onClick = onPickRom,
        )
        StepChoice(
            buttonText = "Continuar",
            // El aviso aparece al habilitarse el botón.
            caption = if (canContinue) "Dé clic en Continuar para iniciar con el escaneo" else "",
            primary = canContinue,
            enabled = canContinue,
            onClick = onContinue,
        )
    }

    Spacer(Modifier.height(24.dp))
    Text(
        text = "Podrá modificarlas después en Configuración.",
        fontFamily = GinnyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = palette.muted,
        textAlign = TextAlign.Center,
    )
}

/**
 * El escaneo en curso: el contenido de antes desaparece y los sistemas encontrados van apareciendo
 * (con su cantidad de juegos), con la lista siempre desplazada hasta el último.
 */
@Composable
private fun ScanningStep(entries: List<com.ginny.frontend.ui.screens.settings.ScanProgressEntry>) {
    val palette = GinnyThemes.current
    val scroll = rememberScrollState()
    LaunchedEffect(entries.size) { scroll.animateScrollTo(scroll.maxValue) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = palette.accent, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Escaneando su biblioteca…",
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            color = palette.ink,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "${entries.size} ${if (entries.size == 1) "sistema encontrado" else "sistemas encontrados"}",
        fontFamily = GinnyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = palette.muted,
    )
    Spacer(Modifier.height(16.dp))
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .verticalScroll(scroll),
    ) {
        entries.forEach { entry ->
            AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                Text(
                    text = "${entry.systemDisplayName}: ${entry.gameCount} ${if (entry.gameCount == 1) "juego" else "juegos"}",
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = palette.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** El escaneo no encontró nada: se explica y se puede volver a elegir carpetas. */
@Composable
private fun NoGamesStep(onBack: () -> Unit) {
    val palette = GinnyThemes.current
    Text(
        text = "No se encontraron juegos",
        fontFamily = GinnyFont,
        fontWeight = FontWeight.Black,
        fontSize = 26.sp,
        color = palette.ink,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Revise que la carpeta de ROMs tenga una subcarpeta por consola (por ejemplo nes o snes) con sus juegos dentro.",
        fontFamily = GinnyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = palette.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 520.dp),
    )
    Spacer(Modifier.height(20.dp))
    GinnyButton(text = "Elegir otras carpetas", onClick = onBack, primary = true)
}

/** Un botón del paso de carpetas con un texto debajo (la ruta elegida, o el aviso de Continuar). */
@Composable
private fun StepChoice(
    buttonText: String,
    caption: String,
    primary: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 200.dp)) {
        GinnyButton(text = buttonText, onClick = onClick, primary = primary, enabled = enabled)
        Spacer(Modifier.height(6.dp))
        Text(
            text = caption,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = GinnyThemes.current.muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
