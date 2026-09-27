package com.ginny.frontend.ui.theme

import com.ginny.frontend.data.model.SystemId

/**
 * Curated short code shown on a system's tile when it has no custom logo configured
 * (`Assets/<sistema>/system.png` missing in the Carpeta de Usuario — see
 * `app/src/main/assets/system-logos/README.txt`). Deliberately NOT a naive
 * `displayName.take(4)`: several display names collide on their first 4 letters ("Game Boy" /
 * "Game Boy Advance" / "Game Boy Color" all start with "GAME"; "Nintendo 64" / "Nintendo DS" /
 * "Nintendo Switch" all start with "NINT"), which made same-console-family tiles indistinguishable
 * at a glance. These match the abbreviations retro-gaming communities already use.
 */
val SystemId.shortCode: String
    get() = when (this) {
        SystemId.NES -> "NES"
        SystemId.SNES -> "SNES"
        SystemId.GB -> "GB"
        SystemId.GBC -> "GBC"
        SystemId.GBA -> "GBA"
        SystemId.PS1 -> "PS1"
        SystemId.GENESIS -> "MD"
        SystemId.MASTERSYSTEM -> "SMS"
        SystemId.NEOGEO -> "NEO"
        SystemId.FBNEO -> "FBN"
        SystemId.MAME -> "MAME"
        SystemId.GC -> "GC"
        SystemId.PS2 -> "PS2"
        SystemId.PSP -> "PSP"
        SystemId.DREAMCAST -> "DC"
        SystemId.SWITCH -> "NSW"
        SystemId.PICO8 -> "P8"
        SystemId.ATOMISWAVE -> "AW"
        SystemId.NGP -> "NGP"
        SystemId.NGPC -> "NGPC"
        SystemId.NAOMI -> "NAOMI"
        SystemId.PCENGINE -> "PCE"
        SystemId.N64 -> "N64"
        SystemId.GAMEGEAR -> "GG"
        SystemId.WII -> "WII"
        SystemId.WIIU -> "WIIU"
        SystemId.NDS -> "NDS"
        SystemId.N3DS -> "3DS"
    }
