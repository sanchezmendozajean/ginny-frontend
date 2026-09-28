package com.ginny.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.layout.GinnyContent
import com.ginny.frontend.ui.layout.GinnyFooter
import com.ginny.frontend.ui.layout.GinnyHeader
import com.ginny.frontend.ui.theme.GinnyThemes

/**
 * Raíz de la interfaz, reconstruida paso a paso (ver docs/plan-reconstruccion-ui.md).
 * La implementación anterior completa está archivada en docs/ui-legacy/ como referencia.
 */
@Composable
fun GinnyApp() {
    val palette = GinnyThemes.Menta

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(18.dp),                            // margen con los bordes de la pantalla
        verticalArrangement = Arrangement.spacedBy(12.dp), // separación entre las tres filas
    ) {
        GinnyHeader()
        GinnyContent(modifier = Modifier.weight(1f))    // ocupa todo el alto sobrante
        GinnyFooter()
    }
}
