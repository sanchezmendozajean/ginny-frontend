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
    EDEN("Eden", "dev.eden.eden_emulator"),
    // WiiU, via Cemu's Android port (github.com/SSimco/Cemu and forks — still "early and
    // experimental" upstream, no Play Store release). Confirmed via `adb shell pm list packages`
    // against a real install (`info.cemu.cemu`, versionName 0.5) — same as every other package
    // name in this enum, not a guess. supportsPackageOverride is still on for this system (see
    // SystemCatalog.WII_U) because the Android port ecosystem is young and forked, same reasoning
    // as Switch/EDEN.
    CEMU("Cemu", "info.cemu.cemu"),
    // Confirmed via `adb shell pm list packages` against a real install.
    MELONDS("melonDS DS", "me.magnum.melonds"),
    // Azahar merged the former Lime3DS project and kept its Android applicationId — confirmed via
    // `adb shell pm list packages` against a real install (the app shows up as "AzaharPlus", the
    // "Replace" build flavor of https://github.com/AzaharPlus/AzaharPlus, which intentionally reuses
    // this id so frontends built against Azahar keep working; official Play Store listing "Azahar
    // Emulator" also ships under this id).
    AZAHAR("Azahar", "io.github.lime3ds.android");

    companion object {
        /** Every RetroArch build variant — the three are separate installable apps, but share one
         * [com.ginny.frontend.launch.LauncherStrategy] and one core/config resolution path. */
        val RETROARCH_VARIANTS: Set<EmulatorTarget> = setOf(RETROARCH, RETROARCH_AARCH64, RETROARCH_RA32)
    }
}
