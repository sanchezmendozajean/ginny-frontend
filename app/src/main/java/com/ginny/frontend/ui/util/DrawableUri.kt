package com.ginny.frontend.ui.util

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Uri de un recurso de la app (`android.resource://<paquete>/<id>`), para pasarlo donde se espera
 * una imagen como [Uri] — ej. `Element(image = drawableUri(R.drawable.ic_ginny))`. Útil para
 * pruebas y para imágenes de reemplazo; las imágenes reales vienen de la carpeta de Usuario.
 */
@Composable
fun drawableUri(@DrawableRes id: Int): Uri {
    val packageName = LocalContext.current.packageName
    return Uri.parse("android.resource://$packageName/$id")
}
