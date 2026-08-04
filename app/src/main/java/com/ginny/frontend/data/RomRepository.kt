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

/** Matches by filename suffix rather than "extension after the last dot" — needed for compound
 * extensions like PICO-8's `"p8.png"` (`substringAfterLast('.', "")` would only ever see `"png"`
 * for those). Behaves the same as the old last-dot comparison for single-part extensions. */
private fun matchesExtension(fileName: String, extensions: Set<String>): Boolean {
    val lower = fileName.lowercase()
    return extensions.any { lower.endsWith(".$it") }
}

/** Strips the ROM's matched extension from its file name for the fallback display name — plain
 * `substringBeforeLast('.')` would leave a stray ".p8" behind for compound extensions like PICO-8's
 * "p8.png" (picks the longest matching suffix in case more than one extension would match). */
private fun stripRomExtension(fileName: String, extensions: Set<String>): String {
    val lower = fileName.lowercase()
    val matched = extensions.filter { lower.endsWith(".$it") }.maxByOrNull { it.length }
    return if (matched != null) fileName.dropLast(matched.length + 1) else fileName.substringBeforeLast('.', fileName)
}

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
    /** Full library scan across every system, for the Settings "Escanear biblioteca" button — the
     * only place this expensive SAF walk should run; everywhere else reads the Room-backed cache
     * that this populates (see [com.ginny.frontend.data.GameIndexRepository]). */
    suspend fun scanAllSystems(romFolderUri: Uri, userFolderUri: Uri?): List<GameEntry> {
        val result = mutableListOf<GameEntry>()
        for (definition in SystemCatalog.all) {
            result += listGames(romFolderUri, userFolderUri, definition.id)
        }
        return result
    }

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
        val nameIndex = userFolderUri?.let { assetRepository.loadNameIndex(it, systemId) } ?: emptyMap()

        when (definition.romOrganization) {
            RomOrganization.FLAT_FILES -> scanFlatFiles(systemDir, definition, iconIndex, nameIndex)
            RomOrganization.FOLDER_PER_GAME -> scanFolderPerGame(systemDir, definition, iconIndex, nameIndex)
        }
    }

    private fun scanFlatFiles(
        systemDir: DocumentFile,
        definition: SystemDefinition,
        iconIndex: Map<String, Uri>,
        nameIndex: Map<String, String>,
    ): List<GameEntry> =
        systemDir.listFiles()
            .filter { file ->
                file.isFile && file.name != null &&
                    matchesExtension(file.name!!, definition.romExtensions) &&
                    file.name!!.lowercase() !in definition.excludedFileNames
            }
            .map { file ->
                val romFileName = file.name!!
                val displayName = nameIndex[romFileName.lowercase()]
                    ?: stripRomExtension(romFileName, definition.romExtensions)
                val iconKey = assetRepository.expectedIconFileName(romFileName).lowercase()
                // Si no hay un ícono propio en Assets/<sistema>/icons/, algunos sistemas (PICO-8:
                // los carts ".p8.png") ya traen una imagen válida embebida en el propio archivo de
                // ROM — se usa esa como fallback en vez del placeholder genérico.
                val embeddedIconUri = file.uri.takeIf { matchesExtension(romFileName, definition.embeddedIconExtensions) }
                GameEntry(
                    systemId = definition.id,
                    romUri = file.uri,
                    romFileName = romFileName,
                    displayName = displayName,
                    iconUri = iconIndex[iconKey] ?: embeddedIconUri,
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
        nameIndex: Map<String, String>,
    ): List<GameEntry> =
        systemDir.listFiles()
            .filter { it.isDirectory && it.name != null }
            .mapNotNull { folder ->
                val baseFile = pickBaseGameFile(folder, definition.romExtensions) ?: return@mapNotNull null
                val folderName = folder.name!!
                val displayName = nameIndex[folderName.lowercase()] ?: folderName
                val iconKey = assetRepository.expectedIconFileName(folderName).lowercase()
                GameEntry(
                    systemId = definition.id,
                    romUri = baseFile.uri,
                    romFileName = folderName,
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
            file.isFile && file.name != null && matchesExtension(file.name!!, extensions)
        }
        if (candidates.isEmpty()) return null

        val likelyBaseGame = candidates.filter { file ->
            val lower = file.name!!.lowercase()
            UPDATE_OR_DLC_MARKERS.none { marker -> lower.contains(marker) }
        }
        return (likelyBaseGame.ifEmpty { candidates }).maxByOrNull { it.length() }
    }
}
