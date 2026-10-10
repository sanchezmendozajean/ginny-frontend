package com.ginny.frontend.ui.components.box

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class BoxGeometryTest {

    private val box = BoxGeometry(width = 0.72f, depth = 0.12f)
    private val camera = 3f

    private fun rad(degrees: Float) = (degrees * PI / 180).toFloat()
    private fun facesAt(yaw: Float, pitch: Float = 0f) = box.visibleFaces(rad(yaw), rad(pitch), camera).map { it.face }

    @Test
    fun `de frente solo se ve el frente`() {
        assertEquals(listOf(BoxFace.FRONT), facesAt(0f))
    }

    @Test
    fun `en tres cuartos de perfil se ven el frente y el lomo`() {
        assertEquals(setOf(BoxFace.FRONT, BoxFace.SPINE), facesAt(35f).toSet())
    }

    @Test
    fun `girando hacia el otro lado se ve el costado derecho`() {
        assertEquals(setOf(BoxFace.FRONT, BoxFace.RIGHT), facesAt(-35f).toSet())
    }

    @Test
    fun `de espaldas solo se ve la trasera`() {
        assertEquals(listOf(BoxFace.BACK), facesAt(180f))
    }

    @Test
    fun `inclinada se ve tambien la tapa`() {
        assertTrue(BoxFace.TOP in facesAt(35f, pitch = 10f))
        assertTrue(BoxFace.TOP !in facesAt(35f, pitch = 0f))
    }

    @Test
    fun `las caras salen de la mas lejana a la mas cercana`() {
        val faces = box.visibleFaces(rad(35f), rad(10f), camera)
        assertEquals(faces.sortedByDescending { it.distance }, faces)
        assertTrue(faces.size >= 2)
    }

    @Test
    fun `el frente de cara conserva su forma y queda centrado`() {
        val front = box.visibleFaces(0f, 0f, camera).single()
        val (tl, tr, br, bl) = front.corners
        assertEquals(-tl.x, tr.x, 1e-4f)             // simétrico a izquierda y derecha
        assertEquals(tl.y, tr.y, 1e-4f)              // borde superior horizontal
        assertTrue(tl.y < bl.y)                       // la Y de pantalla crece hacia abajo
        assertTrue(br.x > bl.x)
    }

    @Test
    fun `las texturas no salen espejadas`() {
        // En cada cara visible, el borde izquierdo de la textura queda a la izquierda del derecho y el
        // superior arriba del inferior (la Y de pantalla crece hacia abajo).
        fun check(yaw: Float) = box.visibleFaces(rad(yaw), rad(8f), camera)
            .filter { it.face in setOf(BoxFace.FRONT, BoxFace.BACK, BoxFace.SPINE) }
            .forEach { f ->
                val (tl, tr, br, bl) = f.corners
                assertTrue("${f.face} a $yaw°: izquierda < derecha", tl.x < tr.x && bl.x < br.x)
                assertTrue("${f.face} a $yaw°: arriba < abajo", tl.y < bl.y && tr.y < br.y)
            }
        listOf(0f, 35f, 80f, 145f, 180f, 215f, 280f, 325f).forEach(::check)
    }

    @Test
    fun `a tres cuartos el lomo queda a la izquierda del frente`() {
        val faces = box.visibleFaces(rad(35f), 0f, camera).associateBy { it.face }
        val spineRight = faces.getValue(BoxFace.SPINE).corners.maxOf { it.x }
        val frontLeft = faces.getValue(BoxFace.FRONT).corners.minOf { it.x }
        assertEquals(frontLeft, spineRight, 1e-4f) // comparten la arista
        assertTrue(faces.getValue(BoxFace.SPINE).corners.minOf { it.x } < frontLeft)
    }

    @Test
    fun `lo cercano se ve mas grande que lo lejano`() {
        val near = BoxGeometry.project(Vec3(1f, 0f, 1f), camera)
        val far = BoxGeometry.project(Vec3(1f, 0f, -1f), camera)
        assertTrue(near.x > far.x)
    }

    @Test
    fun `la luz nunca baja de un minimo`() {
        BoxFace.entries.forEach { face ->
            val light = BoxGeometry.light(box.normal(face))
            assertTrue(light in 0.55f..1f)
        }
    }

    @Test
    fun `las proporciones salen de las texturas con topes`() {
        // Una caja normal no se deforma: sus medidas son las de sus imágenes.
        val normal = BoxGeometry.fromTextures(frontAspect = 0.75f, backAspect = 0.7f, spineAspect = 0.09f)
        assertEquals(0.75f, normal.width, 1e-6f)
        assertEquals(0.09f, normal.depth, 1e-6f)

        // Solo las imágenes absurdas se topan.
        val absurd = BoxGeometry.fromTextures(frontAspect = 9f, spineAspect = 0.0f)
        assertEquals(2.5f, absurd.width, 1e-6f)
        assertEquals(0.02f, absurd.depth, 1e-6f)

        // Sin frente se usa la trasera; sin nada, medidas por defecto.
        assertEquals(0.7f, BoxGeometry.fromTextures(null, 0.7f, null).width, 1e-6f)
        val d = BoxGeometry.fromTextures(null, null, null)
        assertEquals(BoxGeometry.DefaultWidth, d.width, 1e-6f)
        assertEquals(BoxGeometry.DefaultDepth, d.depth, 1e-6f)
    }

    @Test
    fun `un lomo apaisado va arriba y su fondo sale del ancho de la caja`() {
        // Caja de SNES: frente 2:1 apaisado, lomo 8:1 recorriendo el ancho → fondo = 2 / 8.
        val snes = BoxGeometry.fromTextures(frontAspect = 2f, spineAspect = 8f)
        assertEquals(2f, snes.width, 1e-6f)
        assertEquals(0.25f, snes.depth, 1e-6f)
        assertEquals(true, BoxGeometry.isSpineHorizontal(8f))
        assertEquals(false, BoxGeometry.isSpineHorizontal(0.09f))
        assertEquals(false, BoxGeometry.isSpineHorizontal(null))
    }
}
