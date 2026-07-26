package com.ginny.frontend.data.model

import android.net.Uri

/** One entry in the save-states grid for a game: either the synthetic "New game" tile or a real slot. */
sealed interface SaveStateEntry {
    data object NewGame : SaveStateEntry

    data class Slot(
        /** RetroArch save-state slot index (0 = unnumbered/"Auto" slot depending on core convention). */
        val slotIndex: Int,
        val label: String,
        val thumbnailUri: Uri?,
    ) : SaveStateEntry
}
