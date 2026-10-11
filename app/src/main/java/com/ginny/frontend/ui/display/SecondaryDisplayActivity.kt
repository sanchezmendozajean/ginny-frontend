package com.ginny.frontend.ui.display

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.ginny.frontend.GinnyApplication
import com.ginny.frontend.ui.screens.games.GameSidePanel
import com.ginny.frontend.ui.theme.GinnyTheme
import com.ginny.frontend.ui.theme.GinnyThemes

/** Margen alrededor de lo que se muestra. */
private val ContentPadding = 32.dp

/** Tamaño del ícono de la app (ej. la estrella de Favoritos). */
private val IconSize = 200.dp

/** Radio de las esquinas del grid y el wheel. */
private val ImageCornerRadius = 8.dp

/** Duración del cambio de fondo y de margen entre la caja y el video. */
private const val BackgroundFadeMs = 450

/**
 * La actividad de la otra pantalla (la secundaria, o la principal si se intercambiaron): solo dibuja lo que
 * publica la interfaz en [SecondaryScreen]. No toma el foco, para que el mando siga hablando con la interfaz.
 * La abre y la cierra [com.ginny.frontend.MainActivity] según haya o no otra pantalla.
 */
class SecondaryDisplayActivity : ComponentActivity() {

    /** Pantalla en la que corre (para saber si hay que moverla al cambiar la pantalla principal). */
    val displayIdNow: Int get() = window.decorView.display?.displayId ?: -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())

        val container = (application as GinnyApplication).container
        setContent {
            GinnyTheme(container.settingsRepository) { SecondaryScreenContent() }
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        /** La que está abierta ahora (o null). Solo se toca desde el hilo principal. */
        var instance: SecondaryDisplayActivity? = null
            private set
    }
}

@Composable
private fun SecondaryScreenContent() {
    val palette = GinnyThemes.current
    val detail = SecondaryScreen.detail

    // Caja sobre blanco; el video sobre negro y a pantalla completa (sin margen); el resto, sobre el fondo del tema.
    val videoOn = detail?.videoOn == true
    val target = when {
        videoOn -> Color.Black
        detail != null -> Color.White
        else -> palette.background
    }
    val background by animateColorAsState(target, tween(BackgroundFadeMs), label = "secondaryBackground")
    val padding by animateDpAsState(if (videoOn) 0.dp else ContentPadding, tween(BackgroundFadeMs), label = "secondaryPadding")

    Box(Modifier.fillMaxSize().background(background)) {
        if (detail != null) {
            GameSidePanel(
                assets = detail.assets,
                videoOn = detail.videoOn,
                videoVolume = detail.videoVolume,
                onVideoStopped = detail.onVideoStopped,
                modifier = Modifier.fillMaxSize().padding(padding),
                spinTrigger = detail.spinTrigger,
            )
        } else {
            Crossfade(targetState = SecondaryScreen.focus, label = "secondaryFocus") { content ->
                when (content) {
                    SecondaryContent.None -> Unit
                    is SecondaryContent.Image -> Box(Modifier.fillMaxSize().padding(ContentPadding), contentAlignment = Alignment.Center) {
                        when {
                            content.uri != null -> RoundedImage(content.uri)
                            content.icon != null -> Image(
                                painter = painterResource(content.icon),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(palette.accent),
                                modifier = Modifier.size(IconSize),
                            )
                        }
                    }
                }
            }
        }
        // Al lanzar un juego, esta pantalla también se funde a negro.
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = SecondaryScreen.darkness) })
    }
}

/** La imagen entera (sin recortar) con las esquinas redondeadas: se mide su proporción para redondear la imagen y no el espacio que sobra. */
@Composable
private fun RoundedImage(uri: android.net.Uri) {
    var aspect by remember(uri) { mutableFloatStateOf(1f) }
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) aspect = size.width / size.height
        },
        modifier = Modifier.aspectRatio(aspect).clip(RoundedCornerShape(ImageCornerRadius)),
    )
}
