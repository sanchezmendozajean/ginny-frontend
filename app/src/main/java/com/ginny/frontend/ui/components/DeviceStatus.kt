package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.system.BatteryObserver
import com.ginny.frontend.system.BatteryStatus
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Hora + batería (ícono y porcentaje) dentro de un [Container]. Va a la derecha del header.
 * Si [BatteryObserver] decide que la batería no es confiable, el ícono y el porcentaje se ocultan
 * (regla del spec: mejor no mostrar nada que un dato falso); la hora siempre se muestra.
 */
@Composable
fun DeviceStatus(modifier: Modifier = Modifier, scrapping: Boolean = false, onScrapClick: () -> Unit = {}) {
    val palette = GinnyThemes.current
    val time = rememberClockText()
    val battery = rememberBatteryStatus()

    Container(modifier = modifier) {
        // Mientras el scrapper trabaja, a la izquierda de la hora: tocarlo abre el modal de avance.
        if (scrapping) ScrapActivityIcon(onClick = onScrapClick)

        Text(
            text = time,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = palette.ink,
        )

        // battery es null cuando no hay datos confiables → no se dibuja nada de esto.
        if (battery != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BatteryIcon(percent = battery.percent, charging = battery.isCharging, color = palette.ink)
                Text(
                    text = "${battery.percent}%",
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = palette.ink,
                )
            }
        }
    }
}

/** Hora actual como texto ("08:45 PM"), actualizada justo al cambiar cada minuto. */
@Composable
private fun rememberClockText(): String {
    var text by remember { mutableStateOf(LocalTime.now().format(TimeFormatter)) }
    // LaunchedEffect: corre este bucle mientras el componente esté en pantalla, y lo detiene solo
    // cuando sale.
    LaunchedEffect(Unit) {
        while (true) {
            text = LocalTime.now().format(TimeFormatter)
            delay((60 - LocalTime.now().second) * 1000L) // espera hasta el próximo minuto
        }
    }
    return text
}

private val TimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

/** Estado de la batería, o null si no es confiable (ver [BatteryObserver]). */
@Composable
private fun rememberBatteryStatus(): BatteryStatus? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val observer = remember { BatteryObserver(context, scope) }
    // DisposableEffect: empieza a escuchar la batería al aparecer, y deja de hacerlo al salir.
    DisposableEffect(observer) {
        observer.start()
        onDispose { observer.stop() }
    }
    val status by observer.status.collectAsState()
    return status
}
