package com.ginny.frontend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ginny.frontend.system.BatteryObserver
import com.ginny.frontend.ui.theme.LocalGinnyPalette
import com.ginny.frontend.ui.theme.diagonalGradient
import com.ginny.frontend.ui.theme.oklch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Bar is sized to match the height of the pill-shaped buttons that sit next to it. */
private val TopBarHeight = ButtonPillHeight
private val LogoSize = 32.dp

/** What the leading pill of [GinnyTopBar] shows — the Systems screen's app logo, or a Games/Save
 * States screen's back-to-Systems pill (whole pill is the back button, per the reference). */
sealed interface TopBarLeading {
    data object AppLogo : TopBarLeading
    data class SystemBack(val systemName: String, val systemHue: Float, val onBack: () -> Unit) : TopBarLeading
}

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

private val TimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

/** Small shim so this file doesn't need an extra lifecycle-runtime-compose dependency just for one call. */
@Composable
private fun <T> StateFlow<T>.collectAsStateSafely() =
    produceState(initialValue = value, this) { collect { value = it } }

/**
 * Top bar: a leading glass pill (app logo on Systems, back+system on Games/Save States), a
 * clock+date+battery glass pill, and a plain hamburger glass circle that opens Settings directly
 * — the old dropdown ("...") menu is gone; see docs/architecture.md's Settings-as-overlay note.
 */
@Composable
fun GinnyTopBar(
    leading: TopBarLeading,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGinnyPalette.current
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
        LeadingPill(leading, modifier = Modifier.height(TopBarHeight))

        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))

        GlassSurface(modifier = Modifier.height(TopBarHeight)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(text = clockText, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
                battery?.let { status ->
                    Spacer(Modifier.width(16.dp))
                    Box(modifier = Modifier.width(2.dp).height(20.dp).background(palette.line, RoundedCornerShape(1.dp)))
                    Spacer(Modifier.width(16.dp))
                    BatteryIndicator(percent = status.percent, charging = status.isCharging)
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        IconActionBadge(
            icon = Icons.Filled.Menu,
            contentDescription = "Configuración",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun LeadingPill(leading: TopBarLeading, modifier: Modifier = Modifier) {
    val palette = LocalGinnyPalette.current
    when (leading) {
        is TopBarLeading.AppLogo -> {
            GlassSurface(modifier = modifier) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 10.dp, end = 20.dp),
                ) {
                    Box(
                        modifier = Modifier.size(LogoSize).background(palette.grad, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "G", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(text = "Ginny", style = MaterialTheme.typography.titleLarge, color = palette.accentInk)
                }
            }
        }
        is TopBarLeading.SystemBack -> {
            GlassSurface(modifier = modifier.clickable(onClick = leading.onBack)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, end = 20.dp),
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).background(palette.chip, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "←", color = palette.accentInk, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.size(28.dp).background(
                            diagonalGradient(oklch(0.78f, 0.13f, leading.systemHue), oklch(0.83f, 0.11f, leading.systemHue + 50f)),
                            RoundedCornerShape(9.dp),
                        ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = leading.systemName,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryIndicator(percent: Int, charging: Boolean) {
    val palette = LocalGinnyPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (charging) Icons.Filled.BatteryChargingFull else batteryIconFor(percent),
            contentDescription = if (charging) "Cargando" else "Batería",
            tint = if (charging) palette.accent else palette.ink,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text = "$percent%", style = MaterialTheme.typography.bodyMedium, color = palette.ink)
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
