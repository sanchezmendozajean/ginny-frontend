package com.ginny.frontend.ui.components.box

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/** Las texturas de la caja ya decodificadas; una cara sin imagen queda negra. */
class BoxTextures(val front: Bitmap?, val back: Bitmap?, val spine: Bitmap?) {
    val isEmpty: Boolean get() = front == null && back == null && spine == null
}

/** Giro automático: muy despacio (una vuelta completa en un minuto). */
private const val AutoSpinDegreesPerSecond = 6f

/** Posición de partida: en 3/4 de perfil, con el frente a la derecha y el lomo a la vista. */
private const val StartYawDegrees = 35f

/** Sin inclinación: la cámara mira la caja de frente, así el centro de fuga queda en su centro (perspectiva simétrica arriba y abajo). */
private const val PitchDegrees = 0f

/** Cuánto gira la caja por cada píxel que se arrastra (suave: no sigue al dedo a lo loco). */
private const val DegreesPerDragPixel = 0.4f

/** Distancia de la cámara, en alturas de caja: más lejos = menos perspectiva. */
private const val CameraDistance = 3.2f

/** Qué tan rápido el giro vuelve al ritmo automático después de soltarla (por segundo). */
private const val Settle = 2.2f

private const val BaseBoxColor = 0xFF161616.toInt()

/**
 * Caja de juego en 3D: una caja negra con las texturas de [textures] en el frente, la trasera y el
 * lomo. Gira sola, muy despacio, sobre su eje vertical, empezando en 3/4 de perfil; se puede girar con
 * el dedo arrastrando en horizontal (el arrastre vertical queda libre para desplazar la pantalla), y al
 * soltarla recupera con suavidad el giro automático.
 *
 * No usa ninguna librería 3D: proyecta las caras con [BoxGeometry] y dibuja cada textura sobre su
 * cuadrilátero con una matriz de perspectiva (`Matrix.setPolyToPoly`). El ángulo se lee solo al dibujar,
 * así que girar no recompone nada.
 */
@Composable
fun GameBox3D(textures: BoxTextures, modifier: Modifier = Modifier) {
    val geometry = remember(textures) {
        BoxGeometry.fromTextures(
            frontAspect = textures.front?.let { it.width.toFloat() / it.height },
            backAspect = textures.back?.let { it.width.toFloat() / it.height },
            // El fondo de la caja sale del ancho del lomo respecto de su alto.
            spineAspect = textures.spine?.let { it.width.toFloat() / it.height },
        )
    }

    var yaw by remember { mutableFloatStateOf(StartYawDegrees) }
    var velocity by remember { mutableFloatStateOf(AutoSpinDegreesPerSecond) }
    var dragging by remember { mutableStateOf(false) }

    // Giro automático (y vuelta suave a él tras arrastrar). Se detiene solo al salir de pantalla.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                if (!dragging) {
                    velocity += (AutoSpinDegreesPerSecond - velocity) * min(1f, dt * Settle)
                    yaw += velocity * dt
                }
            }
        }
    }

    val paints = remember { BoxPaints() }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            var lastTime = 0L
            detectHorizontalDragGestures(
                onDragStart = {
                    dragging = true
                    velocity = 0f
                    lastTime = 0L
                },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
            ) { change, dragAmount ->
                val delta = dragAmount * DegreesPerDragPixel
                yaw += delta
                // La velocidad del dedo se conserva al soltar y luego se apaga sola hacia el giro lento.
                val dtMs = change.uptimeMillis - lastTime
                if (lastTime != 0L && dtMs > 0) {
                    velocity = (velocity * 0.6f + (delta / (dtMs / 1000f)) * 0.4f).coerceIn(-240f, 240f)
                }
                lastTime = change.uptimeMillis
                change.consume()
            }
        },
    ) {
        // La caja se centra en el lienzo, en vertical también: con el centro de fuga en el centro de la caja
        // la perspectiva es simétrica arriba y abajo.
        val centerX = size.width / 2
        val centerY = size.height / 2
        val scale = min(size.height / (geometry.height * 1.35f), size.width / (geometry.maxHalfExtent * 2f * 1.25f))

        // Sombra difusa detrás de la caja: un círculo con degradado radial, tenue y fijo (no gira ni se
        // anima con la caja).
        val glowRadius = scale * 0.85f
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Black.copy(alpha = 0.22f),
                    0.55f to Color.Black.copy(alpha = 0.10f),
                    1f to Color.Transparent,
                ),
                center = Offset(centerX, centerY),
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = Offset(centerX, centerY),
        )

        val faces = geometry.visibleFaces(
            yawRadians = (yaw * PI / 180).toFloat(),
            pitchRadians = (PitchDegrees * PI / 180).toFloat(),
            cameraDistance = CameraDistance * geometry.height,
        )

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            faces.forEach { projected ->
                val pts = FloatArray(8)
                projected.corners.forEachIndexed { i, p ->
                    pts[i * 2] = centerX + p.x * scale
                    pts[i * 2 + 1] = centerY + p.y * scale
                }
                val bitmap = when (projected.face) {
                    BoxFace.FRONT -> textures.front
                    BoxFace.BACK -> textures.back
                    BoxFace.SPINE -> textures.spine
                    else -> null
                }

                val path = Path().apply {
                    moveTo(pts[0], pts[1]); lineTo(pts[2], pts[3]); lineTo(pts[4], pts[5]); lineTo(pts[6], pts[7]); close()
                }
                if (bitmap != null) {
                    // La textura se estira sobre el cuadrilátero proyectado (perspectiva incluida).
                    val src = floatArrayOf(0f, 0f, bitmap.width.toFloat(), 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), 0f, bitmap.height.toFloat())
                    val matrix = Matrix()
                    if (matrix.setPolyToPoly(src, 0, pts, 0, 4)) native.drawBitmap(bitmap, matrix, paints.bitmap)
                } else {
                    native.drawPath(path, paints.fill.apply { color = BaseBoxColor })
                }
                // Sombreado según la luz que recibe la cara, y un borde fino para que se note su contorno.
                native.drawPath(path, paints.shade.apply { alpha = ((1f - projected.light) * 255).toInt().coerceIn(0, 255) })
                native.drawPath(path, paints.edge)
            }
        }
    }
}

/** Los pinceles se crean una vez: dibujar 60 veces por segundo no debe reservar objetos. */
private class BoxPaints {
    val bitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.BLACK }
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = 0x66000000
    }
}

/**
 * Decodifica las tres imágenes de la caja en segundo plano, reducidas para no gastar memoria de más.
 * Devuelve null mientras carga; si ninguna se puede leer, una [BoxTextures] vacía.
 */
@Composable
fun rememberBoxTextures(front: Uri?, back: Uri?, spine: Uri?): BoxTextures? {
    val context = LocalContext.current
    val textures by produceState<BoxTextures?>(initialValue = null, front, back, spine) {
        value = withContext(Dispatchers.IO) {
            BoxTextures(
                front = decodeScaled(context, front),
                back = decodeScaled(context, back),
                spine = decodeScaled(context, spine),
            )
        }
    }
    return textures
}

/** Lado mayor máximo, en píxeles, al que se reducen las texturas (sobra para una caja de ~300 dp). */
private const val MaxTextureSide = 900

private fun decodeScaled(context: Context, uri: Uri?): Bitmap? {
    if (uri == null) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = max(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return@runCatching null

        var sample = 1
        while (longSide / (sample * 2) >= MaxTextureSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}
