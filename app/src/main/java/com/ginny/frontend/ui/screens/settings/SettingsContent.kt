package com.ginny.frontend.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/**
 * Panel derecho del modal: el título de la [section] elegida y, debajo,
 * el área de opciones, que se desplaza si las opciones no entran en la pantalla.
 *
 * Las opciones de cada sección van en [options] (todavía vacío).
 */
@Composable
fun SettingsContent(
    section: SettingsSection,
    modifier: Modifier = Modifier,
    options: @Composable ColumnScope.() -> Unit = {},
) {
    val palette = GinnyThemes.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
    ) {
        // ── Área de opciones ───────────────────────────────────────────────────────────────
        // weight(1f): ocupa todo el alto que queda. verticalScroll: si las opciones no entran,
        // se desplazan dentro de esta área (el encabezado queda fijo arriba).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // Aquí van las opciones, apiladas una debajo de otra; esta es la sección scrollable.
            Column(modifier = Modifier.fillMaxWidth()) {
                options() // dentro de un Column hay ColumnScope, que es lo que options necesita
            }
        }
    }
}
