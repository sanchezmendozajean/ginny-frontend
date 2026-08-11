package com.ginny.frontend.launch

import android.content.Context
import android.content.Intent
import com.ginny.frontend.data.model.EmulatorTarget

/**
 * Generic "send the ROM Uri via ACTION_VIEW to a specific package" launcher, used for standalone
 * emulators whose exact internal intent-extras scheme isn't stable/confirmed (Dolphin, PPSSPP,
 * AetherSX2/NetherSX2, Redream). This is the most portable option: it doesn't depend on guessing
 * an internal Activity class name, only on the target app registering itself as a file viewer,
 * which all four do. Swap in a more specific [LauncherStrategy] per emulator if/when its exact
 * intent-extras contract is verified (see README).
 */
abstract class GenericViewLauncher(
    override val emulator: EmulatorTarget,
    private val storeUrlValue: String?,
) : LauncherStrategy {

    override fun buildIntent(context: Context, request: LaunchRequest, packageNameOverride: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage(packageNameOverride ?: emulator.packageName)
            setDataAndType(request.game.romUri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    override fun storeUrl(): String? = storeUrlValue
}

class DolphinLauncher : GenericViewLauncher(
    emulator = EmulatorTarget.DOLPHIN,
    storeUrlValue = "https://dolphin-emu.org/download/",
)

class PpssppLauncher : GenericViewLauncher(
    emulator = EmulatorTarget.PPSSPP,
    storeUrlValue = "https://www.ppsspp.org/download",
)

/**
 * PS2, original app. AetherSX2 was discontinued/delisted in 2024 but plenty of installs remain.
 * The package name is still configurable in Settings (PlayStation 2 section) in case a given
 * build/sideload uses a different applicationId than the one confirmed here.
 */
class AetherSX2Launcher : GenericViewLauncher(
    emulator = EmulatorTarget.AETHERSX2,
    storeUrlValue = null,
)

/**
 * PS2, community fork and practical successor to AetherSX2. No default package name is assumed
 * (see [EmulatorTarget.NETHERSX2]) — the "Package name" override in Settings is required for this
 * one until/unless a stable package name gets confirmed and hardcoded here.
 */
class NetherSX2Launcher : GenericViewLauncher(
    emulator = EmulatorTarget.NETHERSX2,
    storeUrlValue = null,
)

class RedreamLauncher : GenericViewLauncher(
    emulator = EmulatorTarget.REDREAM,
    storeUrlValue = "https://redream.io/download",
)

/**
 * Switch, via Eden. No Android package name is confirmed yet — this generic ACTION_VIEW launcher
 * works as long as the "Package override" in Settings is filled in and Eden registers as a file
 * viewer (true of every emulator this app already supports).
 */
class EdenLauncher : GenericViewLauncher(
    emulator = EmulatorTarget.EDEN,
    storeUrlValue = null,
)

/**
 * WiiU, via Cemu's Android port. Package name confirmed via adb against a real install, but the
 * port itself is still "early and experimental" upstream (see [EmulatorTarget.CEMU]) — the
 * "Package override" in Settings stays available in case a different fork/build is used.
 */
class CemuLauncher : GenericViewLauncher(
    emulator = EmulatorTarget.CEMU,
    storeUrlValue = "https://github.com/SSimco/Cemu",
)

class MelonDSLauncher : GenericViewLauncher(
    emulator = EmulatorTarget.MELONDS,
    storeUrlValue = "https://melonds-ds.github.io/",
)

/**
 * 3DS, via Azahar (see [EmulatorTarget.AZAHAR] re: the AzaharPlus/Lime3DS applicationId history).
 */
class AzaharLauncher : GenericViewLauncher(
    emulator = EmulatorTarget.AZAHAR,
    storeUrlValue = "https://azahar-emu.org/",
)
