package com.ginny.frontend.data.db

import androidx.room.Entity

/** Cached result of a library scan for one game, keyed by system + ROM Uri. Repopulated wholesale
 * every time the user runs "Escanear biblioteca" in Settings — see [ScannedGameDao.replaceAll]. */
@Entity(tableName = "scanned_games", primaryKeys = ["systemId", "romUri"])
data class ScannedGameEntity(
    val systemId: String,
    val romUri: String,
    val romFileName: String,
    val displayName: String,
    val iconUri: String?,
)
