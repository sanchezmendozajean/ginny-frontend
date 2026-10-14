package com.ginny.frontend.launch

import android.content.Context
import com.ginny.frontend.data.SettingsRepository
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SystemCatalog
import kotlinx.coroutines.flow.first

/** Use-case that resolves the user's configured emulator/core/package for a system and launches a game. */
class GameLauncher(private val settingsRepository: SettingsRepository) {

    suspend fun launch(context: Context, game: GameEntry): LaunchResult {
        val emulator = settingsRepository.emulatorFor(game.systemId).first()
        val strategy = LauncherFactory.forEmulator(emulator)

        val coreSoFileName = if (emulator in EmulatorTarget.RETROARCH_VARIANTS) {
            val definition = SystemCatalog.byId.getValue(game.systemId)
            val coreId = settingsRepository.retroArchCoreFor(game.systemId).first()
            definition.retroArchCores.firstOrNull { it.id == coreId }?.soFileName
                ?: definition.retroArchCores.firstOrNull { it.id == definition.defaultRetroArchCoreId }?.soFileName
        } else null

        val packageOverride = settingsRepository.packageOverrideFor(game.systemId).first()?.takeIf { it.isNotBlank() }

        val request = LaunchRequest(
            game = game,
            retroArchCoreSoFileName = coreSoFileName,
        )
        return strategy.launch(context, request, packageOverride)
    }
}
