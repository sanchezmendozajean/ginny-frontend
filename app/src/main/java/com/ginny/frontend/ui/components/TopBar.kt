package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ginny.frontend.data.ButtonMapping
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.system.BatteryObserver
import com.ginny.frontend.ui.theme.GinnyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One entry in the overflow ("...") menu beyond the always-present "Configuración"/"Salir de Ginny". */
data class MenuAction(val label: String, val onClick: () -> Unit)

private val TimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

/** Bar is sized to match the height of the pill-shaped buttons that sit next to it. */
private val TopBarHeight = ButtonPillHeight

@Composable
private fun rememberClockText(): String {
    var text by remember { mutableStateOf(LocalTime.now().format(TimeFormatter)) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            text = LocalTime.now().format(TimeFormatter)
            delay((60 - LocalTime.now().second) * 1000L)
        }
    }
    return text
}

/** Small shim so this file doesn't need an extra lifecycle-runtime-compose dependency just for one call. */
@Composable
private fun <T> StateFlow<T>.collectAsStateSafely() =
    produceState(initialValue = value, this) { collect { value = it } }

/**
 * Top bar: the glass pill wraps only the contextual title (app name on the systems screen,
 * current system name elsewhere), clock and battery indicator. The options (X) badge lives
 * outside the pill as its own separate element, not inside the bar.
 */
@Composable
fun GinnyTopBar(
    contextualTitle: String,
    onOptionsClick: () -> Unit,
    menuExpanded: MutableState<Boolean>,
    buttonMapping: ButtonMapping,
    modifier: Modifier = Modifier,
    /** Extra groups shown above "Configuración"/"Salir de Ginny", each rendered as its own
     * divider-separated block (in order) — empty groups are dropped, so a caller can pass e.g. a
     * "Seleccionar emulador"-only group that only applies with a game selected. */
    extraMenuGroups: List<List<MenuAction>> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val batteryObserver = remember { BatteryObserver(context, scope) }
    val battery by batteryObserver.status.collectAsStateSafely()

    DisposableEffect(Unit) {
        batteryObserver.start()
        onDispose { batteryObserver.stop() }
    }

    // The menu-open state is hoisted up to MainActivity (see GinnyNavHost/MainActivity), since the
    // hardware Menu/Back keys are handled globally there rather than by this composable — reset it
    // on the way out so a freshly-entered screen never inherits an already-open menu.
    DisposableEffect(Unit) {
        onDispose { menuExpanded.value = false }
    }

    val clockText = rememberClockText()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(TopBarHeight)
                .padding(start = 20.dp, end = 20.dp),
        ) {
            Text(
                text = contextualTitle,
                style = MaterialTheme.typography.titleMedium,
                color = GinnyColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(16.dp))
            Text(text = clockText, style = MaterialTheme.typography.bodyLarge, color = GinnyColors.TextPrimary)

            battery?.let { status ->
                Spacer(Modifier.width(16.dp))
                BatteryIndicator(percent = status.percent, charging = status.isCharging)
            }
        }

        Spacer(Modifier.width(14.dp))

        // The trigger badge itself stays touch-only (never part of D-Pad focus traversal, same as
        // the rest of this bar) — the hardware Menu key opens the dropdown directly via
        // [menuExpanded] instead of moving focus here. Once open, the dropdown's own content
        // below owns D-Pad Up/Down/A navigation.
        Box {
            IconActionBadge(
                icon = Icons.Filled.MoreVert,
                contentDescription = "Menú",
                style = BadgeStyle.X,
                onClick = { menuExpanded.value = true },
            )
            OverflowMenu(
                expanded = menuExpanded.value,
                onExpandedChange = { menuExpanded.value = it },
                buttonMapping = buttonMapping,
                groups = buildList {
                    addAll(extraMenuGroups.filter { it.isNotEmpty() })
                    add(listOf(MenuAction("Configuración", onOptionsClick)))
                    add(listOf(MenuAction("Salir de Ginny") { (context as? android.app.Activity)?.finish() }))
                },
            )
        }
    }
}

/**
 * The dropdown's content, with explicit D-Pad Up/Down/A navigation instead of relying on the
 * platform's implicit keyboard focus search — same manual-key-handling approach used by
 * [SnapFocusGrid] elsewhere in this app, for consistency and to not depend on undocumented
 * default DropdownMenu keyboard behavior. A single [FocusRequester] on the container (not one per
 * item) drives a locally tracked [highlightedIndex], matching that grid's pattern. [groups] are
 * rendered in order, each separated by a divider from the next non-empty one.
 */
@Composable
private fun OverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    buttonMapping: ButtonMapping,
    groups: List<List<MenuAction>>,
) {
    val actions = remember(groups) { groups.flatten() }
    var highlightedIndex by remember(expanded) { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
        LaunchedEffect(expanded) {
            if (expanded) focusRequester.requestFocus()
        }
        Column(
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusTarget()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (buttonMapping.buttonForKeyCode(keyEvent.nativeKeyEvent.keyCode)) {
                        LogicalButton.DPAD_UP -> {
                            highlightedIndex = (highlightedIndex - 1 + actions.size) % actions.size
                            true
                        }
                        LogicalButton.DPAD_DOWN -> {
                            highlightedIndex = (highlightedIndex + 1) % actions.size
                            true
                        }
                        LogicalButton.A -> {
                            onExpandedChange(false)
                            actions.getOrNull(highlightedIndex)?.onClick?.invoke()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            var itemIndex = 0
            groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0 && group.isNotEmpty()) HorizontalDivider()
                group.forEach { action ->
                    val currentIndex = itemIndex
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            onExpandedChange(false)
                            action.onClick()
                        },
                        modifier = Modifier.background(
                            if (currentIndex == highlightedIndex) {
                                GinnyColors.AccentBlue.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        ),
                    )
                    itemIndex++
                }
            }
        }
    }
}

@Composable
private fun BatteryIndicator(percent: Int, charging: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (charging) Icons.Filled.BatteryChargingFull else batteryIconFor(percent),
            contentDescription = if (charging) "Cargando" else "Batería",
            tint = if (charging) GinnyColors.AccentBlue else GinnyColors.TextPrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text = "$percent%", style = MaterialTheme.typography.bodyMedium, color = GinnyColors.TextPrimary)
    }
}

private fun batteryIconFor(percent: Int): ImageVector = when {
    percent >= 95 -> Icons.Filled.BatteryFull
    percent >= 80 -> Icons.Filled.Battery6Bar
    percent >= 65 -> Icons.Filled.Battery5Bar
    percent >= 50 -> Icons.Filled.Battery4Bar
    percent >= 35 -> Icons.Filled.Battery3Bar
    percent >= 20 -> Icons.Filled.Battery2Bar
    percent >= 5 -> Icons.Filled.Battery1Bar
    else -> Icons.Filled.Battery0Bar
}
