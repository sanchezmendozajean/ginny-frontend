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
    /**
     * Uri of "<romFileName>.png" inside Assets/<system>/icons/ if present; otherwise, for systems
     * where the ROM itself is a valid image (see [SystemDefinition.embeddedIconExtensions] — e.g.
     * PICO-8's ".p8.png" carts), [romUri] again; otherwise null (placeholder is shown).
     */
    val iconUri: Uri?,
)

/** Composite key used to persist favorites (see [com.ginny.frontend.data.SettingsRepository.favoriteGameKeys]). */
fun GameEntry.favoriteKey(): String = "${systemId.name}|$romFileName"
