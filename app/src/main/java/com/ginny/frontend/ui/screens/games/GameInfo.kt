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

/** Desde este ancho útil de tarjeta se dibuja la caja, a la derecha del contenido. */
private val SideBySideMinWidth = 560.dp

/** Qué parte del ancho ocupa la caja cuando va a la derecha. */
private const val BoxWidthFraction = 0.38f


/**
 * Si la caja en 3D se muestra a la derecha del cuerpo del modal: solo en pantallas anchas ([contentWidth]
 * es el ancho útil de la tarjeta) y si el juego tiene alguna cara de caja guardada. En pantallas angostas
 * la caja no se dibuja.
 */
internal fun shouldShowSideBox(assets: GameAssets?, contentWidth: Dp): Boolean =
    assets?.hasBox == true && contentWidth >= SideBySideMinWidth

/** Ancho de la columna de la caja, a partir del ancho útil de la tarjeta. */
internal fun sideBoxWidth(contentWidth: Dp): Dp = contentWidth * BoxWidthFraction

/**
 * La caja en 3D con las texturas guardadas, a la altura que le den ([modifier]). Mientras se decodifican
 * las imágenes deja el espacio vacío, para que nada salte.
 */
@Composable
internal fun GameSideBox(assets: GameAssets, modifier: Modifier) {
    val textures = rememberBoxTextures(assets.boxFront, assets.boxBack, assets.boxSpine)
    when {
        textures == null -> Spacer(modifier)
        textures.isEmpty -> Unit // había archivos pero no se pudieron leer
        else -> GameBox3D(textures, modifier)
    }
}

/** Sinopsis y datos del juego (estudio, editor, año, género, jugadores y valoración). */
@Composable
internal fun GameInfoPanel(assets: GameAssets?) {
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
