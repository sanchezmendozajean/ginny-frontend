package com.ginny.frontend.data

import android.content.Context
import android.net.Uri
import com.ginny.frontend.data.model.MusicSelection
import com.ginny.frontend.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val INTERFACE_DIR_NAME = "Interface"
private const val BACKGROUND_MUSIC_DIR_NAME = "Background Music"
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "flac")

/** Reads user-supplied background-music tracks from `Interface/Background Music` inside the
 * user-selected "Carpeta de Usuario" — listed in Settings alongside the two bundled tracks. */
class MusicRepository(private val context: Context) {

    suspend fun listCustomTracks(userTreeUri: Uri): List<MusicSelection.Custom> = withContext(Dispatchers.IO) {
        val root = SafUtils.treeDocument(context, userTreeUri) ?: return@withContext emptyList()
        val interfaceDir = SafUtils.findChild(root, INTERFACE_DIR_NAME)?.takeIf { it.isDirectory }
            ?: return@withContext emptyList()
        val musicDir = SafUtils.findChild(interfaceDir, BACKGROUND_MUSIC_DIR_NAME)?.takeIf { it.isDirectory }
            ?: return@withContext emptyList()
        musicDir.listFiles()
            .filter { it.isFile && it.name != null && it.name!!.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
            .map { MusicSelection.Custom(it.uri, it.name!!.substringBeforeLast('.')) }
            .sortedBy { it.displayName.lowercase() }
    }
}
