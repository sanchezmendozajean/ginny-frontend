package com.ginny.frontend.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** Cuánto se queda cada tema antes de pasar al siguiente. */
const val ThemeCycleHoldMs = 30_000L

/** Cuánto dura la transición de un tema al siguiente. */
const val ThemeCycleMoveMs = 3_000L

/** Mezcla dos paletas papel por papel ([t] = 0 → this, 1 → other). */
fun GinnyPalette.mix(other: GinnyPalette, t: Float) = GinnyPalette(
    background = background.mixOklab(other.background, t),
    accent = accent.mixOklab(other.accent, t),
    ink = ink.mixOklab(other.ink, t),
    muted = muted.mixOklab(other.muted, t),
)

/**
 * Paleta del "Cambio cíclico": se queda [ThemeCycleHoldMs] en cada tema fijo y pasa al siguiente
 * en [ThemeCycleMoveMs], en bucle. Solo existe mientras ese modo está elegido.
 *
 * Rendimiento (regla del plan: nada de animaciones permanentes que recompongan cada cuadro):
 * mientras un tema se mantiene, la corrutina solo espera (delay) y no hay ningún trabajo; los
 * cuadros se recorren únicamente durante la transición.
 */
@Composable
fun rememberCyclingPalette(): State<GinnyPalette> {
    val palettes = GinnyThemes.fixed.map(GinnyThemes::paletteFor)
    return produceState(initialValue = palettes.first()) {
        var i = 0
        while (true) {
            delay(ThemeCycleHoldMs)
            val from = palettes[i]
            val to = palettes[(i + 1) % palettes.size]
            var startMs = -1L
            var k = 0f
            while (k < 1f) {
                withInfiniteAnimationFrameMillis { frameMs ->
                    if (startMs < 0) startMs = frameMs
                    k = ((frameMs - startMs).toFloat() / ThemeCycleMoveMs).coerceAtMost(1f)
                    value = from.mix(to, FastOutSlowInEasing.transform(k))
                }
            }
            value = to
            i = (i + 1) % palettes.size
        }
    }
}
