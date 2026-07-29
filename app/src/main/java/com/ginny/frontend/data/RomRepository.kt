package com.ginny.frontend.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.RomOrganization
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Filename fragments that mark a Switch dump as an update/DLC rather than the base game. */
private val UPDATE_OR_DLC_MARKERS = listOf("update", "upd", "dlc", "patch", "[v")

/** Scans the user-selected ROM folder for valid ROM files, per system. */
class RomRepository(
    private val context: Context,
    private val assetRepository: AssetRepository,
) {

    /**
     * Lists every valid game inside `<romFolder>/<systemId.folderName>/`, sorted alphabetically,
     * with icons resolved from the User folder's Assets tree when available. The scan strategy
     * depends on [SystemDefinition.romOrganization] — flat ROM files for most systems, or one
     * subfolder per game (base + updates/DLC together) for Switch.
     * Returns an empty list if the ROM folder, the system subfolder, or the User folder are unset/missing.
     */
    suspend fun listGames(
        romFolderUri: Uri,
        userFolderUri: Uri?,
        systemId: SystemId,
    ): List<GameEntry> = withContext(Dispatchers.IO) {
        val definition = SystemCatalog.byId.getValue(systemId)
        val romRoot = SafUtils.treeDocument(context, romFolderUri) ?: return@withContext emptyList()
        val systemDir = SafUtils.findChild(romRoot, systemId.folderName)?.takeIf { it.isDirectory }
            ?: return@withContext emptyList()

        val iconIndex = userFolderUri?.let { assetRepository.loadIconIndex(it, systemId) } ?: emptyMap()

        when (definition.romOrganization) {
            RomOrganization.FLAT_FILES -> scanFlatFiles(systemDir, definition, iconIndex)
            RomOrganization.FOLDER_PER_GAME -> scanFolderPerGame(systemDir, definition, iconIndex)
        }
    }

    private fun scanFlatFiles(
        systemDir: DocumentFile,
        definition: SystemDefinition,
        iconIndex: Map<String, Uri>,
    ): List<GameEntry> =
        systemDir.listFiles()
            .filter { file ->
                file.isFile && file.name != null &&
                    file.name!!.substringAfterLast('.', "").lowercase() in definition.romExtensions
            }
            .map { file ->
                val romFileName = file.name!!
                val displayName = romFileName.substringBeforeLast('.')
                val iconKey = assetRepository.expectedIconFileName(romFileName).lowercase()
                GameEntry(
                    systemId = definition.id,
                    romUri = file.uri,
                    romFileName = romFileName,
                    displayName = displayName,
                    iconUri = iconIndex[iconKey],
                )
            }
            .sortedBy { it.displayName.lowercase() }

    /**
     * Each subfolder is one game (base + updates/DLC bundled together, same convention Eden and
     * other desktop Switch emulators already recognize). The subfolder name is the display name
     * and the icon lookup key; [pickBaseGameFile] picks which file inside it actually gets
     * launched.
     */
    private fun scanFolderPerGame(
        systemDir: DocumentFile,
        definition: SystemDefinition,
        iconIndex: Map<String, Uri>,
    ): List<GameEntry> =
        systemDir.listFiles()
            .filter { it.isDirectory && it.name != null }
            .mapNotNull { folder ->
                val baseFile = pickBaseGameFile(folder, definition.romExtensions) ?: return@mapNotNull null
                val displayName = folder.name!!
                val iconKey = assetRepository.expectedIconFileName(displayName).lowercase()
                GameEntry(
                    systemId = definition.id,
                    romUri = baseFile.uri,
                    romFileName = displayName,
                    displayName = displayName,
                    iconUri = iconIndex[iconKey],
                )
            }
            .sortedBy { it.displayName.lowercase() }

    /**
     * Prefers a file whose name doesn't look like an update/DLC; if every candidate is
     * ambiguously named (or there's a tie), falls back to the largest file, which in practice is
     * almost always the base game rather than a patch.
     */
    private fun pickBaseGameFile(folder: DocumentFile, extensions: Set<String>): DocumentFile? {
        val candidates = folder.listFiles().filter { file ->
            file.isFile && file.name != null &&
                file.name!!.substringAfterLast('.', "").lowercase() in extensions
        }
        if (candidates.isEmpty()) return null

        val likelyBaseGame = candidates.filter { file ->
            val lower = file.name!!.lowercase()
            UPDATE_OR_DLC_MARKERS.none { marker -> lower.contains(marker) }
        }
        return (likelyBaseGame.ifEmpty { candidates }).maxByOrNull { it.length() }
    }
}
