package com.ginny.frontend.ui.components.game

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ginny.frontend.ui.components.Container
import com.ginny.frontend.ui.components.Icon
import com.ginny.frontend.ui.theme.adjust
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/** Radio de las esquinas del elemento. */
val ElementCornerRadius = 10.dp

/** Grosor del marco blanco: grueso, tipo marco (regla del spec). */
val ElementFrameWidth = 4.dp

/** Elevación de la sombra ligera que rodea al elemento. */
val ElementShadowElevation = 4.dp

/** Tamaño de un ícono de la app dentro del elemento, como fracción del lado (ej. Favoritos). */
const val ElementIconScale = 0.45f

/** Luminosidad (OKLCH, 0…1) del ícono: el acento del tema, aclarado. */
const val ElementIconLightness = 0.8f

/** Duración del fade con el que aparece el elemento cuando su imagen está lista (o falló). */
const val ElementFadeInMs = 300

/** Ciclo de vida de la imagen de un elemento. */
private enum class ImageState {
    Loading, // cargando: el elemento todavía no se muestra
    Loaded,  // lista: aparece con la imagen
    Failed,  // sin imagen o no cargó: aparece con el título
}

/**
 * Un elemento cuadrado de la grilla (sistema o juego), con marco blanco redondeado.
 *
 * - [empty] = true: hueco de relleno, para que la grilla conserve su forma aunque no haya
 *   suficientes elementos. Dibuja solo un Container pequeñito en el centro.
 * - Si no: la imagen ([image]) llenando el cuadrado hasta el marco. Es un [Uri] porque así llegan
 *   todas las imágenes reales (GameEntry.iconUri, AssetRepository.systemLogoUri); para recursos de la app, usar `drawableUri(R.drawable.x)`.
 *   Sin imagen, o si no carga, muestra el [title] como reemplazo.
 * - Aparición: el elemento queda invisible mientras la imagen carga, y aparece con un fade
 *   ([ElementFadeInMs]) cuando está lista — o con el título, si no hay imagen o falló.
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
    @DrawableRes icon: Int? = null,
    empty: Boolean = false,
    cornerRadius: Dp = ElementCornerRadius,
) {
    val palette = GinnyThemes.current
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(10.dp)
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(10.dp)
                )                    // detrás de la imagen mientras carga
        ) {}
        if (!empty) {
            // Estado de la imagen. remember(image, icon): si cambian, se empieza de cero.
            // Un ícono de la app ya está listo; sin imagen, directo a mostrar el título.
            var state by remember(image, icon) {
                mutableStateOf(
                    when {
                        icon != null -> ImageState.Loaded
                        image == null -> ImageState.Failed
                        else -> ImageState.Loading
                    },
                )
            }

            // Opacidad del elemento completo: 0 mientras carga; cuando la imagen está lista (o
            // falló), sube a 1 con un fade. Animatable + LaunchedEffect (y no animateFloatAsState)
            // para que también haya fade cuando el elemento ya nace sin imagen.
            val alpha = remember(image, icon) { Animatable(0f) }
            LaunchedEffect(state) {
                if (state != ImageState.Loading) alpha.animateTo(1f, tween(ElementFadeInMs))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha.value } // se lee al dibujar: no recompone
                    // Sombra antes del fondo y la imagen: se dibuja por fuera del elemento. Con
                    // clip = true además recorta el contenido a las esquinas redondeadas; un
                    // .clip(shape) antes de la sombra la cortaría.
                    .shadow(ElementShadowElevation, shape, clip = true)
                    .background(Color.White),
                    //.border(ElementFrameWidth, Color.White, shape), // marco blanco, encima de la imagen
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    // Ícono de la app (ej. la estrella de Favoritos): chico, centrado y en un
                    // tono claro del tema.
                    Icon(
                        id = icon,
                        tint = palette.accent.adjust(lightness = ElementIconLightness),
                        size = size * ElementIconScale,
                    )
                } else if (image != null && state != ImageState.Failed) {
                    // La imagen se compone aunque el elemento sea invisible: así empieza a cargar ya.
                    AsyncImage(
                        model = image,
                        contentDescription = title,
                        contentScale = ContentScale.Crop, // llena el cuadrado, recortando si hace falta
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { state = ImageState.Loaded },
                        onError = { state = ImageState.Failed },
                    )
                }
                if (state == ImageState.Failed) {
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
