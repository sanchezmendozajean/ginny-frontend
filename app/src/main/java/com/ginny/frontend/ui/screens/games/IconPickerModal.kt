package com.ginny.frontend.ui.screens.games

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ginny.frontend.data.LogicalButton
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.scrap.IconAlternative
import com.ginny.frontend.data.scrap.IconLibrary
import com.ginny.frontend.data.scrap.MAX_ICON_ALTERNATIVES
import com.ginny.frontend.data.scrap.ScrapResult
import com.ginny.frontend.data.scrap.steamgriddb.SteamGridDbImage
import com.ginny.frontend.ui.components.FaceButton
import com.ginny.frontend.ui.components.FaceButtonIcon
import com.ginny.frontend.ui.components.settings.GinnyButton
import com.ginny.frontend.ui.components.settings.LocalInScrollPanel
import com.ginny.frontend.ui.components.settings.settingsFocus
import com.ginny.frontend.ui.screens.scrap.ScrapModalScrim
import com.ginny.frontend.ui.screens.scrap.rememberModalNav
import com.ginny.frontend.ui.screens.settings.SettingsModalCornerRadius
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import com.ginny.frontend.ui.util.appContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lado de cada ícono en la cuadrícula y separación entre ellos. */
private val TileSize = 104.dp
private val TileGap = 10.dp

/** Un ícono de la cuadrícula: el que ya se muestra, uno guardado o uno de SteamGridDB. */
private sealed interface IconTile {
    val key: String

    /** Si es el ícono que el juego muestra ahora. */
    val active: Boolean

    /** El ícono actual, que todavía no está entre los guardados (se guarda al cambiar a otro). */
    class Current(val uri: Uri) : IconTile {
        override val key = "current"
        override val active = true
    }

    /** Uno guardado en `icons-alt/`. */
    class Saved(val alternative: IconAlternative, override val active: Boolean) : IconTile {
        override val key = "saved-${alternative.index}"
    }

    /** Una opción de SteamGridDB, todavía sin descargar. */
    class Remote(val image: SteamGridDbImage) : IconTile {
        override val key = "sgdb-${image.id}"
        override val active = false
    }

    val model: Any
        get() = when (this) {
            is Current -> uri
            is Saved -> alternative.uri
            is Remote -> image.thumb ?: image.url
        }
}

/**
 * Modal "Cambiar icono": los íconos que el juego ya tiene (hasta [MAX_ICON_ALTERNATIVES]) y, con Y, las
 * opciones de SteamGridDB. La cruceta mueve el cursor; A (o "Usar") pone el ícono elegido, B (o "Cerrar")
 * sale. Cambiar nunca borra el ícono anterior: queda guardado como una opción más (ver [com.ginny.frontend.data.scrap.IconChooser]).
 *
 * [onChanged] recibe la dirección del ícono nuevo; el modal se cierra solo al cambiarlo.
 */
@Composable
fun IconPickerModal(game: GameEntry, onClose: () -> Unit, onChanged: (Uri) -> Unit) {
    val palette = GinnyThemes.current
    val chooser = appContainer().iconChooser
    val scope = rememberCoroutineScope()
    val nav = rememberModalNav()

    var library by remember { mutableStateOf<IconLibrary?>(null) }
    var remote by remember { mutableStateOf<List<SteamGridDbImage>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(game) { library = chooser.load(game) }
    // Con los íconos ya leídos, el cursor empieza en el primero (el que se muestra ahora).
    LaunchedEffect(library) {
        if (library != null) {
            delay(120)
            if (nav.focused == null) nav.enter()
        }
    }

    val saved: List<IconTile> = library?.let { lib ->
        buildList {
            if (lib.active != null && lib.activeIndex == null) add(IconTile.Current(lib.active))
            lib.alternatives
                .sortedBy { if (it.index == lib.activeIndex) 0 else it.index }
                .forEach { add(IconTile.Saved(it, active = it.index == lib.activeIndex)) }
        }
    }.orEmpty()
    val online: List<IconTile> = remote.map { IconTile.Remote(it) }
    val selected = (saved + online).firstOrNull { it.key == selectedKey }
    val canUse = !busy && selected != null && !selected.active

    fun use() {
        val tile = selected?.takeIf { canUse } ?: return
        scope.launch {
            busy = true
            status = if (tile is IconTile.Remote) "Descargando el ícono..." else "Cambiando el ícono..."
            val result = when (tile) {
                is IconTile.Saved -> chooser.use(game, tile.alternative)
                is IconTile.Remote -> chooser.addAndUse(game, tile.image)
                is IconTile.Current -> return@launch // ya es el que se muestra (canUse lo impide)
            }
            busy = false
            when (result) {
                is ScrapResult.Ok -> {
                    onChanged(result.value)
                    onClose()
                }
                is ScrapResult.Failure -> status = result.message
            }
        }
    }

    fun search() {
        if (busy) return
        scope.launch {
            busy = true
            status = "Buscando en SteamGridDB..."
            when (val result = chooser.search(game)) {
                is ScrapResult.Ok -> {
                    remote = result.value
                    status = "${result.value.size} ${if (result.value.size == 1) "opción" else "opciones"} de SteamGridDB."
                }
                ScrapResult.NotFound -> status = "SteamGridDB no tiene íconos cuadrados para este juego."
                is ScrapResult.Failure -> status = result.message
            }
            busy = false
        }
    }

    // Y busca en SteamGridDB; A lo atiende el cursor sobre el ícono enfocado; B lo cierra (BackHandler del modal del juego).
    ScrapModalScrim(
        onDismiss = onClose,
        nav = nav,
        focusLast = false,
        onButton = { if (it == LogicalButton.Y) search() },
    ) {
        val shape = RoundedCornerShape(SettingsModalCornerRadius)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.85f), shape)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            val columns = ((maxWidth - 48.dp + TileGap) / (TileSize + TileGap)).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Cambiar icono", fontFamily = GinnyFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = palette.ink)
                    Text(
                        text = game.displayName,
                        fontFamily = GinnyFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(bottom = 2.dp),
                    )
                }

                // La lista se desplaza; el cursor la mantiene a la vista (ver SettingsNav).
                val scroll = rememberScrollState()
                DisposableEffect(nav, scroll) {
                    nav.scroll = scroll
                    onDispose { if (nav.scroll === scroll) nav.scroll = null }
                }
                CompositionLocalProvider(LocalInScrollPanel provides true) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { nav.viewport = it }
                            .verticalScroll(scroll),
                    ) {
                        IconSection("En este juego · hasta $MAX_ICON_ALTERNATIVES") {
                            if (saved.isEmpty()) {
                                Text(
                                    text = if (library == null) "Leyendo los íconos..." else "Este juego todavía no tiene ícono. Busca uno con SteamGridDB.",
                                    fontFamily = GinnyFont,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = palette.muted,
                                )
                            } else {
                                TileGrid(saved, columns, { selectedKey = it }, ::use)
                            }
                        }
                        // El resultado de la búsqueda o de un cambio, justo debajo de los íconos guardados.
                        status?.let {
                            Text(it, fontFamily = GinnyFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.muted)
                        }
                        if (online.isNotEmpty()) {
                            IconSection("SteamGridDB") { TileGrid(online, columns, { selectedKey = it }, ::use) }
                        }
                    }
                }

                // Pie: cada botón lleva el del mando que lo activa.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    GinnyButton(
                        "SteamGridDB",
                        onClick = ::search,
                        enabled = !busy,
                        focusable = false,
                        leadingIcon = { FaceButtonIcon(FaceButton.Y, tint = it, size = 20.dp) },
                    )
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GinnyButton(
                            "Cerrar",
                            onClick = onClose,
                            focusable = false,
                            leadingIcon = { FaceButtonIcon(FaceButton.B, tint = it, size = 20.dp) },
                        )
                        GinnyButton(
                            "Usar",
                            primary = true,
                            enabled = canUse,
                            onClick = ::use,
                            focusable = false,
                            leadingIcon = { FaceButtonIcon(FaceButton.A, tint = it, size = 20.dp) },
                        )
                    }
                }
            }
        }
    }
}

/** Título de sección en mayúsculas y, debajo, su contenido. */
@Composable
private fun IconSection(title: String, content: @Composable () -> Unit) {
    val palette = GinnyThemes.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            fontFamily = GinnyFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            color = palette.accent,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

/** Los íconos de [tiles] en filas de [columns]. */
@Composable
private fun TileGrid(
    tiles: List<IconTile>,
    columns: Int,
    onSelect: (String) -> Unit,
    onUse: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                row.forEach { tile ->
                    IconTileView(
                        tile = tile,
                        onSelect = { onSelect(tile.key) },
                        onUse = onUse,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconTileView(tile: IconTile, onSelect: () -> Unit, onUse: () -> Unit) {
    val palette = GinnyThemes.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(TileSize)
            // El cursor al llegar elige este ícono; A lo usa.
            // Tocar o llegar con el cursor elige este ícono (se ve solo por el cursor); A lo usa.
            .settingsFocus(onFocused = onSelect, focusOnTap = true, onActivate = onUse)
            .clip(shape),
    ) {
        // La imagen ocupa todo el cuadro: sin marco ni borde.
        AsyncImage(
            model = tile.model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (tile.active) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(palette.accent, CircleShape),
            ) {
                Text("✓", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.White)
            }
        }
    }
}
