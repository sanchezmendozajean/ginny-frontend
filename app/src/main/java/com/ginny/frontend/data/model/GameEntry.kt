package com.ginny.frontend.data.model

import android.net.Uri

/** A single ROM discovered inside a system's ROM subfolder. */
data class GameEntry(
    val systemId: SystemId,
    /** SAF content:// Uri of the ROM file itself, used both for launching and as a stable identity key. */
    val romUri: Uri,
    /** Full file name including extension, e.g. "BomberMan.nes" — also the key used to look up the icon. */
    val romFileName: String,
    /**
     * Name shown in the UI and used for sorting: the override from `Assets/<system>/names.json`
     * when [romFileName] (or, for folder-per-game systems, the folder name) has an entry there,
     * otherwise the file name with the extension stripped (or the folder name as-is).
     */
    val displayName: String,
    /** Uri of "<romFileName>.png" inside Assets/<system>/icons/, or null if not found (placeholder is shown). */
    val iconUri: Uri?,
)
