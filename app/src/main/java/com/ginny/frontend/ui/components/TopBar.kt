package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ginny.frontend.system.BatteryObserver
import com.ginny.frontend.ui.theme.GinnyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val batteryObserver = remember { BatteryObserver(context, scope) }
    val battery by batteryObserver.status.collectAsStateSafely()

    DisposableEffect(Unit) {
        batteryObserver.start()
        onDispose { batteryObserver.stop() }
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
                .glassShadow(999.dp)
                .then(glassBackgroundModifier(999.dp))
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

        // Touch/Menu-key only — the D-Pad is scoped exclusively to the grid, never the top bar.
        IconActionBadge(
            icon = Icons.Filled.Settings,
            contentDescription = "Configuración",
            style = BadgeStyle.X,
            onClick = onOptionsClick,
        )
    }
}

@Composable
private fun BatteryIndicator(percent: Int, charging: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (charging) {
            Icon(
                imageVector = Icons.Filled.BatteryChargingFull,
                contentDescription = "Cargando",
                tint = GinnyColors.AccentBlue,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text = "$percent%", style = MaterialTheme.typography.bodyMedium, color = GinnyColors.TextPrimary)
    }
}
