package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.ginny.frontend.ui.theme.GinnyThemes
import com.ginny.frontend.ui.theme.adjust
import com.ginny.frontend.ui.theme.complementary
import com.ginny.frontend.ui.theme.linearGradient
import com.ginny.frontend.ui.theme.rotateHue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── Ajustes ───────────────────────────────────────────────────────────────────────────────

/** Animación de las formas. Futuro: interruptor "Fondo animado" en Configuración. */
private const val AnimationEnabled = true

/** ~30 fps: con movimientos lentos se ve igual que 60 y cuesta la mitad. */
private const val FrameIntervalMs = 33L

/** Cambiar la semilla genera otra composición (siempre la misma para una misma semilla). */
private const val Seed = 7

/** Grilla imaginaria: una forma por celda, para repartirlas sin amontonar. */
private const val GridColumns = 7
private const val GridRows = 4

/**
 * Movimiento de cada forma: recorre un óvalo de radio entre [MinAmplitudeDp] y [MaxAmplitudeDp],
 * dando una vuelta cada [MinPeriodS]–[MaxPeriodS] segundos. Más radio o menos segundos = más
 * movimiento. (Con 3–8 dp y 12–24 s era ~1-2 dp/s: se movía, pero no se notaba.)
 */
private const val MinAmplitudeDp = 10f
private const val MaxAmplitudeDp = 22f
private const val MinPeriodS = 7f
private const val MaxPeriodS = 14f

/**
 * Rotación sutil, "como en el espacio": cada forma gira sin parar, muy despacio, en su propio
 * sentido (horario o antihorario) y desde su propio ángulo inicial. Una vuelta completa tarda
 * entre [MinSpinPeriodS] y [MaxSpinPeriodS] segundos: más segundos = giro más lento.
 */
private const val MinSpinPeriodS = 40f
private const val MaxSpinPeriodS = 90f

/** Color de las formas: blanco translúcido, para que el fondo ambiente sin competir. */
private val ShapeColor = Color.White.copy(alpha = 0.55f)

// ── Componente ────────────────────────────────────────────────────────────────────────────

/**
 * Fondo de la app: degradado entre el color del tema y su complementario (tono + 180°), con formas
 * geométricas de trazo fino que flotan lentamente. Original, inspirado en el estilo "memphis".
 *
 * Rendimiento: la animación solo provoca *repintado* (el tiempo se lee dentro del dibujo), nunca
 * recomposición; y la geometría se calcula una vez por tamaño de pantalla (drawWithCache).
 * Se pausa sola cuando la app pasa a segundo plano (ej. al abrir un emulador).
 */
@Composable
fun GeometricBackground(modifier: Modifier = Modifier) {
    val palette = GinnyThemes.current
    // Pastel claro del tema arriba a la derecha; su opuesto abajo a la izquierda; y en el medio,
    // el tono a mitad de camino (+90°), para que el centro no se vuelva gris al mezclar opuestos.
    val themeColor = palette.accent.adjust(lightness = 0.86f, chroma = 0.09f)
    val middleColor = themeColor.rotateHue(90f)
    val oppositeColor = themeColor.complementary()

    val decos = remember { generateDecos(Seed) }
    val time = remember { mutableFloatStateOf(0f) } // segundos desde que apareció

    if (AnimationEnabled) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (now - last >= FrameIntervalMs * 1_000_000) {
                        last = now
                        time.floatValue = (now - start) / 1_000_000_000f
                    }
                }
            }
        }
    }

    Spacer(
        modifier = modifier.drawWithCache {
            // Esto corre solo cuando cambia el tamaño o el tema: aquí se calcula la geometría.
            val gradient = linearGradient(45f, listOf(oppositeColor, middleColor, themeColor))
            val stroke = Stroke(width = 1.5f * density)
            val placed = decos.map { it.place(this, size, stroke) }

            onDrawBehind {
                // Esto corre en cada fotograma: solo dibuja, con el desplazamiento del momento.
                drawRect(gradient)
                val t = time.floatValue
                for (p in placed) {
                    val angle = 2f * PI.toFloat() * t / p.periodS + p.phase
                    val dx = p.amplitudePx * sin(angle)
                    val dy = p.amplitudePx * cos(angle * 0.7f + p.phase)
                    // Giro continuo: ángulo inicial + velocidad × tiempo.
                    val spin = (p.startAngleDeg + p.spinDegPerS * t) % 360f
                    translate(p.center.x + dx, p.center.y + dy) {
                        // Gira alrededor del centro de la forma (0, 0 tras el translate).
                        rotate(spin, pivot = Offset.Zero) { p.draw(this) }
                    }
                }
            }
        },
    )
}

// ── Formas ────────────────────────────────────────────────────────────────────────────────

private enum class Kind { Square, Circle, Triangle, Plus, Zigzag, DotsLine, DotGrid, HatchedL, TallRect }

/** Más probabilidad = aparece más seguido. */
private val KindWeights = listOf(
    Kind.DotGrid to 4, Kind.Square to 3, Kind.Circle to 2, Kind.Triangle to 2, Kind.HatchedL to 2,
    Kind.Plus to 1, Kind.Zigzag to 1, Kind.DotsLine to 1, Kind.TallRect to 1,
)

/** Una forma "en abstracto": posición relativa (0..1) y tamaño en dp, sin píxeles todavía. */
private data class Deco(
    val kind: Kind,
    val x: Float,
    val y: Float,
    val scale: Float,
    val cols: Int,
    val rows: Int,
    val periodS: Float,
    val phase: Float,
    val amplitudeDp: Float,
    val startAngleDeg: Float,
    val spinDegPerS: Float, // negativo = antihorario
)

/** Una forma ya ubicada en píxeles, con su función de dibujo alrededor de (0, 0). */
private class PlacedDeco(
    val center: Offset,
    val amplitudePx: Float,
    val periodS: Float,
    val phase: Float,
    val startAngleDeg: Float,
    val spinDegPerS: Float,
    val draw: DrawScope.() -> Unit,
)

private fun generateDecos(seed: Int): List<Deco> {
    val rnd = Random(seed)
    val totalWeight = KindWeights.sumOf { it.second }
    fun pickKind(): Kind {
        var r = rnd.nextInt(totalWeight)
        for ((kind, w) in KindWeights) {
            if (r < w) return kind
            r -= w
        }
        return Kind.Square
    }
    return buildList {
        for (row in 0 until GridRows) for (col in 0 until GridColumns) {
            add(
                Deco(
                    kind = pickKind(),
                    x = (col + 0.15f + rnd.nextFloat() * 0.7f) / GridColumns,
                    y = (row + 0.15f + rnd.nextFloat() * 0.7f) / GridRows,
                    scale = 0.75f + rnd.nextFloat() * 0.5f,
                    cols = 4 + rnd.nextInt(6),
                    rows = 3 + rnd.nextInt(5),
                    periodS = MinPeriodS + rnd.nextFloat() * (MaxPeriodS - MinPeriodS),
                    phase = rnd.nextFloat() * 2f * PI.toFloat(),
                    amplitudeDp = MinAmplitudeDp + rnd.nextFloat() * (MaxAmplitudeDp - MinAmplitudeDp),
                    startAngleDeg = rnd.nextFloat() * 360f,
                    spinDegPerS = (if (rnd.nextBoolean()) 1f else -1f) *
                        360f / (MinSpinPeriodS + rnd.nextFloat() * (MaxSpinPeriodS - MinSpinPeriodS)),
                ),
            )
        }
    }
}

/** Convierte una [Deco] a píxeles para este tamaño de pantalla y prepara su dibujo. */
private fun Deco.place(scope: androidx.compose.ui.unit.Density, size: Size, stroke: Stroke): PlacedDeco {
    val u = scope.density * scale // 1 "dp escalado" en píxeles
    val draw: DrawScope.() -> Unit = when (kind) {
        Kind.Square -> {
            val s = 14f * u
            drawing { drawRect(ShapeColor, Offset(-s / 2, -s / 2), Size(s, s), style = stroke) }
        }
        Kind.Circle -> {
            val r = 7f * u
            drawing { drawCircle(ShapeColor, r, Offset.Zero, style = stroke) }
        }
        Kind.Triangle -> {
            val s = 16f * u
            val h = s * 0.866f
            val path = Path().apply {
                moveTo(0f, -h * 2 / 3); lineTo(s / 2, h / 3); lineTo(-s / 2, h / 3); close()
            }
            drawing { drawPath(path, ShapeColor, style = stroke) }
        }
        Kind.Plus -> {
            val arm = 6f * u
            val gap = 12f * u
            drawing {
                for (cx in listOf(-gap, gap).map { it / 2 }) {
                    drawLine(ShapeColor, Offset(cx - arm, 0f), Offset(cx + arm, 0f), stroke.width)
                    drawLine(ShapeColor, Offset(cx, -arm), Offset(cx, arm), stroke.width)
                }
            }
        }
        Kind.Zigzag -> {
            val w = 36f * u
            val a = 4f * u
            val path = Path().apply {
                moveTo(-w / 2, 0f)
                for (i in 1..6) lineTo(-w / 2 + w * i / 6, if (i % 2 == 1) -a else 0f)
            }
            drawing { drawPath(path, ShapeColor, style = stroke) }
        }
        Kind.DotsLine -> {
            val step = 8f * u
            val dots = (0 until 4).map { Offset(-2.5f * step + it * step, 0f) }
            val ringR = 5f * u
            drawing {
                drawPoints(dots, PointMode.Points, ShapeColor, 3f * scope.density, StrokeCap.Round)
                drawCircle(ShapeColor, ringR, Offset(2.5f * step, 0f), style = stroke)
            }
        }
        Kind.DotGrid -> {
            val step = 9f * u
            val w = (cols - 1) * step
            val h = (rows - 1) * step
            val dots = buildList {
                for (r in 0 until rows) for (c in 0 until cols) add(Offset(-w / 2 + c * step, -h / 2 + r * step))
            }
            drawing { drawPoints(dots, PointMode.Points, ShapeColor, 3f * scope.density, StrokeCap.Round) }
        }
        Kind.HatchedL -> {
            val b = 26f * u // lado de cada uno de los 3 bloques de la L
            val path = Path().apply {
                addRect(Rect(-b, -b, b, 0f)) // fila de arriba: 2 bloques
                addRect(Rect(0f, 0f, b, b))  // abajo a la derecha: 1 bloque
            }
            val stepH = 5f * u
            drawing {
                clipPath(path) {
                    var x = -3 * b
                    while (x < 3 * b) {
                        drawLine(ShapeColor, Offset(x, b), Offset(x + 2 * b, -b), stroke.width)
                        x += stepH
                    }
                }
            }
        }
        Kind.TallRect -> {
            val w = 44f * u
            val h = 100f * u
            drawing { drawRect(ShapeColor, Offset(-w / 2, -h / 2), Size(w, h), style = stroke) }
        }
    }
    return PlacedDeco(
        center = Offset(x * size.width, y * size.height),
        amplitudePx = amplitudeDp * scope.density,
        periodS = periodS,
        phase = phase,
        startAngleDeg = startAngleDeg,
        spinDegPerS = spinDegPerS,
        draw = draw,
    )
}

/** Solo para declarar una función de dibujo de forma explícita. */
private fun drawing(block: DrawScope.() -> Unit): DrawScope.() -> Unit = block
