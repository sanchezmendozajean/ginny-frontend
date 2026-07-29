package com.ginny.frontend.data.model

/**
 * Static, hardcoded catalog of every supported system: display name, valid ROM extensions,
 * emulator(s) it can be delegated to, and (for RetroArch systems) the curated list of cores.
 *
 * Extensions follow the proposal from docs/prompt-frontend-emuladores-3ds.md section
 * "Cosas a definir" #3 (not yet explicitly confirmed by the user at implementation time —
 * adjust here if the real answer differs).
 */
object SystemCatalog {

    private fun retroArch(vararg cores: RetroArchCore) = cores.toList()

    val all: List<SystemDefinition> = listOf(
        SystemDefinition(
            id = SystemId.NES,
            displayName = "Nintendo Entertainment System",
            romExtensions = setOf("nes", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("fceumm", "FCEUmm", "fceumm_libretro_android.so"),
                RetroArchCore("quicknes", "QuickNES", "quicknes_libretro_android.so"),
                RetroArchCore("nestopia", "Nestopia UE", "nestopia_libretro_android.so"),
                RetroArchCore("mesen", "Mesen", "mesen_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "fceumm",
        ),
        SystemDefinition(
            id = SystemId.SNES,
            displayName = "Super Nintendo",
            romExtensions = setOf("sfc", "smc", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("snes9x", "Snes9x", "snes9x_libretro_android.so"),
                RetroArchCore("bsnes", "bsnes", "bsnes_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "snes9x",
        ),
        SystemDefinition(
            id = SystemId.GB,
            displayName = "Game Boy",
            romExtensions = setOf("gb", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("gambatte", "Gambatte", "gambatte_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "gambatte",
        ),
        SystemDefinition(
            id = SystemId.GBC,
            displayName = "Game Boy Color",
            romExtensions = setOf("gbc", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("gambatte", "Gambatte", "gambatte_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "gambatte",
        ),
        SystemDefinition(
            id = SystemId.GBA,
            displayName = "Game Boy Advance",
            romExtensions = setOf("gba", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("mgba", "mGBA", "mgba_libretro_android.so"),
                RetroArchCore("vba_next", "VBA Next", "vba_next_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "mgba",
        ),
        SystemDefinition(
            id = SystemId.PS1,
            displayName = "PlayStation",
            romExtensions = setOf("iso", "bin", "cue", "chd", "pbp", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("pcsx_rearmed", "PCSX ReARMed", "pcsx_rearmed_libretro_android.so"),
                RetroArchCore("swanstation", "SwanStation", "swanstation_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "pcsx_rearmed",
        ),
        SystemDefinition(
            id = SystemId.GENESIS,
            displayName = "Sega Genesis / Mega Drive",
            romExtensions = setOf("md", "gen", "bin", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("genesis_plus_gx", "Genesis Plus GX", "genesis_plus_gx_libretro_android.so"),
                RetroArchCore("picodrive", "PicoDrive", "picodrive_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "genesis_plus_gx",
        ),
        SystemDefinition(
            id = SystemId.MASTERSYSTEM,
            displayName = "Sega Master System",
            romExtensions = setOf("sms", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("genesis_plus_gx", "Genesis Plus GX", "genesis_plus_gx_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "genesis_plus_gx",
        ),
        SystemDefinition(
            id = SystemId.NEOGEO,
            displayName = "Neo Geo",
            romExtensions = setOf("zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("fbneo", "FinalBurn Neo", "fbneo_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "fbneo",
        ),
        SystemDefinition(
            id = SystemId.FBNEO,
            displayName = "FinalBurn Neo",
            romExtensions = setOf("zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("fbneo", "FinalBurn Neo", "fbneo_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "fbneo",
        ),
        SystemDefinition(
            id = SystemId.MAME,
            displayName = "MAME",
            romExtensions = setOf("zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("mame2010", "MAME 2010 (romset ~0.139)", "mame2010_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "mame2010",
        ),
        SystemDefinition(
            id = SystemId.GC,
            displayName = "GameCube",
            romExtensions = setOf("iso", "gcm", "rvz"),
            availableEmulators = listOf(EmulatorTarget.DOLPHIN),
            defaultEmulator = EmulatorTarget.DOLPHIN,
        ),
        SystemDefinition(
            id = SystemId.PS2,
            displayName = "PlayStation 2",
            romExtensions = setOf("iso", "chd", "bin"),
            availableEmulators = listOf(EmulatorTarget.AETHERSX2, EmulatorTarget.NETHERSX2),
            defaultEmulator = EmulatorTarget.AETHERSX2,
            supportsPackageOverride = true,
        ),
        SystemDefinition(
            id = SystemId.PSP,
            displayName = "PSP",
            romExtensions = setOf("iso", "cso", "pbp"),
            availableEmulators = listOf(EmulatorTarget.PPSSPP),
            defaultEmulator = EmulatorTarget.PPSSPP,
        ),
        SystemDefinition(
            id = SystemId.DREAMCAST,
            displayName = "Dreamcast",
            romExtensions = setOf("chd", "gdi", "cdi", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.REDREAM,
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.REDREAM,
            retroArchCores = retroArch(
                RetroArchCore("flycast", "Flycast", "flycast_libretro_android.so"),
                RetroArchCore("reicast", "Reicast", "reicast_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "flycast",
        ),
        SystemDefinition(
            id = SystemId.SWITCH,
            displayName = "Nintendo Switch",
            // Loose file extensions, checked against whatever's inside each game's own subfolder
            // — see RomOrganization.FOLDER_PER_GAME and RomRepository's base-file heuristic.
            romExtensions = setOf("nsp", "xci", "nsz", "xcz"),
            availableEmulators = listOf(EmulatorTarget.EDEN),
            defaultEmulator = EmulatorTarget.EDEN,
            romOrganization = RomOrganization.FOLDER_PER_GAME,
            supportsPackageOverride = true,
        ),
    )

    val byId: Map<SystemId, SystemDefinition> = all.associateBy { it.id }

    /** Systems sorted alphabetically by display name, as required for the systems grid. */
    val allSortedByName: List<SystemDefinition> = all.sortedBy { it.displayName.lowercase() }
}
