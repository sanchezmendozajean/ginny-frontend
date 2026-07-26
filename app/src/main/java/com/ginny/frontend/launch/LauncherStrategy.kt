package com.ginny.frontend.launch

import android.content.Context
import android.content.Intent
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.GameEntry

/** What to launch a game with: the game itself, the RetroArch core (if applicable) and an optional save slot. */
data class LaunchRequest(
    val game: GameEntry,
    val retroArchCoreSoFileName: String? = null,
    val saveStateSlot: Int? = null,
)

sealed interface LaunchResult {
    data object Launched : LaunchResult
    data class EmulatorNotInstalled(val emulator: EmulatorTarget, val storeUrl: String?) : LaunchResult
    data class Failed(val message: String) : LaunchResult
}

/**
 * One implementation per external emulator app. Each system delegates emulation entirely to the
 * matching strategy via an explicit Intent — this app never runs emulation itself.
 */
interface LauncherStrategy {
    val emulator: EmulatorTarget

    fun isInstalled(context: Context, packageNameOverride: String? = null): Boolean {
        val packageName = packageNameOverride ?: emulator.packageName
        return runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
    }

    fun buildIntent(context: Context, request: LaunchRequest, packageNameOverride: String? = null): Intent

    fun launch(context: Context, request: LaunchRequest, packageNameOverride: String? = null): LaunchResult {
        if (!isInstalled(context, packageNameOverride)) {
            return LaunchResult.EmulatorNotInstalled(emulator, storeUrl())
        }
        return runCatching {
            context.startActivity(buildIntent(context, request, packageNameOverride))
            LaunchResult.Launched
        }.getOrElse { e ->
            LaunchResult.Failed(e.message ?: "No se pudo iniciar ${emulator.displayName}")
        }
    }

    /** Google Play Store deep link is intentionally not used (no Play Services dependency) — this
     * points to the emulator's own download page when known, so the "missing emulator" dialog can
     * offer a link. Null when there's no stable canonical page to point to. */
    fun storeUrl(): String?
}
