package com.ginny.frontend.ui.screens.games

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.ComponentActivity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.favoriteKey
import com.ginny.frontend.data.scrap.GameAssets
import com.ginny.frontend.launch.LaunchResult
import com.ginny.frontend.ui.SettingsBackdropBlur
import com.ginny.frontend.ui.components.FaceButton
import com.ginny.frontend.ui.components.FaceButtonIcon
import com.ginny.frontend.ui.components.game.Element
import com.ginny.frontend.ui.components.settings.GinnyDropdown
import com.ginny.frontend.ui.components.settings.GinnyTextField
import com.ginny.frontend.ui.screens.settings.SettingsFadeHeight
import com.ginny.frontend.ui.screens.settings.SettingsModalCornerRadius
import com.ginny.frontend.ui.screens.settings.fadingEdges
import com.ginny.frontend.ui.screens.settings.SettingsModalMargin
import com.ginny.frontend.ui.screens.settings.SettingsScrimColor
import com.ginny.frontend.ui.screens.settings.systemFolderLabel
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import com.ginny.frontend.ui.theme.adjust
import com.ginny.frontend.ui.theme.lighten
import com.ginny.frontend.ui.util.LocalSoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Cuánto desplaza el contenido cada pulsación de arriba/abajo de la cruceta. */
private const val ScrollStepPx = 260f

/** Separación entre la columna izquierda del cuerpo y la caja. */
private val BodyGap = 16.dp

/** "Jugar": cuánto gira la caja (con el sonido) antes de empezar a oscurecer la pantalla. */
private const val LaunchSpinMs = 1000L

/** "Jugar": cuánto tarda la pantalla en quedar negra antes de lanzar el juego. */
private const val LaunchFadeMs = 700

/** Cuánto tarda la pantalla en aclararse al volver del emulador. */
private const val RestoreFadeMs = 500

/** Si pasado este tiempo desde el lanzamiento la app no llegó a ponerse en pausa, se aclara la pantalla. */
private const val LaunchStuckMs = 5000L

/** Rojo de "Eliminar" (OKLCH: tono 25°, el mismo para todos los temas). */
internal val DeleteRed = Color(0xFFC0392B)

/**
 * Modal de un juego, al abrirlo desde la grilla de Juegos (diseño de referencia del usuario,
 * 2026-10-05): portada, nombre y sistema; información scrapeada (sinopsis, estudio, año...) con la
 * caja en 3D a la derecha; archivo (nombre y carpeta); emulador y core del
 * sistema; Favorito (solo táctil); Regresar (B) y Jugar (A). El menú de tres puntos de la esquina
 * inferior izquierda tiene Scrappear y Eliminar.
 *
 * B lo maneja GinnyApp (BackHandler, vía MainActivity), igual que en el resto de la app. A se
 * maneja aquí. El modal se queda con el foco: la cruceta no mueve la grilla de atrás.
 */
@Composable
fun GameModal(
    game: GameEntry?,
    viewModel: GamesViewModel?,
    buttonMapping: ButtonMapping,
    onClose: () -> Unit,
    /** Inicia el scrapeo del juego (Sí = "Todo", reemplazando lo guardado). Lo atiende GinnyApp. */
    onStartScrap: (game: GameEntry, overwrite: Boolean) -> Unit = { _, _ -> },
    /** Hay otro modal encima (el de avance del scrapeo): el modal del juego no responde a teclas. */
    blocked: Boolean = false,
    /** Hay un scrapeo en curso (para refrescar la información al terminar). */
    scrapping: Boolean = false,
) {
    AnimatedVisibility(
        visible = game != null && viewModel != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        // Durante el fade de salida game ya es null: se recuerda el último para seguir dibujándolo.
        val lastGame = remember { mutableStateOf(game) }
        val lastViewModel = remember { mutableStateOf(viewModel) }
        if (game != null) lastGame.value = game
        if (viewModel != null) lastViewModel.value = viewModel
        val shown = lastGame.value ?: return@AnimatedVisibility
        val vm = lastViewModel.value ?: return@AnimatedVisibility

        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(SettingsScrimColor)
                // Tocar la cortina (fuera de la tarjeta) cierra; los toques no pasan a la grilla.
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        ) {
            // La tarjeta ocupa la pantalla menos el mismo margen que el modal de Configuración, igual
            // en los cuatro lados; los desplegables se reparten su ancho.
            val cardWidth = maxWidth - SettingsModalMargin * 2

            // Oscurecimiento de la pantalla al lanzar el juego (0 = nada, 1 = negro).
            val darkness = remember(shown) { Animatable(0f) }

            // Ícono nuevo elegido en "Cambiar icono" (la ficha lo muestra sin esperar a que el juego se vuelva a leer).
            var iconOverride by remember(shown) { mutableStateOf<android.net.Uri?>(null) }

            // Diálogo abierto desde el menú de tres puntos (null = ninguno). Cada juego empieza sin diálogo.
            var dialog by remember(shown) { mutableStateOf<GameDialog?>(null) }
            // Con un diálogo encima, la tarjeta se desenfoca (si no, se transparenta por la cortina).
            val cardBlur by animateDpAsState(if (dialog != null) SettingsBackdropBlur else 0.dp, label = "gameCardBlur")
            // B cierra primero el diálogo (este callback se registra después que el de GinnyApp).
            BackHandler(enabled = dialog != null) { dialog = null }

            GameCard(
                game = shown,
                iconUri = iconOverride ?: shown.iconUri,
                viewModel = vm,
                buttonMapping = buttonMapping,
                onClose = onClose,
                onOpenDialog = { dialog = it },
                inputBlocked = dialog != null || blocked,
                cardWidth = cardWidth,
                scrapping = scrapping,
                darkness = darkness,
                modifier = Modifier.blur(cardBlur),
            )

            when (dialog) {
                GameDialog.ChangeIcon -> IconPickerModal(
                    game = shown,
                    onClose = { dialog = null },
                    onChanged = { iconOverride = it },
                )
                GameDialog.ScrapOptions -> ScrapGameDialog(
                    onCancel = { dialog = null },
                    onStart = { overwrite ->
                        dialog = null
                        onStartScrap(shown, overwrite)
                    },
                )
                GameDialog.ConfirmDelete -> {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    ConfirmDeleteDialog(
                        gameName = shown.displayName,
                        onCancel = { dialog = null },
                        onConfirm = {
                            dialog = null
                            scope.launch {
                                vm.deleteGame(context, shown)
                                onClose()
                            }
                        },
                    )
                }
                null -> Unit
            }

            // Fundido a negro de toda la pantalla (encima de todo, diálogos incluidos); no deja pasar toques.
            if (darkness.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = darkness.value))
                        .pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    game: GameEntry,
    /** Ícono que se muestra en la cabecera (puede ser uno recién elegido). */
    iconUri: android.net.Uri?,
    viewModel: GamesViewModel,
    buttonMapping: ButtonMapping,
    /** Ancho de la tarjeta (sin los márgenes de pantalla): reparte el espacio entre la información y la caja. */
    cardWidth: Dp,
    onClose: () -> Unit,
    /** Abre un diálogo del menú de tres puntos. */
    onOpenDialog: (GameDialog) -> Unit,
    /** Hay un diálogo u otro modal encima: la tarjeta cede el foco y lo recupera al quedar libre. */
    inputBlocked: Boolean,
    /** Hay un scrapeo en curso: al terminar se vuelve a leer lo guardado (información y caja). */
    scrapping: Boolean,
    /** Oscurecimiento de toda la pantalla (0 = nada, 1 = negro) al lanzar el juego; lo dibuja [GameModal]. */
    darkness: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    val palette = GinnyThemes.current
    val context = LocalContext.current
    val sound = LocalSoundManager.current
    val scope = rememberCoroutineScope()
    val settings = viewModel.settingsRepository
    val system = SystemCatalog.byId.getValue(game.systemId)

    val favorites by viewModel.favoriteGameKeys.collectAsState()
    val isFavorite = game.favoriteKey() in favorites
    val emulator by settings.emulatorFor(system.id).collectAsState(initial = system.defaultEmulator)
    val coreId by settings.retroArchCoreFor(system.id).collectAsState(initial = system.defaultRetroArchCoreId)
    val romFolder by settings.romFolderUri.collectAsState(initial = null)
    val override by settings.romFolderOverrideFor(system.id).collectAsState(initial = null)

    // Lo guardado del juego (metadatos y caja). Se relee al abrir y cuando termina un scrapeo; mientras
    // tanto se conserva lo anterior para que la caja no desaparezca y vuelva a aparecer.
    var assets by remember(game) { mutableStateOf<GameAssets?>(null) }
    LaunchedEffect(game, scrapping) { assets = viewModel.loadGameAssets(game) }
    val scroll = rememberScrollState()
    val videoVolume by settings.videoVolume.collectAsState(initial = 0.5f)
    // Con el video encendido, la caja se desvanece y el video se reproduce en su lugar.
    var videoOn by remember(game) { mutableStateOf(false) }
    // La caja solo se dibuja en pantallas anchas y si el juego tiene alguna cara guardada.
    val contentWidth = cardWidth - 40.dp
    val showBox = shouldShowSideBox(assets, contentWidth)
    val boxWidth = sideBoxWidth(contentWidth)
    val hasVideo = showBox && assets?.video != null
    val videoPlaying = videoOn && hasVideo
    // Mientras suena el video, la música de fondo se pausa (y vuelve al apagarlo o al cerrar el modal).
    DisposableEffect(videoPlaying) {
        if (videoPlaying) sound?.pauseMusic()
        onDispose { if (videoPlaying) sound?.resumeMusic() }
    }

    // Ancho de cada desplegable de Emulador (dos columnas iguales). Se calcula sobre la columna izquierda
    // —la caja, si está, le quita su parte— menos el margen de la sección (2×10), el borde (2) y el hueco (12).
    val leftColumnWidth = if (showBox) contentWidth - boxWidth - BodyGap else contentWidth
    val controlWidth = (leftColumnWidth - 20.dp - 2.dp - 12.dp) / 2

    // Secuencia de "Jugar": suena el efecto y la caja recibe un golpe de giro; pasado [LaunchSpinMs] la pantalla
    // se funde a negro y recién entonces se lanza el emulador. B (cierra el modal) la cancela.
    //
    // El modal **no se cierra** al lanzar: la capa negra vive en él y, si desapareciera, la interfaz volvería a
    // verse mientras Android anima el cambio de aplicación. Al regresar al frontend se aclara solo (ver abajo).
    var launching by remember(game) { mutableStateOf(false) }
    // Menú "Más" (Scrappear, Cambiar icono, Eliminar): se abre con el botón o con Y.
    var menuOpen by remember(game) { mutableStateOf(false) }
    var spinTrigger by remember(game) { mutableIntStateOf(0) }
    /** Ya se pidió lanzar el emulador (la pantalla está en negro esperando a que la app pase a segundo plano). */
    var launchRequested by remember(game) { mutableStateOf(false) }
    /** La app llegó a ponerse en pausa después de pedir el lanzamiento (el emulador tomó la pantalla). */
    var leftApp by remember(game) { mutableStateOf(false) }

    /** Aclara la pantalla otra vez (al volver del emulador, o si el lanzamiento no llegó a ocurrir). */
    suspend fun restoreScreen() {
        launchRequested = false
        leftApp = false
        darkness.animateTo(0f, tween(RestoreFadeMs))
        launching = false
    }

    // Al volver del emulador (la actividad se reanuda tras haber pasado a segundo plano), la pantalla se aclara.
    val lifecycleOwner = remember(context) { context.findComponentActivity() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> if (launchRequested) leftApp = true
                Lifecycle.Event.ON_RESUME -> if (launchRequested && leftApp) scope.launch { restoreScreen() }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    fun play() {
        if (launching) return // un segundo toque (o A) no reinicia la secuencia
        launching = true
        videoOn = false
        sound?.playLaunchSound()
        spinTrigger++
        scope.launch {
            delay(LaunchSpinMs)
            darkness.animateTo(1f, tween(LaunchFadeMs, easing = LinearEasing))
            launchRequested = true
            when (val result = viewModel.launch(context, game)) {
                LaunchResult.Launched -> {
                    // Seguro por si el emulador nunca llega a tomar la pantalla: no dejar al usuario en negro.
                    delay(LaunchStuckMs)
                    if (!leftApp) restoreScreen()
                }
                is LaunchResult.EmulatorNotInstalled -> {
                    Toast.makeText(context, "${result.emulator.displayName} no está instalado.", Toast.LENGTH_LONG).show()
                    restoreScreen()
                }
                is LaunchResult.Failed -> {
                    Toast.makeText(context, "No se pudo abrir: ${result.message}", Toast.LENGTH_LONG).show()
                    restoreScreen()
                }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    // Al abrirse y cada vez que un diálogo u otro modal que estaba encima se cierra.
    LaunchedEffect(inputBlocked) { if (!inputBlocked) focusRequester.requestFocus() }
    val currentPlay by rememberUpdatedState(::play)
    val currentToggleVideo by rememberUpdatedState({ if (hasVideo && !launching) videoOn = !videoOn })

    val shape = RoundedCornerShape(SettingsModalCornerRadius)
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            // Mismo margen que el modal de Configuración, igual en horizontal y en vertical.
            .padding(SettingsModalMargin)
            .fillMaxSize()
            .shadow(24.dp, shape)
            .background(Color.White, shape)
            // Los toques dentro de la tarjeta no cierran el modal.
            .pointerInput(Unit) { detectTapGestures { } }
            .focusRequester(focusRequester)
            .focusable()
            // A = Jugar; X = Video; Y = menú "Más"; arriba/abajo de la cruceta desplazan el contenido. Las demás teclas se consumen
            // (B lo maneja MainActivity antes).
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (buttonMapping.buttonForKeyCode(event.nativeKeyEvent.keyCode)) {
                        LogicalButton.A -> currentPlay()
                        LogicalButton.X -> currentToggleVideo()
                        LogicalButton.Y -> menuOpen = true
                        LogicalButton.DPAD_DOWN -> scope.launch { scroll.animateScrollBy(ScrollStepPx) }
                        LogicalButton.DPAD_UP -> scope.launch { scroll.animateScrollBy(-ScrollStepPx) }
                        else -> Unit
                    }
                }
                true
            }
            .padding(20.dp),
    ) {
        // ── Cuerpo: [encabezado + contenido con scroll] a la izquierda y la caja a la derecha ──────
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(BodyGap)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // ── Encabezado: portada · nombre y sistema · cerrar ─────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Element(size = 72.dp, title = game.displayName, image = iconUri)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = game.displayName,
                            fontFamily = GinnyFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = palette.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(palette.background, CircleShape)
                                .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        Brush.linearGradient(listOf(palette.accent.lighten(0.1f), palette.accent)),
                                        RoundedCornerShape(5.dp),
                                    ),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(system.displayName, fontFamily = GinnyFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.muted)
                        }
                    }
                }

                // ── Contenido: se desplaza; arriba y abajo se desvanece lo que sigue por ese lado ─────────
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fadingEdges(scroll, SettingsFadeHeight)
                        .verticalScroll(scroll),
                ) {
                    // Información del juego; Archivo y Emulador, debajo. La caja va aparte, a la derecha.
                    GameInfoPanel(assets)

                    // ── Archivo ─────────────────────────────────────────────────────────────────────────
                    ModalSection("Archivo") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                text = game.romFileName,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = systemFolderLabel(override, romFolder, system.id.folderName) + "/",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = palette.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // ── Emulador (es del sistema: cambia para todos sus juegos) ─────────────────────────
                    ModalSection("Emulador") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            LabeledControl("Aplicación") {
                                GinnyDropdown(
                                    selected = emulator,
                                    options = system.availableEmulators.map { it.displayName to it },
                                    onSelect = { viewModel.setEmulator(system.id, it) },
                                    width = controlWidth,
                                )
                            }
                            LabeledControl("Core") {
                                val isRetroArch = emulator in EmulatorTarget.RETROARCH_VARIANTS
                                when {
                                    isRetroArch && system.retroArchCores.isNotEmpty() -> GinnyDropdown(
                                        selected = system.retroArchCores.firstOrNull { it.id == coreId }?.id ?: system.retroArchCores.first().id,
                                        options = system.retroArchCores.map { it.label to it.id },
                                        onSelect = { viewModel.setCore(system.id, it) },
                                        width = controlWidth,
                                    )
                                    system.supportsPackageOverride -> {
                                        val packageFlow = remember(system.id) { settings.packageOverrideFor(system.id).map { it.orEmpty() } }
                                        val stored by packageFlow.collectAsState(initial = null)
                                        stored?.let {
                                            GinnyTextField(
                                                value = it,
                                                onValueChange = { pkg -> viewModel.setPackageOverride(system.id, pkg) },
                                                placeholder = "Package…",
                                                width = controlWidth,
                                            )
                                        }
                                    }
                                    else -> Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier
                                            .width(controlWidth)
                                            .height(40.dp)
                                            .border(1.dp, palette.ink.copy(alpha = 0.08f), CircleShape)
                                            .padding(horizontal = 16.dp),
                                    ) {
                                        Text("App independiente", fontFamily = GinnyFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Caja en 3D: a la derecha, a toda la altura del cuerpo y siempre visible (no se desplaza con el
            // contenido). Solo en pantallas anchas y si el juego tiene alguna cara de caja guardada.
            val boxAssets = assets
            if (showBox && boxAssets != null) {
                GameSidePanel(
                    assets = boxAssets,
                    videoOn = videoPlaying,
                    videoVolume = videoVolume,
                    onVideoStopped = { videoOn = false },
                    modifier = Modifier.width(boxWidth).fillMaxHeight(),
                    spinTrigger = spinTrigger,
                )
            }
        }

        // ── Acciones ────────────────────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Esquina inferior izquierda: menú "Más" (Y).
            GameMenuButton(
                expanded = menuOpen,
                onExpandedChange = { menuOpen = it },
                buttonMapping = buttonMapping,
                onScrap = { onOpenDialog(GameDialog.ScrapOptions) },
                onChangeIcon = { onOpenDialog(GameDialog.ChangeIcon) },
                onDelete = { onOpenDialog(GameDialog.ConfirmDelete) },
                onClosed = { runCatching { focusRequester.requestFocus() } },
            )
            // Favorito: solo táctil (sin botón del mando).
            PillButton(
                background = if (isFavorite) palette.accent.copy(alpha = 0.18f) else palette.background,
                onClick = { scope.launch { viewModel.toggleFavorite(game) } },
            ) {
                Text(if (isFavorite) "★" else "☆", fontSize = 16.sp, color = accentText(palette.accent))
                Text(if (isFavorite) "En favoritos" else "Favorito", style = buttonText(accentText(palette.accent)))
            }
            Spacer(Modifier.weight(1f))

            // Video (botón X): solo si el juego tiene video. Con el video encendido, vuelve a la caja.
            if (hasVideo) {
                PillButton(background = palette.background, onClick = { videoOn = !videoOn }) {
                    FaceButtonIcon(FaceButton.X, size = 20.dp)
                    Text(if (!videoPlaying) "Video" else if (assets?.hasBox == true) "Caja" else "Detener", style = buttonText(palette.ink))
                }
            }

            // Regresar (B) y Jugar (A): con su botón del mando, y también táctiles.
            PillButton(background = palette.background, onClick = onClose) {
                FaceButtonIcon(FaceButton.B, size = 20.dp)
                Text("Regresar", style = buttonText(palette.ink))
            }
            PillButton(
                background = palette.accent,
                brush = Brush.horizontalGradient(listOf(palette.accent.lighten(0.08f), palette.accent)),
                elevated = true,
                onClick = ::play,
            ) {
                FaceButtonIcon(FaceButton.A, tint = Color.White, size = 20.dp)
                Text("Jugar", style = buttonText(Color.White).copy(fontSize = 16.sp))
            }
        }
    }
}

/** Título de sección en mayúsculas + tarjeta con borde fino. */
@Composable
internal fun ModalSection(title: String, content: @Composable () -> Unit) {
    val palette = GinnyThemes.current
    val shape = RoundedCornerShape(18.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            fontFamily = GinnyFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            color = accentText(palette.accent),
            modifier = Modifier.padding(start = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.ink.copy(alpha = 0.08f), shape),
        ) { content() }
    }
}

/** Un control con su etiqueta chica arriba ("Aplicación", "Core"). */
@Composable
private fun LabeledControl(label: String, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = GinnyThemes.current.muted,
            modifier = Modifier.padding(start = 6.dp),
        )
        control()
    }
}

/** Botón píldora con contenido libre (ícono + texto). [brush] tiene prioridad sobre [background]. */
@Composable
private fun PillButton(
    background: Color,
    onClick: () -> Unit,
    brush: Brush? = null,
    elevated: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = GinnyThemes.current
    val currentOnClick by rememberUpdatedState(onClick)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(44.dp)
            .then(if (elevated) Modifier.shadow(6.dp, CircleShape, spotColor = palette.accent) else Modifier)
            .then(if (brush != null) Modifier.background(brush, CircleShape) else Modifier.background(background, CircleShape))
            .pointerInput(Unit) { detectTapGestures { currentOnClick() } }
            .padding(horizontal = 16.dp),
        content = content,
    )
}

/** Acento oscurecido, para textos en el color del tema. */
private fun accentText(accent: Color): Color = accent.adjust(lightness = 0.45f)

private fun buttonText(color: Color) = androidx.compose.ui.text.TextStyle(
    fontFamily = GinnyFont,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 14.sp,
    color = color,
)

/** Diálogos que abre el menú "Más" del modal del juego. */
enum class GameDialog { ScrapOptions, ConfirmDelete, ChangeIcon }

/** Una opción del menú "Más". */
private class MenuEntry(val label: String, val color: Color, val action: () -> Unit)

/**
 * Botón "Más" (con el ícono de Y) en la esquina del modal. Abre un menú con "Scrappear", "Cambiar icono"
 * y "Eliminar" (en rojo). [expanded] lo controla el modal: Y también lo abre.
 *
 * Con el mando, ya abierto: arriba / abajo mueven la opción resaltada, A la elige y B (o Y, o izquierda) lo cierra.
 */
@Composable
private fun GameMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    buttonMapping: ButtonMapping,
    onScrap: () -> Unit,
    onChangeIcon: () -> Unit,
    onDelete: () -> Unit,
    /** El menú se cerró (con o sin elegir): el modal recupera el foco de teclas. */
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = GinnyThemes.current
    val sound = LocalSoundManager.current
    val entries = listOf(
        MenuEntry("Scrappear", palette.ink, onScrap),
        MenuEntry("Cambiar icono", palette.ink, onChangeIcon),
        MenuEntry("Eliminar", DeleteRed, onDelete),
    )
    var highlight by remember { mutableIntStateOf(0) }
    val menuFocus = remember { FocusRequester() }

    fun close() {
        onExpandedChange(false)
        onClosed()
    }

    fun choose(entry: MenuEntry) {
        onExpandedChange(false)
        onClosed()
        entry.action()
    }

    Box(modifier = modifier) {
        PillButton(background = palette.background, onClick = { onExpandedChange(true) }) {
            FaceButtonIcon(FaceButton.Y, size = 20.dp)
            Text("Más", style = buttonText(palette.ink))
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = ::close,
            modifier = Modifier
                .background(Color.White) // sin esto, Material usa un fondo lila por defecto
                .focusRequester(menuFocus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent true
                    when (buttonMapping.buttonForKeyCode(event.nativeKeyEvent.keyCode)) {
                        LogicalButton.DPAD_UP -> if (highlight > 0) { highlight--; sound?.playFocusSound() }
                        LogicalButton.DPAD_DOWN -> if (highlight < entries.lastIndex) { highlight++; sound?.playFocusSound() }
                        LogicalButton.A -> { sound?.playActivateSound(); choose(entries[highlight]) }
                        LogicalButton.B, LogicalButton.Y, LogicalButton.DPAD_LEFT -> close()
                        else -> Unit
                    }
                    true // el menú se queda con todas las teclas mientras está abierto
                },
        ) {
            // Ya en pantalla: empieza resaltando la primera opción y toma el foco de teclas.
            LaunchedEffect(Unit) {
                highlight = 0
                runCatching { menuFocus.requestFocus() }
            }
            entries.forEachIndexed { index, entry ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(entry.label, fontFamily = GinnyFont, fontWeight = FontWeight.Bold, color = entry.color) },
                    onClick = { choose(entry) },
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .then(
                            if (index == highlight) {
                                Modifier.background(palette.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

/** La ComponentActivity que hay detrás de un Context (el de Compose puede venir envuelto), o null. */
private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}
