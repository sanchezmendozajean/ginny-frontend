package com.ginny.frontend.ui.display

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ginny.frontend.data.scrap.GameAssets

/** Lo que muestra la pantalla secundaria cuando no hay un juego abierto: lo que la interfaz tiene enfocado. */
sealed interface SecondaryContent {
    data object None : SecondaryContent

    /** Una imagen: el logo del sistema enfocado, o el wheel (o el grid) del juego enfocado. [icon] = ícono de la app. */
    data class Image(val uri: Uri?, @DrawableRes val icon: Int? = null) : SecondaryContent
}

/** El modal de un juego abierto: la caja en 3D y el video, que pasan a la pantalla secundaria. */
data class SecondaryGameDetail(
    val assets: GameAssets,
    val videoOn: Boolean,
    val videoVolume: Float,
    val spinTrigger: Int,
    val onVideoStopped: () -> Unit,
)

/**
 * Lo que la interfaz le pide a la pantalla secundaria. Es estado de Compose compartido por todo el proceso:
 * la interfaz lo escribe y [SecondaryDisplayActivity] lo lee, aunque corran en pantallas distintas.
 */
object SecondaryScreen {
    /** Lo enfocado en la grilla (sistema o juego). */
    var focus by mutableStateOf<SecondaryContent>(SecondaryContent.None)

    /** El juego abierto en su modal; tiene prioridad sobre [focus] mientras no sea null. */
    var detail by mutableStateOf<SecondaryGameDetail?>(null)

    /** Oscurecimiento (0 = nada, 1 = negro) al lanzar un juego: acompaña al de la pantalla de la interfaz. */
    var darkness by mutableFloatStateOf(0f)
}
