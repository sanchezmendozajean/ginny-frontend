package com.ginny.frontend.launch

import com.ginny.frontend.data.model.EmulatorTarget

object LauncherFactory {
    private val strategies: Map<EmulatorTarget, LauncherStrategy> = mapOf(
        EmulatorTarget.RETROARCH to RetroArchLauncher(EmulatorTarget.RETROARCH),
        EmulatorTarget.RETROARCH_AARCH64 to RetroArchLauncher(EmulatorTarget.RETROARCH_AARCH64),
        EmulatorTarget.RETROARCH_RA32 to RetroArchLauncher(EmulatorTarget.RETROARCH_RA32),
        EmulatorTarget.DOLPHIN to DolphinLauncher(),
        EmulatorTarget.PPSSPP to PpssppLauncher(),
        EmulatorTarget.AETHERSX2 to AetherSX2Launcher(),
        EmulatorTarget.NETHERSX2 to NetherSX2Launcher(),
        EmulatorTarget.REDREAM to RedreamLauncher(),
        EmulatorTarget.EDEN to EdenLauncher(),
        EmulatorTarget.CEMU to CemuLauncher(),
        EmulatorTarget.MELONDS to MelonDSLauncher(),
        EmulatorTarget.AZAHAR to AzaharLauncher(),
    )

    fun forEmulator(emulator: EmulatorTarget): LauncherStrategy =
        strategies.getValue(emulator)
}
