package com.ginny.frontend.ui.screens.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.data.scrap.GameAssets
import com.ginny.frontend.data.scrap.GameMetadata
import com.ginny.frontend.ui.components.box.GameBox3D
import com.ginny.frontend.ui.components.box.rememberBoxTextures
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import java.util.Locale

/** Desde este ancho de tarjeta, la información va a la izquierda y la caja a la derecha. */
private val SideBySideMinWidth = 560.dp

/** Qué parte del ancho ocupa la caja cuando va a la derecha. */
private const val BoxWidthFraction = 0.38f

/** Alto del área de la caja en 3D. */
private val BoxHeight = 280.dp

/**
 * Lo primero del contenido del modal: la información del juego a la izquierda y su caja en 3D a la
 * derecha. Si la tarjeta es angosta, la caja pasa arriba. Sin ninguna cara de caja guardada, la
 * información ocupa todo el ancho.
 *
 * [assets] es null mientras se lee lo guardado (se reserva el espacio de la caja para que nada salte).
 */
@Composable
internal fun GameInfoRow(assets: GameAssets?, contentWidth: Dp) {
    val boxAssets = assets?.takeIf { it.hasBox }
    val sideBySide = contentWidth >= SideBySideMinWidth

    when {
        boxAssets != null && sideBySide -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.weight(1f)) { GameInfoPanel(assets) }
            GameBoxView(boxAssets, Modifier.width(contentWidth * BoxWidthFraction))
        }
        boxAssets != null -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GameBoxView(boxAssets, Modifier.fillMaxWidth())
            GameInfoPanel(assets)
        }
        else -> GameInfoPanel(assets)
    }
}

/** La caja en 3D con las texturas guardadas; mientras se decodifican, un espacio vacío del mismo tamaño. */
@Composable
private fun GameBoxView(assets: GameAssets, modifier: Modifier) {
    val textures = rememberBoxTextures(assets.boxFront, assets.boxBack, assets.boxSpine)
    val sized = modifier.height(BoxHeight)
    when {
        textures == null -> Spacer(sized)
        textures.isEmpty -> Spacer(Modifier.height(0.dp)) // había archivos pero no se pudieron leer
        else -> GameBox3D(textures, sized)
    }
}

/** Sinopsis y datos del juego (estudio, editor, año, género, jugadores y valoración). */
@Composable
private fun GameInfoPanel(assets: GameAssets?) {
    val palette = GinnyThemes.current
    val metadata = assets?.metadata

    ModalSection("Información") {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            when {
                assets == null -> Muted("Leyendo la información guardada...")
                metadata == null || !metadata.hasDetails -> Muted(
                    "Todavía no hay información de este juego. Descárgala desde el menú de tres puntos > Scrappear.",
                )
                else -> {
                    metadata.synopsis?.let {
                        Text(
                            text = it,
                            fontFamily = GinnyFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = palette.ink,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        facts(metadata).forEach { (label, value) -> Fact(label, value) }
                    }
                }
            }
        }
    }
}

/** Los datos del juego que existan, en orden. */
internal fun facts(metadata: GameMetadata): List<Pair<String, String>> = buildList {
    metadata.developer?.let { add("Estudio" to it) }
    // Si el editor es el mismo estudio, no se repite.
    metadata.publisher?.takeIf { it != metadata.developer }?.let { add("Editor" to it) }
    metadata.releaseYear?.let { add("Año" to it.toString()) }
    if (metadata.genres.isNotEmpty()) add((if (metadata.genres.size == 1) "Género" else "Géneros") to metadata.genres.joinToString(", "))
    metadata.players?.let { add("Jugadores" to it) }
    metadata.rating?.let { add("Valoración" to ratingText(it)) }
}

/** La nota de 0 a 1 como "7,5 / 10". */
internal fun ratingText(rating: Double): String = String.format(Locale.getDefault(), "%.1f / 10", rating.coerceIn(0.0, 1.0) * 10)

@Composable
private fun Fact(label: String, value: String) {
    val palette = GinnyThemes.current
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = palette.muted,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = palette.ink,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, fontFamily = GinnyFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = GinnyThemes.current.muted)
}
