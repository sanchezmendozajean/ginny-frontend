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
            excludedFileNames = setOf("neogeo.zip"),
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
            excludedFileNames = setOf("neogeo.zip"),
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
            excludedFileNames = setOf("neogeo.zip"),
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
        SystemDefinition(
            id = SystemId.PICO8,
            displayName = "PICO-8",
            // ".p8" es el cart de texto plano (código Lua, sin carátula embebida); ".p8.png" es el
            // cart "visual" — un PNG real (normalmente un screenshot/label del juego) con el código
            // y los datos esteganografiados en los bits menos significativos de los píxeles. Un
            // decoder de PNG normal (Coil incluido) simplemente ve la imagen visible, sin necesidad
            // de decodificar nada oculto.
            romExtensions = setOf("p8", "p8.png"),
            // El propio archivo ".p8.png" ya es una imagen válida — se usa como ícono de fallback
            // (ver RomRepository.scanFlatFiles) cuando no hay uno propio en Assets/pico8/icons/.
            embeddedIconExtensions = setOf("p8.png"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                // fake-08 (https://github.com/jtothebell/fake-08): reimplementación open-source
                // compatible con PICO-8, no el runtime oficial de Lexaloffle — es el único core
                // libretro de PICO-8 distribuido en el buildbot oficial.
                RetroArchCore("fake08", "fake-08", "fake08_libretro_android.so"),
                // Retro8 (https://github.com/Jakz/retro8): otra reimplementación open-source
                // compatible con PICO-8, también distribuida en el buildbot oficial de libretro.
                RetroArchCore("retro8", "Retro8", "retro8_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "fake08",
        ),
        SystemDefinition(
            id = SystemId.ATOMISWAVE,
            displayName = "Atomiswave",
            romExtensions = setOf("zip", "chd"),
            // A diferencia de neogeo.zip (MAME/FBNeo/NeoGeo), la BIOS de Atomiswave (`awbios.zip`)
            // no vive junto a los ROMs: Flycast la busca en la carpeta `system/dc/` propia de
            // RetroArch, así que acá no hace falta excludedFileNames.
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("flycast", "Flycast", "flycast_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "flycast",
        ),
        SystemDefinition(
            id = SystemId.NGP,
            displayName = "Neo Geo Pocket",
            romExtensions = setOf("ngp", "npc", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                // Beetle NeoPop: mismo core soporta NGP y NGPC (ver SystemId.NGPC más abajo).
                RetroArchCore("mednafen_ngp", "Beetle NeoPop", "mednafen_ngp_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "mednafen_ngp",
        ),
        SystemDefinition(
            id = SystemId.NGPC,
            displayName = "Neo Geo Pocket Color",
            romExtensions = setOf("ngc", "npc", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("mednafen_ngp", "Beetle NeoPop", "mednafen_ngp_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "mednafen_ngp",
        ),
        SystemDefinition(
            id = SystemId.NAOMI,
            displayName = "Naomi",
            romExtensions = setOf("zip", "chd"),
            // Misma nota que Atomiswave: la BIOS (naomi.zip / naomi_boot.bin) va en `system/dc/` de
            // RetroArch, no junto a los ROMs.
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("flycast", "Flycast", "flycast_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "flycast",
        ),
        SystemDefinition(
            id = SystemId.PCENGINE,
            displayName = "PC Engine / TurboGrafx-16",
            romExtensions = setOf("pce", "cue", "ccd", "chd", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                RetroArchCore("mednafen_pce_fast", "Beetle PCE Fast", "mednafen_pce_fast_libretro_android.so"),
                RetroArchCore("mednafen_pce", "Beetle PCE", "mednafen_pce_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "mednafen_pce_fast",
        ),
        SystemDefinition(
            id = SystemId.N64,
            displayName = "Nintendo 64",
            romExtensions = setOf("n64", "z64", "v64", "zip"),
            availableEmulators = listOf(
                EmulatorTarget.RETROARCH,
                EmulatorTarget.RETROARCH_AARCH64,
                EmulatorTarget.RETROARCH_RA32,
            ),
            defaultEmulator = EmulatorTarget.RETROARCH,
            retroArchCores = retroArch(
                // Mupen64Plus-Next no se distribuye como un único .so — el buildbot de libretro
                // solo publica variantes separadas por versión de GLES (verificado contra el
                // listado real de buildbot.libretro.com/nightly/android/latest/arm64-v8a/, no hay
                // "mupen64plus_next_libretro_android.so" a secas). GLES3 como default: cobertura de
                // hardware Android moderno amplia y menos bugs gráficos que GLES2.
                RetroArchCore("mupen64plus_next_gles3", "Mupen64Plus-Next (GLES3)", "mupen64plus_next_gles3_libretro_android.so"),
                RetroArchCore("mupen64plus_next_gles2", "Mupen64Plus-Next (GLES2)", "mupen64plus_next_gles2_libretro_android.so"),
                RetroArchCore("parallel_n64", "ParaLLEl N64", "parallel_n64_libretro_android.so"),
            ),
            defaultRetroArchCoreId = "mupen64plus_next_gles3",
        ),
        SystemDefinition(
            id = SystemId.GAMEGEAR,
            displayName = "Sega Game Gear",
            romExtensions = setOf("gg", "zip"),
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
            id = SystemId.WII,
            displayName = "Wii",
            // Mismo emulador que GameCube (SystemId.GC) — Dolphin corre ambos. wbfs y ciso son
            // formatos de backup comprimido específicos de Wii (a diferencia de GameCube, que no
            // los usa); wad es el formato de instalación de canales/VC de la Wii, no un disco.
            romExtensions = setOf("iso", "wbfs", "rvz", "ciso", "wad"),
            availableEmulators = listOf(EmulatorTarget.DOLPHIN),
            defaultEmulator = EmulatorTarget.DOLPHIN,
        ),
        SystemDefinition(
            id = SystemId.WIIU,
            displayName = "Wii U",
            // .wud/.wux: dump completo de disco de un solo archivo, mismo criterio que GC/Wii (a
            // diferencia de un dump "suelto" tipo carpeta code/content/meta, que no encaja con el
            // envío de un solo Uri por ACTION_VIEW que usa GenericViewLauncher). .wua ("Wii U
            // Archive"): formato de archivo único propio de Cemu, generado desde su Title Manager
            // ("Convert to compressed Wii U archive") — recomendado específicamente para el port de
            // Android por sobre wud/wux (empaqueta base+updates+DLC comprimidos en un solo archivo).
            romExtensions = setOf("wud", "wux", "wua"),
            availableEmulators = listOf(EmulatorTarget.CEMU),
            defaultEmulator = EmulatorTarget.CEMU,
            // El port de Cemu para Android sigue siendo joven/forkeado (ver EmulatorTarget.CEMU) —
            // mismo criterio que PS2/Switch: dejar el package name configurable por si el usuario
            // termina usando un fork distinto al confirmado acá.
            supportsPackageOverride = true,
        ),
    )

    val byId: Map<SystemId, SystemDefinition> = all.associateBy { it.id }

    /** Systems sorted alphabetically by display name, as required for the systems grid. */
    val allSortedByName: List<SystemDefinition> = all.sortedBy { it.displayName.lowercase() }
}
