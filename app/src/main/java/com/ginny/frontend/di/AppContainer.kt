package com.ginny.frontend.di

import android.content.Context
import com.ginny.frontend.data.AssetRepository
import com.ginny.frontend.data.MusicRepository
import com.ginny.frontend.data.RomRepository
import com.ginny.frontend.data.SaveStateRepository
import com.ginny.frontend.data.SettingsRepository
import com.ginny.frontend.data.StorageBootstrapper
import com.ginny.frontend.launch.GameLauncher

/** Minimal hand-rolled DI container — the app is small enough that Hilt would be pure ceremony. */
class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    val settingsRepository = SettingsRepository(context)
    val assetRepository = AssetRepository(context)
    val romRepository = RomRepository(context, assetRepository)
    val saveStateRepository = SaveStateRepository(context)
    val storageBootstrapper = StorageBootstrapper(context)
    val musicRepository = MusicRepository(context)
    val gameLauncher = GameLauncher(settingsRepository)
}
