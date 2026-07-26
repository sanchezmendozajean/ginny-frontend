package com.ginny.frontend.data.model

/** External emulator app a system can be delegated to. Each maps to one [com.ginny.frontend.launch.LauncherStrategy]. */
enum class EmulatorTarget(val displayName: String, val packageName: String) {
    // RetroArch ships as three separate, mutually-installable Android builds distributed from
    // buildbot.libretro.com/.../android/ (RetroArch.apk, RetroArch_aarch64.apk, RetroArch_ra32.apk).
    // Their applicationId/flavor names are confirmed straight from the official build config —
    // pkg/android/phoenix/build.gradle in libretro/RetroArch — not a guess:
    //   flavor "normal"/"playStoreNormal" -> com.retroarch          ("RetroArch")
    //   flavor "aarch64"/"playStorePlus"  -> com.retroarch.aarch64  ("RetroArch (AArch64)" / Play Store "RetroArch Plus")
    //   flavor "ra32"                     -> com.retroarch.ra32     ("RetroArch (32-bit)")
    RETROARCH("RetroArch", "com.retroarch"),
    RETROARCH_AARCH64("RetroArch (64-bit)", "com.retroarch.aarch64"),
    RETROARCH_RA32("RetroArch (32-bit)", "com.retroarch.ra32"),
    DOLPHIN("Dolphin", "org.dolphinemu.dolphinemu"),
    PPSSPP("PPSSPP", "org.ppsspp.ppsspp"),
    // Confirmed via `adb shell pm list packages` against a real install.
    AETHERSX2("AetherSX2", "xyz.aethersx2.android"),
    // NetherSX2 ("NetherSX2 Classic" v2.2n-3668) ships as a drop-in replacement for AetherSX2 and
    // reuses its exact applicationId — confirmed via `aapt dump badging` on the APK, not a guess.
    // Because of that, the two can never be installed side by side (Android rejects the install as
    // "conflicts with an existing package" unless the other is uninstalled first).
    NETHERSX2("NetherSX2", "xyz.aethersx2.android"),
    REDREAM("Redream", "io.recompiled.redream"),
    // Confirmed via `adb shell pm list packages` against a real install.
    EDEN("Eden", "dev.eden.eden_emulator");

    companion object {
        /** Every RetroArch build variant — the three are separate installable apps, but share one
         * [com.ginny.frontend.launch.LauncherStrategy] and one core/config resolution path. */
        val RETROARCH_VARIANTS: Set<EmulatorTarget> = setOf(RETROARCH, RETROARCH_AARCH64, RETROARCH_RA32)
    }
}
