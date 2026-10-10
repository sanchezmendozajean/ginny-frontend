package com.ginny.frontend.data

import android.content.Context
import android.net.Uri
import com.ginny.frontend.data.db.GinnyDatabase
import com.ginny.frontend.data.db.ScannedGameEntity
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SystemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Cached index of the ROM library, backed by Room. [RomRepository] does the (slow) SAF filesystem
 * walk only when the user explicitly triggers a scan from Settings; every other screen reads from
 * here instead, so browsing/navigation never re-touches the filesystem.
 */
class GameIndexRepository(context: Context) {

    private val dao = GinnyDatabase.getInstance(context).scannedGameDao()

    fun observeGames(systemId: SystemId): Flow<List<GameEntry>> =
        dao.observeGames(systemId.name).map { rows -> rows.map { it.toGameEntry() } }

    /** Cross-system listing, used by the Favoritos pseudo-system (see GamesScope.Favorites). */
    fun observeAllGames(): Flow<List<GameEntry>> =
        dao.observeAllGames().map { rows -> rows.map { it.toGameEntry() } }

    /** Game count per system, derived from the same cached table used to list games. */
    fun observeGameCounts(): Flow<Map<SystemId, Int>> =
        dao.observeGameCounts().map { rows ->
            rows.mapNotNull { row ->
                runCatching { SystemId.valueOf(row.systemId) }.getOrNull()?.let { it to row.count }
            }.toMap()
        }

    suspend fun replaceAll(games: List<GameEntry>) {
        dao.replaceAll(games.map { it.toEntity() })
    }

    /** Used by "Reescanear este sistema" — replaces only the cached rows for [systemId]. */
    suspend fun replaceForSystem(systemId: SystemId, games: List<GameEntry>) {
        dao.replaceForSystem(systemId.name, games.map { it.toEntity() })
    }

    /** Actualiza el ícono mostrado de [game] sin reescanear (lo usa el scrapper al guardar uno nuevo). */
    suspend fun updateIcon(game: GameEntry, iconUri: Uri?) {
        dao.updateIconUri(game.systemId.name, game.romUri.toString(), iconUri?.toString())
    }

    suspend fun deleteGame(game: GameEntry) {
        dao.deleteByRomUri(game.systemId.name, game.romUri.toString())
    }
}

private fun ScannedGameEntity.toGameEntry() = GameEntry(
    systemId = SystemId.valueOf(systemId),
    romUri = Uri.parse(romUri),
    romFileName = romFileName,
    displayName = displayName,
    iconUri = iconUri?.let(Uri::parse),
)

private fun GameEntry.toEntity() = ScannedGameEntity(
    systemId = systemId.name,
    romUri = romUri.toString(),
    romFileName = romFileName,
    displayName = displayName,
    iconUri = iconUri?.toString(),
)
