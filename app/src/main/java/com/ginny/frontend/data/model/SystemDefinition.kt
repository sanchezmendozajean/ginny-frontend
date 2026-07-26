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
) {
    val allowsEmulatorChoice: Boolean get() = availableEmulators.size > 1
}
