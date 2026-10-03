package com.ginny.frontend.ui.util

import androidx.compose.runtime.staticCompositionLocalOf
import com.ginny.frontend.audio.SoundManager

/**
 * Los efectos de sonido (foco, activación) desde cualquier composable. Lo provee MainActivity,
 * dueña del [SoundManager] (que también maneja la música). null = sin sonido (ej. @Preview).
 * Uso: `LocalSoundManager.current?.playFocusSound()`.
 */
val LocalSoundManager = staticCompositionLocalOf<SoundManager?> { null }
