package com.ginny.frontend.ui.components.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.ginny.frontend.audio.SoundManager
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.ui.components.game.Cursor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Hacia dónde se mueve el foco entre los controles de Configuración. */
enum class FocusMove { Up, Down, Left, Right }

/**
 * Qué control recibe el foco al moverlo en una dirección. Solo geometría (sin Compose), para
 * probarla aparte.
 *
 * - Arriba / Abajo: la fila más cercana en esa dirección (dos controles están en la misma fila si sus
 *   centros verticales difieren menos que [rowTolerance]) y, dentro de ella, el de centro horizontal
 *   más cercano al actual.
 * - Izquierda / Derecha: el control más cercano de la misma fila, hacia ese lado.
 *
 * Devuelve el índice en [rects], o null si no hay a dónde ir.
 */
object FocusSearch {
    fun next(rects: List<Rect>, current: Int, move: FocusMove, rowTolerance: Float): Int? {
        val cur = rects[current].center
        val others = rects.indices.filter { it != current }
        return when (move) {
            FocusMove.Up, FocusMove.Down -> {
                val sign = if (move == FocusMove.Up) -1 else 1
                val inDirection = others.filter { (rects[it].center.y - cur.y) * sign > rowTolerance }
                val nearestRow = inDirection.minOfOrNull { abs(rects[it].center.y - cur.y) } ?: return null
                inDirection
                    .filter { abs(rects[it].center.y - cur.y) <= nearestRow + rowTolerance }
                    .minByOrNull { abs(rects[it].center.x - cur.x) }
            }
            FocusMove.Left, FocusMove.Right -> {
                val sign = if (move == FocusMove.Left) -1 else 1
                others
                    .filter { abs(rects[it].center.y - cur.y) <= rowTolerance && (rects[it].center.x - cur.x) * sign > 0 }
                    .minByOrNull { abs(rects[it].center.x - cur.x) }
            }
        }
    }
}

/** Un control de Configuración que se puede enfocar con la cruceta. Lo crea [settingsFocus]. */
@Stable
class SettingsFocusItem {
    internal var coords: LayoutCoordinates? = null

    /** Sube la primera vez que se coloca el control: el cursor lo lee para ubicarse en cuanto hay dónde. */
    internal var placedTick by mutableIntStateOf(0)

    /** Tamaño actual en píxeles (el cursor lo envuelve). */
    var size by mutableStateOf(IntSize.Zero)
        internal set

    /** Izquierda / derecha sobre este control; devuelven true si lo usaron (ej. un deslizador). */
    internal var onLeft: (() -> Boolean)? = null
    internal var onRight: (() -> Boolean)? = null

    /** Botón A sobre este control. */
    internal var onActivate: () -> Unit = {}

    /** false = el control se puede enfocar pero está apagado: A no hace nada (ni suena). */
    internal var activatable: Boolean = true

    internal fun bounds(): Rect? = coords?.takeIf { it.isAttached }?.rectInRoot()
}

/**
 * Dónde está este layout en la raíz, **sin recortarlo** a lo que se ve. (`boundsInRoot` recorta a la
 * zona visible del panel con desplazamiento: los controles que quedaron fuera saldrían apilados en el
 * borde y la búsqueda del vecino —o del primero de la lista— elegiría uno cualquiera.)
 */
internal fun LayoutCoordinates.rectInRoot(): Rect = Rect(positionInRoot(), size.toSize())

/** Distancia que se desplaza el contenido con arriba/abajo cuando no hay otro control a donde ir. */
private const val ScrollStepPx = 240f

/**
 * Foco por cruceta sobre los controles de la sección abierta de Configuración. Los controles se
 * registran solos (ver [settingsFocus]); [SettingsContent][com.ginny.frontend.ui.screens.settings.SettingsContent]
 * dibuja el cursor ([SettingsCursorOverlay]) y desplaza el panel para que el control enfocado se vea.
 *
 * [active] indica que el foco está en las opciones (si no, está en la lista de secciones y no hay
 * cursor). Las teclas las entrega el modal con [handleKey].
 */
@Stable
class SettingsNav(
    private val scope: CoroutineScope,
    /** Tolerancia, en píxeles, para decir que dos controles están en la misma fila. */
    private val rowTolerancePx: Float,
    /** Margen, en píxeles, que se deja entre el control enfocado y el borde del panel al desplazar. */
    private val edgeMarginPx: Float,
) {
    private val items = mutableListOf<SettingsFocusItem>()

    var focused by mutableStateOf<SettingsFocusItem?>(null)
        private set

    var active by mutableStateOf(false)
        private set

    /** Cuántos controles hay registrados en la sección abierta. */
    private var itemCount by mutableIntStateOf(0)

    /**
     * Si el cursor se ve. Al tocar la pantalla se apaga (con un fundido) y vuelve con la primera tecla
     * de cruceta; así no queda un cursor de mando sobre una pantalla que se maneja con el dedo.
     */
    var cursorVisible by mutableStateOf(true)
        private set

    fun showCursor() { cursorVisible = true }
    fun hideCursor() { cursorVisible = false }

    // ── Lista de secciones: el cursor también está ahí cuando el foco es suyo ────────────────────
    private val sidebarItems = mutableMapOf<Int, SettingsFocusItem>()

    /** Sección elegida en la barra lateral (su posición en la lista). */
    var sidebarIndex by mutableIntStateOf(0)
        private set

    /** El "control" de la sección número [index] de la barra lateral (lo registra la barra con [cursorTarget]). */
    fun sidebarItem(index: Int): SettingsFocusItem = sidebarItems.getOrPut(index) { SettingsFocusItem() }

    /** Cambia la sección elegida: el cursor se desliza hasta ella. */
    fun selectSection(index: Int) {
        if (index == sidebarIndex) return
        cursorFrom = if (cursorVisible) lastCursorRoot else null
        sidebarIndex = index
    }

    /**
     * Sobre qué va el cursor: con el foco en la lista de secciones, la elegida; con el foco en las
     * opciones, el control enfocado (o la sección, si la sección no tiene controles, como Créditos).
     */
    val cursorTarget: SettingsFocusItem?
        get() = if (!active) sidebarItem(sidebarIndex) else focused ?: if (itemCount == 0) sidebarItem(sidebarIndex) else null

    var buttonMapping: ButtonMapping = ButtonMapping.DEFAULT
    var sound: SoundManager? = null

    /** Panel con las opciones (lo registra SettingsContent). */
    internal var scroll: ScrollState? = null
    internal var viewport: LayoutCoordinates? = null

    /** Pide el foco de teclas para el modal (lo registra SettingsModal). */
    var reclaimFocus: () -> Unit = {}

    /** Dónde se dibujó el cursor por última vez (px, en la raíz) y desde dónde desliza al cambiar de control. */
    internal var lastCursorRoot: Offset? = null
    internal var cursorFrom: Offset? = null

    /** Mientras se escribe en un campo de texto: cómo dejar de escribir. */
    private var stopEditing: (() -> Unit)? = null
    val editing: Boolean get() = stopEditing != null

    fun beginEditing(stop: () -> Unit) {
        stopEditing = stop
    }

    fun endEditing() {
        if (stopEditing != null) {
            stopEditing = null
            reclaimFocus()
        }
    }

    internal fun register(item: SettingsFocusItem) {
        if (item !in items) items += item
        itemCount = items.size
    }

    internal fun unregister(item: SettingsFocusItem) {
        items -= item
        itemCount = items.size
        if (focused === item) {
            // Se queda con su lugar: si otro control aparece ahí mismo (ej. "Escanear" pasa a "Detener"),
            // el foco se le pasa (ver [itemPlaced]).
            lostBounds = focusedBounds
            focused = null
        }
    }

    /** Dónde estaba el control enfocado la última vez que se colocó, y dónde estaba el que se perdió. */
    private var focusedBounds: Rect? = null
    private var lostBounds: Rect? = null

    /** Un control acaba de colocarse (o moverse). */
    internal fun itemPlaced(item: SettingsFocusItem) {
        val bounds = item.bounds() ?: return
        if (item === focused) {
            focusedBounds = bounds
            return
        }
        val lost = lostBounds
        if (active && focused == null && lost != null && lost.overlaps(bounds)) {
            lostBounds = null
            cursorFrom = null
            focused = item
        }
    }

    /** Controles ya colocados, de arriba abajo y de izquierda a derecha. */
    private fun placed(): List<Pair<SettingsFocusItem, Rect>> =
        items.mapNotNull { item -> item.bounds()?.let { item to it } }
            .sortedWith(compareBy({ it.second.center.y.roundToInt() / 8 }, { it.second.center.x }))

    /** El foco pasa a las opciones, empezando por el primer control. */
    fun enter() {
        active = true
        cursorFrom = if (cursorVisible) lastCursorRoot else null // desde la sección hasta el primer control
        lostBounds = null
        focused = placed().firstOrNull()?.first
        // El panel pudo quedar desplazado de una visita anterior: se empieza desde arriba.
        scroll?.let { s -> scope.launch { s.animateScrollTo(0) } }
    }

    /** El foco vuelve a la lista de secciones. */
    fun leave() {
        stopEditing?.invoke()
        stopEditing = null
        active = false
        focused = null
        cursorFrom = if (cursorVisible) lastCursorRoot else null // desde el control hasta la sección
        lostBounds = null
    }

    /**
     * Botón B con el foco en las opciones. Primero deja de escribir, si se estaba escribiendo; si no,
     * devuelve el foco a la lista de secciones. Siempre lo atiende (devuelve true).
     */
    fun back(): Boolean {
        if (editing) {
            stopEditing?.invoke()
            return true
        }
        leave()
        return true
    }

    private fun focus(item: SettingsFocusItem) {
        cursorFrom = if (cursorVisible) lastCursorRoot else null
        focused = item
        ensureVisible(item)
    }

    private fun move(direction: FocusMove): Boolean {
        val list = placed()
        val current = focused?.let { f -> list.indexOfFirst { it.first === f } } ?: -1
        if (current < 0) {
            list.firstOrNull()?.let { focus(it.first) }
            return list.isNotEmpty()
        }
        val target = FocusSearch.next(list.map { it.second }, current, direction, rowTolerancePx) ?: return false
        focus(list[target].first)
        return true
    }

    /** Desplaza el panel lo justo para que [item] se vea; en la primera / última fila, hasta el borde. */
    private fun ensureVisible(item: SettingsFocusItem) {
        val scroll = scroll ?: return
        val viewport = viewport?.takeIf { it.isAttached } ?: return
        scope.launch {
            val list = placed()
            val index = list.indexOfFirst { it.first === item }
            if (index < 0) return@launch
            val rects = list.map { it.second }
            val box = rects[index]
            val view = viewport.rectInRoot()
            when {
                FocusSearch.next(rects, index, FocusMove.Up, rowTolerancePx) == null -> scroll.animateScrollTo(0)
                FocusSearch.next(rects, index, FocusMove.Down, rowTolerancePx) == null -> scroll.animateScrollTo(scroll.maxValue)
                box.top < view.top + edgeMarginPx -> scroll.animateScrollBy(box.top - (view.top + edgeMarginPx))
                box.bottom > view.bottom - edgeMarginPx -> scroll.animateScrollBy(box.bottom - (view.bottom - edgeMarginPx))
            }
        }
    }

    private fun scrollBy(delta: Float) {
        val scroll = scroll ?: return
        scope.launch { scroll.animateScrollBy(delta) }
    }

    /** Un botón del mando con el foco en las opciones. */
    fun handleKey(button: LogicalButton?) {
        if (editing) {
            // Escribiendo: A termina; arriba/abajo terminan y pasan al control vecino.
            when (button) {
                LogicalButton.A -> stopEditing?.invoke()
                LogicalButton.DPAD_UP -> { stopEditing?.invoke(); if (move(FocusMove.Up)) sound?.playFocusSound() }
                LogicalButton.DPAD_DOWN -> { stopEditing?.invoke(); if (move(FocusMove.Down)) sound?.playFocusSound() }
                else -> Unit
            }
            return
        }
        when (button) {
            LogicalButton.DPAD_UP -> if (move(FocusMove.Up)) sound?.playFocusSound() else scrollBy(-ScrollStepPx)
            LogicalButton.DPAD_DOWN -> if (move(FocusMove.Down)) sound?.playFocusSound() else scrollBy(ScrollStepPx)
            LogicalButton.DPAD_LEFT -> {
                val item = focused
                when {
                    item?.onLeft?.invoke() == true -> Unit
                    move(FocusMove.Left) -> sound?.playFocusSound()
                    else -> leave() // no hay más a la izquierda: de vuelta a la lista de secciones
                }
            }
            LogicalButton.DPAD_RIGHT -> {
                val item = focused
                if (item?.onRight?.invoke() != true && move(FocusMove.Right)) sound?.playFocusSound()
            }
            LogicalButton.A -> focused?.takeIf { it.activatable }?.let {
                sound?.playActivateSound()
                it.onActivate()
            }
            else -> Unit
        }
    }
}

/** El [SettingsNav] de la Configuración abierta; null fuera de ella (los controles funcionan solo con toque). */
val LocalSettingsNav = staticCompositionLocalOf<SettingsNav?> { null }

/**
 * Hace que un control se pueda enfocar con la cruceta dentro de Configuración (no hace nada fuera de
 * ella). Va **al principio** de la cadena de modificadores del control, o justo después de los que
 * fijan su tamaño, para que el cursor lo envuelva a él y no a su margen.
 *
 * [onLeft] / [onRight]: lo que hacen izquierda / derecha sobre el control (true = lo usó; si no, el
 * foco pasa al control vecino). [onActivate]: el botón A. [enabled] = false lo quita de la navegación;
 * [activatable] = false lo deja enfocable pero sin respuesta a A (un botón apagado: el cursor puede pasar
 * por él, así izquierda / derecha siguen llegando al botón de al lado).
 */
@Composable
fun Modifier.settingsFocus(
    enabled: Boolean = true,
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    activatable: Boolean = true,
    onActivate: () -> Unit,
): Modifier {
    val nav = LocalSettingsNav.current ?: return this
    val item = remember { SettingsFocusItem() }
    item.onLeft = onLeft
    item.onRight = onRight
    item.onActivate = onActivate
    item.activatable = activatable
    DisposableEffect(nav, item, enabled) {
        if (enabled) nav.register(item)
        onDispose { nav.unregister(item) }
    }
    return this.onGloballyPositioned {
        if (item.coords == null) item.placedTick++
        item.coords = it
        if (item.size != it.size) item.size = it.size
        nav.itemPlaced(item)
    }
}

/** Marca un elemento (una sección de la barra lateral) como sitio donde puede ir el cursor, sin registrarlo como control. */
fun Modifier.cursorTarget(item: SettingsFocusItem): Modifier = this.onGloballyPositioned {
    if (item.coords == null) item.placedTick++
    item.coords = it
    if (item.size != it.size) item.size = it.size
}

/** Duración del fundido del cursor al apagarse / encenderse. */
private const val CursorFadeMs = 220

/** Cuánto sobresale el cursor del control, por cada lado. */
private val SettingsCursorOutset = 6.dp

/** Esquinas del cursor en Configuración: más cortas y más cerradas que las de la grilla de juegos. */
private val SettingsCursorArm = 14.dp
private val SettingsCursorRadius = 10.dp

/**
 * Dibuja el cursor sobre el control enfocado. Va en el mismo espacio que el panel de opciones (recorta a
 * sus bordes) y sigue al control aunque el panel se desplace; al cambiar de control se desliza, como el
 * cursor de la grilla de juegos.
 */
@Composable
fun SettingsCursorOverlay(nav: SettingsNav, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val item = nav.cursorTarget
    var overlay by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Se apaga con un fundido al tocar la pantalla y vuelve con la cruceta.
    val alpha by animateFloatAsState(if (nav.cursorVisible) 1f else 0f, tween(CursorFadeMs), label = "cursorAlpha")

    Box(
        modifier = modifier
            .onGloballyPositioned { overlay = it }
            .clipToBounds(),
    ) {
        if (item == null) return@Box

        val outsetPx = with(density) { SettingsCursorOutset.toPx() }
        // Tamaño animado: el cursor crece o se encoge hacia el control nuevo.
        val spec = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
        val width by animateFloatAsState(item.size.width.toFloat(), spec, label = "cursorWidth")
        val height by animateFloatAsState(item.size.height.toFloat(), spec, label = "cursorHeight")

        // Deslizamiento: 0 = en el control anterior, 1 = en este. Se crea con el control, así no hay un
        // cuadro en el que el cursor aparezca ya en su sitio.
        val slide = remember(item) { Animatable(if (nav.cursorFrom == null) 1f else 0f) }
        LaunchedEffect(item) { slide.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }

        Cursor(
            width = with(density) { (width + 2 * outsetPx).toDp() },
            height = with(density) { (height + 2 * outsetPx).toDp() },
            cornerRadius = SettingsCursorRadius,
            armLength = SettingsCursorArm,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }.offset {
                // Se lee al ubicar el cursor: seguir al control al desplazar no recompone nada. El
                // desplazamiento se lee aquí para que este bloque se repita cuando el panel se mueve.
                nav.scroll?.value
                item.placedTick
                val target = item.coords?.takeIf { it.isAttached }?.positionInRoot() ?: return@offset IntOffset.Zero
                val origin = overlay?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
                val from = nav.cursorFrom
                val p = slide.value
                val position = if (from != null) from + (target - from) * p else target
                nav.lastCursorRoot = position
                IntOffset((position.x - origin.x - outsetPx).roundToInt(), (position.y - origin.y - outsetPx).roundToInt())
            },
        )
    }
}
