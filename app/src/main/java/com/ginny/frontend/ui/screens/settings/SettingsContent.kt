package com.ginny.frontend.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Panel derecho del modal: las opciones de la sección elegida ([options]), apiladas una debajo de
 * otra. Si no entran en la pantalla, se desplazan.
 */
@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    options: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // verticalScroll antes que padding: el margen se desplaza junto con las opciones, así
            // al llegar al final queda el mismo aire que arriba.
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        content = options,
    )
}
