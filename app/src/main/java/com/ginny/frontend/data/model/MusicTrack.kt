package com.ginny.frontend.data.model

import android.net.Uri

/** The two bundled background-music tracks (see app/src/main/res/raw) offered in Settings
 * alongside anything the user drops into Interface/Background Music — see [MusicSelection]. */
enum class BundledMusicTrack(val id: String, val displayName: String) {
    MONDAY_MARIMBA("monday_marimba", "Monday Marimba"),
    CALM_BAY("calm_bay", "Calm Bay"),
}

/** What SoundManager should currently be looping: one of the two bundled tracks, or a user file
 * from Interface/Background Music inside the "Carpeta de Usuario" (see MusicRepository). */
sealed interface MusicSelection {
    data class Bundled(val track: BundledMusicTrack) : MusicSelection
    data class Custom(val uri: Uri, val displayName: String) : MusicSelection

    companion object {
        private const val BUNDLED_PREFIX = "bundled:"
        private const val CUSTOM_PREFIX = "custom:"

        fun encode(selection: MusicSelection): String = when (selection) {
            is Bundled -> "$BUNDLED_PREFIX${selection.track.id}"
            is Custom -> "$CUSTOM_PREFIX${selection.uri}"
        }

        /** Falls back to the first bundled track when [stored] is blank, names a bundled id that
         * no longer exists, or points at a custom file that's since been removed/renamed. */
        fun decode(stored: String?, customTracks: List<Custom>): MusicSelection {
            val fallback = Bundled(BundledMusicTrack.entries.first())
            val value = stored ?: return fallback
            return when {
                value.startsWith(BUNDLED_PREFIX) ->
                    BundledMusicTrack.entries.firstOrNull { it.id == value.removePrefix(BUNDLED_PREFIX) }
                        ?.let(::Bundled) ?: fallback
                value.startsWith(CUSTOM_PREFIX) -> {
                    val uriString = value.removePrefix(CUSTOM_PREFIX)
                    customTracks.firstOrNull { it.uri.toString() == uriString } ?: fallback
                }
                else -> fallback
            }
        }
    }
}
