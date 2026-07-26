package com.ginny.frontend.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ASSETS_DIR_NAME = "Assets"
private const val ICONS_DIR_NAME = "icons"
private const val SYSTEM_LOGO_FILE_NAME = "system.png"

/** Reads game icons and system logos from the user-selected "Carpeta de Usuario" (Assets/<system>/...). */
class AssetRepository(private val context: Context) {

    /** Assets/<systemFolder> directory for [systemId], or null if the User folder or subtree doesn't exist. */
    private fun systemAssetsDir(userTreeUri: Uri, systemId: SystemId): DocumentFile? {
        val root = SafUtils.treeDocument(context, userTreeUri) ?: return null
        val assets = SafUtils.findChild(root, ASSETS_DIR_NAME)?.takeIf { it.isDirectory } ?: return null
        return SafUtils.findChild(assets, systemId.folderName)?.takeIf { it.isDirectory }
    }

    /** Maps lowercase ROM file name (e.g. "bomberman.nes") -> icon Uri, for every icon present. */
    suspend fun loadIconIndex(userTreeUri: Uri, systemId: SystemId): Map<String, Uri> = withContext(Dispatchers.IO) {
        val systemDir = systemAssetsDir(userTreeUri, systemId) ?: return@withContext emptyMap()
        val iconsDir = SafUtils.findChild(systemDir, ICONS_DIR_NAME)?.takeIf { it.isDirectory }
            ?: return@withContext emptyMap()
        iconsDir.listFiles()
            .filter { it.isFile && it.name != null }
            .associate { it.name!!.lowercase() to it.uri }
    }

    suspend fun systemLogoUri(userTreeUri: Uri, systemId: SystemId): Uri? = withContext(Dispatchers.IO) {
        val systemDir = systemAssetsDir(userTreeUri, systemId) ?: return@withContext null
        SafUtils.findChildFileIgnoreCase(systemDir, SYSTEM_LOGO_FILE_NAME)?.uri
    }

    /** Expected icon file name for a given ROM file name, per the "<romFileName>.png" convention. */
    fun expectedIconFileName(romFileName: String): String = "$romFileName.png"
}
