package com.ginny.frontend.ui.components.box

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Las seis caras de la caja. Las tres primeras llevan textura (frente, trasera y lomo). */
enum class BoxFace { FRONT, BACK, SPINE, RIGHT, TOP, BOTTOM }

/** Un punto o vector en el espacio. Con la caja de frente al espectador: x → derecha, y → arriba, z → hacia él. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun normalized(): Vec3 {
        val length = sqrt(dot(this))
        return if (length == 0f) this else Vec3(x / length, y / length, z / length)
    }
}

/** Un punto en pantalla, en unidades de la caja (la altura de la caja vale 1). Y crece hacia abajo. */
data class ScreenPoint(val x: Float, val y: Float)

/** Una cara visible ya proyectada: sus cuatro esquinas (en el orden de la textura) y cuánta luz recibe. */
data class ProjectedFace(
    val face: BoxFace,
    /** Arriba-izquierda, arriba-derecha, abajo-derecha y abajo-izquierda, **mirando la cara desde fuera**. */
    val corners: List<ScreenPoint>,
    /** Distancia al espectador (menor = más cerca). */
    val distance: Float,
    /** 0…1: 1 = tan iluminada como la luz permite. */
    val light: Float,
)

/**
 * Una caja de medidas [width] × [height] × [depth], centrada en el origen, y cómo se ve girada. Código
 * puro: no depende de Android, así se prueba con pruebas unitarias. El dibujo está en [GameBox3D].
 *
 * Convención de las texturas: cada cara se ve **sin espejar y derecha** desde fuera. El lomo está a la
 * izquierda del frente, como en una caja de juego real.
 */
class BoxGeometry(val width: Float, val height: Float = 1f, val depth: Float) {

    private val w = width / 2
    private val h = height / 2
    private val d = depth / 2

    /** Normal de la cara (hacia afuera). */
    fun normal(face: BoxFace): Vec3 = when (face) {
        BoxFace.FRONT -> Vec3(0f, 0f, 1f)
        BoxFace.BACK -> Vec3(0f, 0f, -1f)
        BoxFace.SPINE -> Vec3(-1f, 0f, 0f)
        BoxFace.RIGHT -> Vec3(1f, 0f, 0f)
        BoxFace.TOP -> Vec3(0f, 1f, 0f)
        BoxFace.BOTTOM -> Vec3(0f, -1f, 0f)
    }

    /** Esquinas de la cara: arriba-izquierda, arriba-derecha, abajo-derecha y abajo-izquierda, vistas desde fuera. */
    fun corners(face: BoxFace): List<Vec3> = when (face) {
        BoxFace.FRONT -> listOf(Vec3(-w, h, d), Vec3(w, h, d), Vec3(w, -h, d), Vec3(-w, -h, d))
        BoxFace.BACK -> listOf(Vec3(w, h, -d), Vec3(-w, h, -d), Vec3(-w, -h, -d), Vec3(w, -h, -d))
        BoxFace.SPINE -> listOf(Vec3(-w, h, -d), Vec3(-w, h, d), Vec3(-w, -h, d), Vec3(-w, -h, -d))
        BoxFace.RIGHT -> listOf(Vec3(w, h, d), Vec3(w, h, -d), Vec3(w, -h, -d), Vec3(w, -h, d))
        // Arriba y abajo: el "arriba" de la textura apunta hacia atrás (arriba) o hacia el frente (abajo).
        BoxFace.TOP -> listOf(Vec3(-w, h, -d), Vec3(w, h, -d), Vec3(w, h, d), Vec3(-w, h, d))
        BoxFace.BOTTOM -> listOf(Vec3(-w, -h, d), Vec3(w, -h, d), Vec3(w, -h, -d), Vec3(-w, -h, -d))
    }

    private fun center(face: BoxFace): Vec3 {
        val c = corners(face)
        return Vec3(c.sumOf { it.x.toDouble() }.toFloat() / 4, c.sumOf { it.y.toDouble() }.toFloat() / 4, c.sumOf { it.z.toDouble() }.toFloat() / 4)
    }

    /**
     * Las caras que se ven con la caja girada [yawRadians] sobre su eje vertical (positivo: el frente
     * gira hacia la derecha y deja ver el lomo) e inclinada [pitchRadians] (positivo: se ve un poco la
     * tapa). La cámara está a [cameraDistance] del centro, mirándolo. Salen ordenadas de la más lejana a
     * la más cercana, para dibujarlas en ese orden.
     */
    fun visibleFaces(yawRadians: Float, pitchRadians: Float, cameraDistance: Float): List<ProjectedFace> {
        val camera = Vec3(0f, 0f, cameraDistance)
        return BoxFace.entries.mapNotNull { face ->
            val n = rotate(normal(face), yawRadians, pitchRadians)
            val c = rotate(center(face), yawRadians, pitchRadians)
            // Se ve si mira hacia la cámara (descarte de caras traseras, con perspectiva).
            if (n.dot(camera - c) <= 0f) return@mapNotNull null

            val corners = corners(face).map { project(rotate(it, yawRadians, pitchRadians), cameraDistance) }
            val distance = sqrt((camera - c).dot(camera - c))
            ProjectedFace(face, corners, distance, light(n))
        }.sortedByDescending { it.distance }
    }

    /** Medio ancho, en unidades de la caja, del mayor recorrido horizontal posible (para encajarla en el lienzo). */
    val maxHalfExtent: Float get() = max(sqrt(w * w + d * d), h)

    companion object {
        /** Luz fija, desde arriba a la izquierda y hacia el espectador (en el espacio de la cámara). */
        private val LightDirection = Vec3(-0.4f, 0.6f, 0.7f).normalized()

        /** Cuánta luz recibe una cara cuya normal (ya girada) es [normal]: nunca menos de 0,55. */
        fun light(normal: Vec3): Float = 0.55f + 0.45f * max(0f, normal.dot(LightDirection))

        /** Gira sobre el eje vertical y luego inclina sobre el horizontal. */
        fun rotate(v: Vec3, yaw: Float, pitch: Float): Vec3 {
            val cy = cos(yaw)
            val sy = sin(yaw)
            val x1 = v.x * cy + v.z * sy
            val z1 = -v.x * sy + v.z * cy
            val cp = cos(pitch)
            val sp = sin(pitch)
            return Vec3(x1, v.y * cp - z1 * sp, v.y * sp + z1 * cp)
        }

        /** Proyección en perspectiva: lo cercano se ve más grande. La cámara está en z = [cameraDistance]. */
        fun project(v: Vec3, cameraDistance: Float): ScreenPoint {
            val k = cameraDistance / (cameraDistance - v.z)
            return ScreenPoint(v.x * k, -v.y * k)
        }

        /**
         * Proporciones de la caja **a partir de sus texturas** (alto = 1): el ancho sale del aspecto del
         * frente (o de la trasera, si falta) y el fondo del aspecto del lomo (ancho ÷ alto de su imagen).
         * Los topes solo protegen de imágenes absurdas; no deforman una caja normal.
         */
        fun fromTextures(frontAspect: Float?, backAspect: Float? = null, spineAspect: Float?): BoxGeometry = BoxGeometry(
            width = (frontAspect ?: backAspect ?: DefaultWidth).coerceIn(0.2f, 2.5f),
            depth = (spineAspect ?: DefaultDepth).coerceIn(0.02f, 0.8f),
        )

        /** Ancho (con alto 1) y fondo de una caja cuando no se conocen sus texturas. */
        const val DefaultWidth = 0.72f
        const val DefaultDepth = 0.12f
    }
}
