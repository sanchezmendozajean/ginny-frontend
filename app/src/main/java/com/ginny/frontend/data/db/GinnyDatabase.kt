package com.ginny.frontend.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScannedGameEntity::class], version = 1, exportSchema = false)
abstract class GinnyDatabase : RoomDatabase() {

    abstract fun scannedGameDao(): ScannedGameDao

    companion object {
        @Volatile
        private var instance: GinnyDatabase? = null

        fun getInstance(context: Context): GinnyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GinnyDatabase::class.java,
                    "ginny_library.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
