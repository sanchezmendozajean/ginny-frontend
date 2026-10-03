package com.ginny.frontend.ui.components.game

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ginny.frontend.ui.components.Container
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/** Radio de las esquinas del elemento. */
val ElementCornerRadius = 10.dp

/** Grosor del marco blanco: grueso, tipo marco (regla del spec). */
val ElementFrameWidth = 4.dp

/**
 * Un elemento cuadrado de la grilla (sistema, juego o estado guardado), con marco blanco redondeado.
 *
 * - [empty] = true: hueco de relleno, para que la grilla conserve su forma aunque no haya
 *   suficientes elementos. Dibuja solo un Container pequeñito en el centro.
 * - Si no: la imagen ([image]) llenando el cuadrado hasta el marco. Es un [Uri] porque así llegan
 *   todas las imágenes reales (GameEntry.iconUri, AssetRepository.systemLogoUri,
 *   SaveStateEntry.thumbnailUri); para recursos de la app, usar `drawableUri(R.drawable.x)`.
 *   Sin imagen, o si no carga, muestra el [title] como reemplazo.
 *
 * El elemento no sabe si está seleccionado: la selección la muestra un cursor aparte que se mueve
 * hacia el elemento elegido.
 *
 * [title]/[subtitle] no se dibujan sobre la imagen: los muestra el footer (SelectionInfo) para el
 * elemento seleccionado. [title] sí se usa como descripción para accesibilidad.
 */
@Composable
fun Element(
    size: Dp,
    modifier: Modifier = Modifier,
    title: String = "",
    subtitle: String = "",
    image: Uri? = null,
    empty: Boolean = false,
    cornerRadius: Dp = ElementCornerRadius,
) {
    val palette = GinnyThemes.current
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (empty) {
            // Hueco: un Container pequeñito, sin desenfoque, centrado.
            Box(
                modifier = Modifier.size(10.dp)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp)
                    )                    // detrás de la imagen mientras carga
            ) {}
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)                                // recorta la imagen a las esquinas redondeadas
                    .background(Color.White)                    // detrás de la imagen mientras carga
                    .border(ElementFrameWidth, Color.White, shape), // marco blanco, encima de la imagen
                contentAlignment = Alignment.Center,
            ) {
                // Si la imagen no carga (archivo borrado, formato inválido…), se cae al título.
                // remember(image): si cambia la imagen, se vuelve a intentar desde cero.
                var failed by remember(image) { mutableStateOf(false) }

                if (image != null && !failed) {
                    AsyncImage(
                        model = image,
                        contentDescription = title,
                        contentScale = ContentScale.Crop, // llena el cuadrado, recortando si hace falta
                        modifier = Modifier.fillMaxSize(),
                        onError = { failed = true },
                    )
                } else {
                    // Sin imagen (o falló la carga): el título como reemplazo provisional.
                    Text(
                        text = title,
                        fontFamily = GinnyFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}
