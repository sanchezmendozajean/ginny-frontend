package com.ginny.frontend.data.model

/** How a system's ROM subfolder is laid out, for [com.ginny.frontend.data.RomRepository]. */
enum class RomOrganization {
    /** One valid ROM file directly inside the system folder — the convention every system but
     * Switch uses. */
    FLAT_FILES,

    /**
     * One subfolder per game, containing the base game plus its updates/DLC together (the
     * convention used by Switch dumps, and recognized the same way by desktop emulators like
     * Eden). The subfolder itself is the "game"; scanning picks the base-game file inside it to
     * actually launch.
     */
    FOLDER_PER_GAME,
}
