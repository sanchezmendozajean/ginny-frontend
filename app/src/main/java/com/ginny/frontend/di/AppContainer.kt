package com.ginny.frontend.di

import android.content.Context
import android.net.Uri
import com.ginny.frontend.data.AssetRepository
import com.ginny.frontend.data.GameIndexRepository
import com.ginny.frontend.data.GinnyConfigRepository
import com.ginny.frontend.data.MusicRepository
import com.ginny.frontend.data.RomRepository
import com.ginny.frontend.data.SaveStateRepository
import com.ginny.frontend.data.SettingsRepository
import com.ginny.frontend.data.StorageBootstrapper
import com.ginny.frontend.launch.GameLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Minimal hand-rolled DI container — the app is small enough that Hilt would be pure ceremony. */
class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    val settingsRepository = SettingsRepository(context)
    val assetRepository = AssetRepository(context)
    val romRepository = RomRepository(context, assetRepository)
    val gameIndexRepository = GameIndexRepository(context)
    val saveStateRepository = SaveStateRepository(context)
    val storageBootstrapper = StorageBootstrapper(context)
    val musicRepository = MusicRepository(context)
    val gameLauncher = GameLauncher(settingsRepository)
    val configRepository = GinnyConfigRepository(context, settingsRepository)

    /** Lives as long as the process — backs [GinnyConfigRepository]'s debounced auto-sync and the
     * one-shot read of `ginny.cfg` on app start below. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        configRepository.startAutoSync(applicationScope)
        applicationScope.launch {
            settingsRepository.userFolderUri.first()?.let { uri ->
                configRepository.ensureConfigFile(Uri.parse(uri))
            }
        }
    }
}
