package com.ginny.frontend.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.ui.theme.adjust
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Menú desplegable propio, dibujado **dentro de la misma ventana** de la aplicación.
 *
 * El `DropdownMenu` de Material abre una ventana emergente aparte del sistema; Android muestra entonces la barra de
 * navegación (se ve la raya inferior) y rompe el modo inmersivo, y las teclas van a esa otra ventana. Este menú, en
 * cambio, es una capa más de la interfaz: se dibuja en [GinnyMenuHost] —el último hijo de `GinnyApp`, encima de todo,
 * incluidos los modales— y recibe las teclas como cualquier otro elemento.
 */

/** Una opción de un menú. [color] la tiñe (ej. "Eliminar" en rojo); [checked] la marca como la elegida (negrita, ✓). */
data class GinnyMenuItem(val label: String, val color: Color? = null, val checked: Boolean = false)

/** Color del texto de la opción elegida: el del tema, más oscuro para que se lea sobre blanco. */
@Composable
private fun accentText(): Color = GinnyThemes.current.accent.adjust(lightness = 0.45f)

/** Alto de cada opción del menú. Fijo, para poder calcular el alto total antes de medir. */
private val MenuItemHeight = 48.dp
private val MenuVerticalPadding = 8.dp
private val MenuCorner = 14.dp
private val MenuGap = 4.dp
private val ScreenMargin = 8.dp

/** Si hay algún menú abierto (MainActivity lo consulta para que Menú / Y no abran Configuración encima). */
object GinnyMenus {
    @Volatile
    var isOpen: Boolean = false
}

/** Una capa del host: lo que dibuja un [GinnyMenu] cuando está abierto. */
@Stable
internal class MenuLayer {
    var content by mutableStateOf<@Composable () -> Unit>({})
}

/** Dónde se dibujan los menús abiertos; lo provee `GinnyApp` (ver [GinnyMenuHost]). */
@Stable
class GinnyMenuHostState {
    internal val layers = mutableStateListOf<MenuLayer>()
}

val LocalGinnyMenuHost = staticCompositionLocalOf<GinnyMenuHostState?> { null }

/** Dibuja los menús abiertos. Va como **último** hijo del Box raíz de la interfaz, para quedar encima de todo. */
@Composable
fun GinnyMenuHost(state: GinnyMenuHostState) {
    state.layers.forEach { layer -> key(layer) { layer.content() } }
}

/**
 * Menú desplegable anclado a un botón.
 *
 * @param expanded si está abierto. Lo controla quien lo usa (ej. la tecla Y abre el menú "Más").
 * @param anchor dónde está el botón, en coordenadas de la raíz (px); el menú se abre debajo o, si no cabe, encima.
 * @param onChoose recibe la posición de la opción elegida (con A o tocándola); quien lo usa lo cierra.
 * @param onDismiss se pide cerrar sin elegir: B, tocar fuera o una de [closeButtons].
 * @param closeButtons botones del mando que, además de B, lo cierran (izquierda; el menú "Más" suma Y).
 *
 * Con el mando, abierto: arriba / abajo mueven la opción resaltada (fondo del acento suave) y A la elige. El menú se
 * queda con todas las teclas mientras está abierto. B lo cierra por el `BackHandler` de su capa (MainActivity entrega B
 * al despachador de "atrás"), no por una tecla.
 */
@Composable
fun GinnyMenu(
    expanded: Boolean,
    anchor: Rect?,
    items: List<GinnyMenuItem>,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
    buttonMapping: ButtonMapping,
    sound: SoundManager?,
    width: Dp,
    initialHighlight: Int = 0,
    closeButtons: Set<LogicalButton> = setOf(LogicalButton.DPAD_LEFT),
) {
    val host = LocalGinnyMenuHost.current ?: return
    val layer = remember { MenuLayer() }
    DisposableEffect(host, layer) {
        host.layers.add(layer)
        onDispose { host.layers.remove(layer) }
    }
    // El contenido se arma aquí (con los datos de quien lo usa) y se dibuja en el host.
    SideEffect {
        layer.content = {
            if (expanded && anchor != null) {
                MenuContent(anchor, items, onChoose, onDismiss, buttonMapping, sound, width, initialHighlight, closeButtons)
            }
        }
    }
}

@Composable
private fun MenuContent(
    anchor: Rect,
    items: List<GinnyMenuItem>,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
    buttonMapping: ButtonMapping,
    sound: SoundManager?,
    width: Dp,
    initialHighlight: Int,
    closeButtons: Set<LogicalButton>,
) {
    val palette = GinnyThemes.current
    val density = LocalDensity.current
    var highlight by remember { mutableIntStateOf(initialHighlight.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val fade = remember { Animatable(0f) }
    // Los bloques de toque no se reinician cuando cambian estas funciones: se leen siempre las últimas.
    val currentOnChoose by rememberUpdatedState(onChoose)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    // B: el despachador de "atrás" de la Activity. Se registra al abrirse (no antes), así tiene prioridad sobre el
    // de Configuración o el de un modal que estén debajo.
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) {
        GinnyMenus.isOpen = true
        onDispose { GinnyMenus.isOpen = false }
    }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        fade.animateTo(1f, tween(120))
    }
    // Mantiene a la vista la opción resaltada (si hay más opciones de las que caben).
    LaunchedEffect(highlight) {
        val itemPx = with(density) { MenuItemHeight.toPx() }
        scroll.animateScrollTo((highlight * itemPx - itemPx).coerceAtLeast(0f).roundToInt())
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Tocar fuera lo cierra; los toques no llegan a lo que hay detrás.
            .pointerInput(Unit) { detectTapGestures { currentOnDismiss() } }
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val button = buttonMapping.buttonForKeyCode(event.nativeKeyEvent.keyCode)
                    when {
                        button == LogicalButton.DPAD_UP -> if (highlight > 0) { highlight--; sound?.playFocusSound() }
                        button == LogicalButton.DPAD_DOWN -> if (highlight < items.lastIndex) { highlight++; sound?.playFocusSound() }
                        button == LogicalButton.A -> { sound?.playActivateSound(); onChoose(highlight) }
                        button in closeButtons -> onDismiss()
                    }
                }
                true // el menú se queda con todas las teclas mientras está abierto
            },
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val margin = with(density) { ScreenMargin.toPx() }
        val gap = with(density) { MenuGap.toPx() }
        val menuWidth = with(density) { width.toPx() }
        val fullHeight = with(density) { (MenuItemHeight * items.size + MenuVerticalPadding * 2).toPx() }

        // Debajo del botón si cabe (o si hay más sitio abajo que arriba); si no, encima. Si ni así cabe, se recorta y desplaza.
        val below = screenHeight - anchor.bottom - gap - margin
        val above = anchor.top - gap - margin
        val openDown = fullHeight <= below || below >= above
        val menuHeight = min(fullHeight, if (openDown) below else above)
        val y = if (openDown) anchor.bottom + gap else anchor.top - gap - menuHeight
        val x = anchor.left.coerceIn(margin, max(margin, screenWidth - menuWidth - margin))

        val shape = RoundedCornerShape(MenuCorner)
        Column(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .graphicsLayer { alpha = fade.value }
                .width(width)
                .height(with(density) { menuHeight.toDp() })
                .shadow(12.dp, shape)
                .background(Color.White, shape) // sin esto el menú sería transparente
                // Los toques dentro del menú no lo cierran (cada opción atiende el suyo).
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(scroll)
                .padding(vertical = MenuVerticalPadding),
        ) {
            items.forEachIndexed { index, item ->
                val highlighted = index == highlight
                val textColor = item.color ?: if (item.checked) accentText() else palette.ink
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MenuItemHeight)
                        .padding(horizontal = 6.dp)
                        // La resaltada: fondo del acento suave, como una píldora dentro del menú.
                        .then(
                            if (highlighted) {
                                Modifier.background(palette.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            },
                        )
                        .pointerInput(index) { detectTapGestures { currentOnChoose(index) } }
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = item.label,
                        fontFamily = GinnyFont,
                        fontWeight = if (item.checked) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 15.sp,
                        color = textColor,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.checked) Text("✓", fontWeight = FontWeight.ExtraBold, color = accentText())
                }
            }
        }
    }
}
