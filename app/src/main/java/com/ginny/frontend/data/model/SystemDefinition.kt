package com.ginny.frontend.data.model

/** Static catalog entry describing everything needed to scan, display and launch a system. */
data class SystemDefinition(
    val id: SystemId,
    val displayName: String,
    /** Valid ROM file extensions, lowercase, without the leading dot. */
    val romExtensions: Set<String>,
    /** Emulators the user may pick between for this system. Single entry unless [allowsEmulatorChoice]. */
    val availableEmulators: List<EmulatorTarget>,
    val defaultEmulator: EmulatorTarget,
    /** Cores offered when the active emulator for this system is RetroArch. Empty for non-RetroArch systems. */
    val retroArchCores: List<RetroArchCore> = emptyList(),
    val defaultRetroArchCoreId: String? = null,
    val romOrganization: RomOrganization = RomOrganization.FLAT_FILES,
    /** Whether Settings should offer a manual package-name override for this system — for
     * ecosystems with more than one real-world app/fork and no single reliable default (PS2,
     * Switch), even when one of the options happens to already have a confirmed default. */
    val supportsPackageOverride: Boolean = false,
    /** Filenames (lowercase, with extension) to skip during scanning even though they match
     * [romExtensions] — e.g. shared BIOS/parent romsets like `neogeo.zip` in MAME/FBNeo, which
     * aren't themselves playable games. */
    val excludedFileNames: Set<String> = emptySet(),
    /** ROM extensions (subset of [romExtensions], lowercase, without the leading dot — may contain
     * a dot themselves, e.g. `"p8.png"`) whose file is *itself* a valid image and doubles as the
     * game's icon when no custom one exists in `Assets/<sistema>/icons/` — e.g. PICO-8's `.p8.png`
     * cartridge format embeds its label image directly in the same PNG as the game data. */
    val embeddedIconExtensions: Set<String> = emptySet(),
) {
    val allowsEmulatorChoice: Boolean get() = availableEmulators.size > 1
}
