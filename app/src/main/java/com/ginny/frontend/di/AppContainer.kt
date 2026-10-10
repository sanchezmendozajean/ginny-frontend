package com.ginny.frontend.di

import android.content.Context
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ginny.frontend.data.AssetRepository
import com.ginny.frontend.data.GameIndexRepository
import com.ginny.frontend.data.GinnyConfigRepository
import com.ginny.frontend.data.MusicRepository
import com.ginny.frontend.data.RomRepository
import com.ginny.frontend.data.SaveStateRepository
import com.ginny.frontend.data.SettingsRepository
import com.ginny.frontend.data.StorageBootstrapper
import com.ginny.frontend.data.scrap.ScrapNetwork
import com.ginny.frontend.data.scrap.ScrapRepository
import com.ginny.frontend.data.scrap.ScrapRunner
import com.ginny.frontend.data.scrap.ScrapStorage
import com.ginny.frontend.data.scrap.ScrapperSettingsRepository
import com.ginny.frontend.data.scrap.screenscraper.ScreenScraperClient
import com.ginny.frontend.data.scrap.steamgriddb.SteamGridDbClient
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

    /** Credenciales y opciones del scrapper. Deliberadamente fuera de [configRepository] (ginny.cfg). */
    val scrapperSettingsRepository = ScrapperSettingsRepository(context)
    val scrapStorage = ScrapStorage(context, settingsRepository)
    val screenScraperClient = ScreenScraperClient(scrapperSettingsRepository)
    val steamGridDbClient = SteamGridDbClient(scrapperSettingsRepository)
    private val scrapNetwork = ScrapNetwork(context)
    private val scrapRepository = ScrapRepository(
        context, scrapperSettingsRepository, gameIndexRepository, scrapStorage,
        screenScraperClient, steamGridDbClient, scrapNetwork,
    )

    /** Lives as long as the process — backs [GinnyConfigRepository]'s debounced auto-sync and the
     * one-shot read of `ginny.cfg` on app start below, and runs scrapper batches (see [scrapRunner]). */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Lotes de scrapeo: viven en el scope de la aplicación, no en una pantalla. */
    val scrapRunner = ScrapRunner(scrapRepository, applicationScope)

    init {
        configRepository.startAutoSync(applicationScope)
        applicationScope.launch {
            settingsRepository.userFolderUri.first()?.let { uri ->
                configRepository.ensureConfigFile(Uri.parse(uri))
            }
        }
        // Flush ginny.cfg immediately whenever the app leaves the foreground, instead of relying
        // on the debounced writer: a device shutdown/reboot kills this process with no guarantee
        // the debounce fired, which otherwise lets a stale ginny.cfg revert the last-changed
        // setting on the next launch.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                configRepository.flushNow(applicationScope)
            }
        })
    }
}
