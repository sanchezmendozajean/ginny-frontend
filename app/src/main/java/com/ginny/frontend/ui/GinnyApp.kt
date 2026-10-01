package com.ginny.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.components.GeometricBackground
import com.ginny.frontend.ui.layout.Content
import com.ginny.frontend.ui.layout.GinnyFooter
import com.ginny.frontend.ui.layout.GinnyHeader
import com.ginny.frontend.ui.modifiers.LocalHazeState
import com.ginny.frontend.ui.theme.GinnyThemes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * Raíz de la interfaz, reconstruida paso a paso (ver docs/plan-reconstruccion-ui.md).
 * La implementación anterior completa está archivada en docs/ui-legacy/ como referencia.
 */
@Composable
fun GinnyApp() {
    // El tema lo provee GinnyTheme (en MainActivity), leído de Configuración.
    val palette = GinnyThemes.current
    // Une el fondo (que se desenfoca) con los Container (que lo desenfocan detrás de sí).
    val hazeState = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Capa 1: fondo geométrico animado, marcado como "desenfocable".
        GeometricBackground(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState, backgroundColor = palette.background),
        )

        // Capa 2: la interfaz. Todo lo de adentro puede desenfocar el fondo (ver Container).
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),                                // margen con los bordes de la pantalla
                verticalArrangement = Arrangement.spacedBy(12.dp),  // separación entre las tres filas
            ) {
                GinnyHeader()
                Content(modifier = Modifier.weight(1f))            // ocupa todo el alto sobrante
                GinnyFooter()
            }
        }
    }
}
