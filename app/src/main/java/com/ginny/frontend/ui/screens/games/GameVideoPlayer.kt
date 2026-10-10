package com.ginny.frontend.ui.screens.games

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Radio de las esquinas del video. */
private val VideoCornerRadius = 4.dp

/** Proporción que se usa hasta conocer la real del video (los de ScreenScraper suelen ser 4:3 o 16:9). */
private const val DefaultVideoAspect = 4f / 3f

/**
 * Reproduce el [uri] (un MP4 del juego) con el MediaPlayer del sistema, sin librerías, dentro del espacio
 * que le den ([modifier]), conservando su proporción y centrado. Usa un TextureView para que se pueda
 * desvanecer con `graphicsLayer { alpha }`. Se reproduce solo una vez: al terminar llama a [onEnded]; si
 * no se puede reproducir, a [onError]. [volume] (0…1) se aplica en vivo y el reproductor se libera al salir.
 */
@Composable
internal fun GameVideoPlayer(
    uri: Uri,
    volume: Float,
    modifier: Modifier = Modifier,
    onEnded: () -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentOnError by rememberUpdatedState(onError)
    var aspect by remember(uri) { mutableFloatStateOf(DefaultVideoAspect) }

    val player = remember(uri) {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            setOnVideoSizeChangedListener { _, w, h -> if (w > 0 && h > 0) aspect = w.toFloat() / h }
            setOnPreparedListener { it.start() }
            setOnCompletionListener { currentOnEnded() }
            setOnErrorListener { _, _, _ -> currentOnError(); true }
        }
    }
    DisposableEffect(player) { onDispose { runCatching { player.release() } } }

    // Se reaplica al cambiar el volumen y al quedar listo el reproductor.
    LaunchedEffect(player, volume) { runCatching { player.setVolume(volume, volume) } }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            // Las esquinas se redondean sobre el video mismo (no sobre el espacio que le sobra alrededor).
            modifier = Modifier.aspectRatio(aspect).clip(RoundedCornerShape(VideoCornerRadius)),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            runCatching {
                                player.setSurface(Surface(texture))
                                player.setDataSource(context, uri)
                                player.prepareAsync()
                            }.onFailure { currentOnError() }
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
        )
    }
}
