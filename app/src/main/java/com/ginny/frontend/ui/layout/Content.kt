package com.ginny.frontend.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ginny.frontend.R
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.ui.components.Container
import com.ginny.frontend.ui.components.game.Cursor
import com.ginny.frontend.ui.components.game.Element
import com.ginny.frontend.ui.screens.systems.SystemsViewModel
import com.ginny.frontend.ui.util.appContainer
import com.ginny.frontend.ui.util.drawableUri

/** Panel central. Aquí irá la grilla de sistemas / juegos / estados guardados. */
@Composable
fun Content(modifier: Modifier = Modifier) {
    // Provisional, para probar los logos: cuando exista la pantalla de Sistemas, el ViewModel se
    // creará allí y Content recibirá ya los datos.
    val container = appContainer()
    val viewModel: SystemsViewModel = viewModel(
        factory = viewModelFactory { initializer { SystemsViewModel(container) } },
    )
    // Mapa SystemId → Uri del logo. Empieza vacío y se llena cuando termina la búsqueda.
    val logos by viewModel.systemLogos.collectAsState()

    Container(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Prueba del cursor: Box apila al elemento y al cursor uno encima del otro.
            Box(
                contentAlignment = Alignment.Center
            ) {
                Element(size = 120.dp, title = "Super Nintendo", image = logos[SystemId.SNES])
                Cursor(size = 140.dp)
            }
            Element(size = 120.dp, title = "NES", image = logos[SystemId.NES])
            Element(size = 120.dp, title = "Prueba", image = drawableUri(R.drawable.ic_ginny))
            Element(size = 120.dp, empty = true)
        }
    }
}
