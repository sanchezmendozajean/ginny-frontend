package com.ginny.frontend.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class SystemGameCount(val systemId: String, val count: Int)

@Dao
interface ScannedGameDao {

    @Query("SELECT * FROM scanned_games WHERE systemId = :systemId ORDER BY displayName COLLATE NOCASE")
    fun observeGames(systemId: String): Flow<List<ScannedGameEntity>>

    /** Cross-system listing, used by the Favoritos pseudo-system (see GamesScope.Favorites). */
    @Query("SELECT * FROM scanned_games ORDER BY displayName COLLATE NOCASE")
    fun observeAllGames(): Flow<List<ScannedGameEntity>>

    @Query("SELECT systemId, COUNT(*) as count FROM scanned_games GROUP BY systemId")
    fun observeGameCounts(): Flow<List<SystemGameCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<ScannedGameEntity>)

    @Query("DELETE FROM scanned_games")
    suspend fun clearAll()

    @Query("DELETE FROM scanned_games WHERE systemId = :systemId")
    suspend fun clearForSystem(systemId: String)

    @Query("DELETE FROM scanned_games WHERE systemId = :systemId AND romUri = :romUri")
    suspend fun deleteByRomUri(systemId: String, romUri: String)

    /** Wholesale replace, used by the Settings "Escanear biblioteca" scan — the cache always
     * reflects the most recent full scan rather than being incrementally merged. */
    @Transaction
    suspend fun replaceAll(games: List<ScannedGameEntity>) {
        clearAll()
        insertAll(games)
    }

    /** Same as [replaceAll] but scoped to a single system, used by "Reescanear este sistema". */
    @Transaction
    suspend fun replaceForSystem(systemId: String, games: List<ScannedGameEntity>) {
        clearForSystem(systemId)
        insertAll(games)
    }
}
