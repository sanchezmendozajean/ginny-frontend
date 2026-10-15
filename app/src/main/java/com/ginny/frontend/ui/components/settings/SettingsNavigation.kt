package com.ginny.frontend.ui.components.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.ginny.frontend.ui.components.game.CursorBorderWidth
import com.ginny.frontend.ui.components.game.CursorStrokeWidth
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

    /** Se llama cuando el cursor llega a este control. */
    internal var onFocused: (() -> Unit)? = null

    /** El control está dentro de un panel con desplazamiento (ver [LocalInScrollPanel]). */
    internal var scrolls: Boolean = false

    /** Panel secundario (con desplazamiento propio) al que pertenece; null = el panel principal de opciones o los controles fijos. */
    internal var pane: SettingsPane? = null

    /** Entre los controles de un panel secundario, el que representa la elección actual: donde cae el foco al volver a él. */
    internal var anchor: Boolean = false

    /** false = el control se puede enfocar pero está apagado: A no hace nada (ni suena). */
    internal var activatable: Boolean = true

    internal fun bounds(): Rect? = coords?.takeIf { it.isAttached }?.rectInRoot()

    private var lastRoot: Offset? = null

    /**
     * Anota dónde quedó el control tras colocarse. Si es la primera vez o cambió de sitio sin que el panel se
     * desplazara (otro elemento apareció o desapareció encima), sube [placedTick]: el cursor lo lee y se recoloca.
     */
    internal fun notePlaced(placed: LayoutCoordinates) {
        coords = placed
        if (size != placed.size) size = placed.size
        val root = placed.positionInRoot()
        if (lastRoot != root) {
            lastRoot = root
            placedTick++
        }
    }
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

    /** false = no hay otra lista a la que volver (los modales): izquierda sin más controles no hace nada. */
    var canLeave: Boolean = true

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
            moveFocusTo(item)
        }
    }

    /** Controles ya colocados, de arriba abajo y de izquierda a derecha. */
    private fun placed(): List<Pair<SettingsFocusItem, Rect>> =
        items.mapNotNull { item -> item.bounds()?.let { item to it } }
            .sortedWith(compareBy({ it.second.center.y.roundToInt() / 8 }, { it.second.center.x }))

    /**
     * Tocar un control: el cursor aparece sobre él (aunque se hubiera apagado al tocar la pantalla) y pasa a
     * ser el enfocado. Así tocar y usar la cruceta dan la misma selección.
     */
    internal fun focusByTouch(item: SettingsFocusItem) {
        if (item !in items) return
        val wasVisible = cursorVisible
        cursorVisible = true
        active = true
        if (focused !== item) {
            cursorFrom = if (wasVisible) lastCursorRoot else null
            moveFocusTo(item)
        }
    }

    /**
     * El foco pasa a las opciones, empezando por el primer control ([last] = false) o por el último (el
     * de abajo a la derecha: en un modal, el botón principal).
     */
    fun enter(last: Boolean = false) {
        active = true
        cursorFrom = if (cursorVisible) lastCursorRoot else null // desde la sección hasta el primer control
        lostBounds = null
        val list = placed()
        // El último: el de abajo a la derecha entre los controles fijos (los botones de un modal); si no hay, el último.
        val fixed = list.filter { !it.first.scrolls }.ifEmpty { list }
        val target = if (last) fixed.lastOrNull()?.first else (list.firstOrNull { it.first.anchor } ?: list.firstOrNull())?.first
        moveFocusTo(target)
        if (target != null && target.pane != null) ensureVisible(target) // la elección actual de un panel secundario
        // El panel pudo quedar desplazado de una visita anterior: se empieza desde arriba.
        if (!last) scroll?.let { s -> scope.launch { s.animateScrollTo(0) } }
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

    /** Cambia el control enfocado y le avisa. */
    private fun moveFocusTo(item: SettingsFocusItem?) {
        focused = item
        item?.onFocused?.invoke()
    }

    private fun focus(item: SettingsFocusItem) {
        cursorFrom = if (cursorVisible) lastCursorRoot else null
        moveFocusTo(item)
        ensureVisible(item)
    }

    private fun move(direction: FocusMove): Boolean {
        val list = placed()
        val current = focused?.let { f -> list.indexOfFirst { it.first === f } } ?: -1
        if (current < 0) {
            list.firstOrNull()?.let { focus(it.first) }
            return list.isNotEmpty()
        }
        val cur = list[current]

        // 1) Dentro de su grupo: el panel con desplazamiento, o los controles fijos (ej. los botones de un modal).
        val sameGroup = list.filter { it.first.scrolls == cur.first.scrolls && it.first.pane === cur.first.pane }
        FocusSearch.next(sameGroup.map { it.second }, sameGroup.indexOfFirst { it.first === cur.first }, direction, rowTolerancePx)
            ?.let { focus(sameGroup[it].first); return true }

        // 2) Arriba / abajo entre grupos. Las posiciones de la lista, que se desplaza, no sirven para comparar
        // con las de los controles fijos (una fila oculta bajo el panel queda "a la altura" de los botones):
        // se sale del panel por su borde y se entra por el borde contrario, solo a lo que está a la vista.
        if (direction == FocusMove.Left || direction == FocusMove.Right) {
            // Entre paneles (lista de la izquierda ↔ opciones): derecha entra a la primera opción; izquierda vuelve a la elección.
            if (direction == FocusMove.Right && cur.first.pane != null) {
                list.firstOrNull { it.first.scrolls && it.first.pane == null }?.let { focus(it.first); return true }
            }
            if (direction == FocusMove.Left && cur.first.pane == null && cur.first.scrolls) {
                val secondary = list.filter { it.first.pane != null }
                (secondary.firstOrNull { it.first.anchor } ?: secondary.firstOrNull())?.let { focus(it.first); return true }
            }
            return false
        }
        val view = viewport?.takeIf { it.isAttached }?.rectInRoot() ?: return false
        val otherGroup = list.filter { it.first.scrolls != cur.first.scrolls }
        val candidates = if (cur.first.scrolls) otherGroup else otherGroup.filter { it.second.overlaps(view) }
        if (candidates.isEmpty()) return false
        val goingDown = direction == FocusMove.Down
        val startY = when {
            cur.first.scrolls -> if (goingDown) view.bottom - 1f else view.top + 1f // saliendo del panel
            else -> if (goingDown) view.top - 1f else view.bottom + 1f // entrando al panel
        }
        val x = cur.second.center.x
        val rects = candidates.map { it.second } + Rect(x - 1f, startY - 1f, x + 1f, startY + 1f)
        val target = FocusSearch.next(rects, rects.lastIndex, direction, rowTolerancePx) ?: return false
        focus(candidates[target].first)
        return true
    }

    /** Desplaza el panel lo justo para que [item] se vea; en la primera / última fila, hasta el borde. */
    private fun ensureVisible(item: SettingsFocusItem) {
        if (!item.scrolls) return // fuera del panel con desplazamiento (ej. los botones de un modal)
        val pane = item.pane
        val scroll = pane?.scroll ?: scroll ?: return
        val viewport = (pane?.viewport ?: viewport)?.takeIf { it.isAttached } ?: return
        scope.launch {
            val list = placed().filter { it.first.scrolls && it.first.pane === pane } // la primera / última fila son las del panel
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

    /** Mueve el foco como la cruceta (para controles que, al activarse con A, equivalen a un movimiento). */
    fun step(direction: FocusMove) { if (move(direction)) sound?.playFocusSound() }

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
            LogicalButton.DPAD_UP -> if (move(FocusMove.Up)) sound?.playFocusSound() else if (focused?.pane == null) scrollBy(-ScrollStepPx)
            LogicalButton.DPAD_DOWN -> if (move(FocusMove.Down)) sound?.playFocusSound() else if (focused?.pane == null) scrollBy(ScrollStepPx)
            LogicalButton.DPAD_LEFT -> {
                val item = focused
                when {
                    item?.onLeft?.invoke() == true -> Unit
                    move(FocusMove.Left) -> sound?.playFocusSound()
                    canLeave -> leave() // no hay más a la izquierda: de vuelta a la lista de secciones
                    else -> Unit
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

/** true dentro de un panel con desplazamiento: sus controles se mantienen a la vista al enfocarlos. */
val LocalInScrollPanel = staticCompositionLocalOf { false }

/**
 * Panel con desplazamiento propio junto al principal (ej. la lista de sistemas, a la izquierda de sus opciones).
 * Lo crea [com.ginny.frontend.ui.screens.settings.SettingsListPane].
 */
@Stable
class SettingsPane(val scroll: ScrollState) {
    internal var viewport: LayoutCoordinates? = null
}

/** El panel secundario en el que está un control; null en el principal. */
val LocalSettingsPane = staticCompositionLocalOf<SettingsPane?> { null }

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
    /** Se llama cuando el cursor llega al control. */
    onFocused: (() -> Unit)? = null,
    /** true = tocar el control también le lleva el cursor (para listas de elementos que se eligen, como íconos). */
    focusOnTap: Boolean = false,
    /** true = en un panel secundario, este control es la elección actual (ver [SettingsPane]). */
    anchor: Boolean = false,
    onActivate: () -> Unit,
): Modifier {
    val nav = LocalSettingsNav.current ?: return this
    val item = remember { SettingsFocusItem() }
    item.scrolls = LocalInScrollPanel.current
    item.pane = LocalSettingsPane.current
    item.anchor = anchor
    item.onLeft = onLeft
    item.onRight = onRight
    item.onActivate = onActivate
    item.activatable = activatable
    item.onFocused = onFocused
    DisposableEffect(nav, item, enabled) {
        if (enabled) nav.register(item)
        onDispose { nav.unregister(item) }
    }
    return this
        .onGloballyPositioned {
            item.notePlaced(it)
            nav.itemPlaced(item)
        }
        .then(
            if (focusOnTap) Modifier.pointerInput(item) { detectTapGestures { nav.focusByTouch(item) } } else Modifier,
        )
}

/** Marca un elemento (una sección de la barra lateral) como sitio donde puede ir el cursor, sin registrarlo como control. */
fun Modifier.cursorTarget(item: SettingsFocusItem): Modifier = this.onGloballyPositioned { item.notePlaced(it) }

/** Duración del fundido del cursor al apagarse / encenderse. */
private const val CursorFadeMs = 220

/** Cuánto sobresale el cursor del control, por cada lado. */
private val SettingsCursorOutset = 6.dp
private val CompactCursorOutset = 3.dp

/** Esquinas del cursor en Configuración: más cortas y más cerradas que las de la grilla de juegos. */
private val SettingsCursorArm = 14.dp
private val SettingsCursorRadius = 10.dp

/** Versión compacta (modales): aún más corto y fino. */
private val CompactCursorArm = 10.dp
private val CompactCursorRadius = 8.dp
private val CompactCursorStroke = 3.dp
private val CompactCursorBorder = 1.5.dp

/**
 * Dibuja el cursor sobre el control enfocado. Va en el mismo espacio que el panel de opciones (recorta a
 * sus bordes) y sigue al control aunque el panel se desplace; al cambiar de control se desliza, como el
 * cursor de la grilla de juegos.
 */
@Composable
fun SettingsCursorOverlay(nav: SettingsNav, modifier: Modifier = Modifier, compact: Boolean = false) {
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
        // Sin control (o sin colocar todavía, como la lista de secciones que un modal no tiene): sin cursor.
        if (item == null || item.placedTick == 0) return@Box

        val outsetPx = with(density) { (if (compact) CompactCursorOutset else SettingsCursorOutset).toPx() }
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
            cornerRadius = if (compact) CompactCursorRadius else SettingsCursorRadius,
            armLength = if (compact) CompactCursorArm else SettingsCursorArm,
            strokeWidth = if (compact) CompactCursorStroke else CursorStrokeWidth,
            borderWidth = if (compact) CompactCursorBorder else CursorBorderWidth,
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
