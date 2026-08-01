package com.ginny.frontend.data

import android.content.Context
import android.net.Uri
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SaveStateEntry
import com.ginny.frontend.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects RetroArch save-state thumbnails for a game inside the configured save-states folder
 * (Settings > "Ruta de estados guardados de RetroArch"). Requires the user to have enabled
 * `savestate_thumbnail_enable` in RetroArch — see README. Standalone emulators (Dolphin, PPSSPP,
 * AetherSX2) are out of scope for v1 (each uses its own opaque save format).
 */
class SaveStateRepository(private val context: Context) {

    /** Empty when saves aren't configured/found — callers should skip the save-states screen entirely. */
    suspend fun listSaveStates(savesFolderUri: Uri?, game: GameEntry): List<SaveStateEntry.Slot> =
        withContext(Dispatchers.IO) {
            if (savesFolderUri == null) return@withContext emptyList()
            val savesDir = SafUtils.treeDocument(context, savesFolderUri) ?: return@withContext emptyList()

            val candidateBaseNames = setOf(
                game.romFileName,
                game.romFileName.substringBeforeLast('.'),
            ).map { Regex.escape(it) }

            val pattern = Regex(
                "^(?:${candidateBaseNames.joinToString("|")})\\.state(\\d*)\\.png$",
                RegexOption.IGNORE_CASE,
            )

            savesDir.listFiles()
                .filter { it.isFile && it.name != null }
                .mapNotNull { file ->
                    val match = pattern.matchEntire(file.name!!) ?: return@mapNotNull null
                    val slotIndex = match.groupValues[1].toIntOrNull() ?: 0
                    SaveStateEntry.Slot(
                        slotIndex = slotIndex,
                        label = if (slotIndex == 0) "Auto" else "Slot $slotIndex",
                        thumbnailUri = file.uri,
                    )
                }
                .sortedBy { it.slotIndex }
        }
}
