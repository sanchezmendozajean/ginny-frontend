package com.ginny.frontend.ui.theme

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState

private const val HoldMs = 6000L
private const val MoveMs = 2500L

/**
 * Pure port of the mockup's `cycleHue()`: holds each stop in [hues] for [holdMs], then eases into
 * the next one over [moveMs] with a cubic in-out curve, looping forever. Kept free of Compose so
 * it can be reasoned about/tested in isolation, same spirit as [com.ginny.frontend.ui.components.FocusGridMath].
 */
fun cycleHue(elapsedMs: Long, hues: List<Float>, holdMs: Long = HoldMs, moveMs: Long = MoveMs): Float {
    if (hues.isEmpty()) return 0f
    if (hues.size == 1) return hues[0]
    val period = holdMs + moveMs
    val t = elapsedMs % (period * hues.size)
    val i = (t / period).toInt()
    val p = t % period
    val a = hues[i]
    val b = hues[(i + 1) % hues.size]
    // Shortest signed delta a->b in [-180, 180]; b-a is in (-360, 360) since hues are in [0, 360),
    // so +540 before the modulo keeps the dividend positive without changing the result.
    val delta = ((b - a + 540f) % 360f) - 180f
    val k = if (p < holdMs) 0f else (p - holdMs).toFloat() / moveMs
    val eased = if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).let { it * it * it } / 2f
    return (a + delta * eased + 360f) % 360f
}

/** [ThemeMode]'s 4 fixed hues, in their declared order (Menta, Cielo, Durazno, Lila). */
val ThemeCycleHues: List<Float> = ThemeMode.entries.mapNotNull { it.hue }

/** Live-animating hue for [ThemeMode.CICLO] — restarts its clock each time it re-enters composition. */
@Composable
fun rememberCyclingHue(hues: List<Float> = ThemeCycleHues): State<Float> =
    produceState(initialValue = hues.first(), hues) {
        var startMs = -1L
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                if (startMs < 0) startMs = frameMs
                value = cycleHue(frameMs - startMs, hues)
            }
        }
    }
