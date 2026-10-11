package com.ginny.frontend.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.components.DeviceStatus
import com.ginny.frontend.ui.components.SystemLabel

/**
 * Barra superior: a la izquierda "Ginny" y, dentro de un sistema, su nombre ([title]) y cuántos juegos
 * tiene ([gameCount]); a la derecha la hora y la batería.
 */
@Composable
fun GinnyHeader(
    modifier: Modifier = Modifier,
    title: String? = null,
    /** Cuántos juegos tiene el sistema abierto: se muestra tras su nombre ("/ 8 juegos"). */
    gameCount: Int? = null,
    scrapping: Boolean = false,
    onScrapClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        SystemLabel(systemName = title, gameCount = gameCount)
        DeviceStatus( // a la derecha
            modifier = Modifier.align(Alignment.CenterEnd),
            scrapping = scrapping,
            onScrapClick = onScrapClick,
        )
    }
}
