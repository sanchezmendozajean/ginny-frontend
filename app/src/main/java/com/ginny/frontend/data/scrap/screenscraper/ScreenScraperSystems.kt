package com.ginny.frontend.data.scrap.screenscraper

import com.ginny.frontend.data.model.SystemId

/**
 * Identificador de cada sistema de Ginny en ScreenScraper (`systemeid`). Valores tomados de la lista
 * oficial (`systemesListe.php`), no de memoria. Una prueba verifica que todo [SystemId] tenga entrada.
 *
 * Elecciones donde ScreenScraper tiene más de un sistema parecido:
 * - FBNEO y MAME → 75 "Mame" (agrupa arcade, FBA y MAME).
 * - NEOGEO → 142 "Neo-Geo" (consola y arcade); existe 68 "Neo-Geo MVS" y 70 "Neo-Geo CD".
 * - NAOMI → 56; los juegos en GD-ROM están en 227 "Naomi GD-ROM" y Naomi 2 en 230.
 * - PCENGINE → 31; PC Engine CD-Rom es 114 y SuperGrafx 105.
 * - GENESIS → 1 "Megadrive" (incluye Genesis); el 32X es 19.
 */
object ScreenScraperSystems {

    private val ids: Map<SystemId, Int> = mapOf(
        SystemId.NES to 3,
        SystemId.SNES to 4,
        SystemId.GB to 9,
        SystemId.GBC to 10,
        SystemId.GBA to 12,
        SystemId.PS1 to 57,
        SystemId.GENESIS to 1,
        SystemId.MASTERSYSTEM to 2,
        SystemId.NEOGEO to 142,
        SystemId.FBNEO to 75,
        SystemId.MAME to 75,
        SystemId.GC to 13,
        SystemId.PS2 to 58,
        SystemId.PSP to 61,
        SystemId.DREAMCAST to 23,
        SystemId.SWITCH to 225,
        SystemId.PICO8 to 234,
        SystemId.ATOMISWAVE to 53,
        SystemId.NGP to 25,
        SystemId.NGPC to 82,
        SystemId.NAOMI to 56,
        SystemId.PCENGINE to 31,
        SystemId.N64 to 14,
        SystemId.GAMEGEAR to 21,
        SystemId.WII to 16,
        SystemId.WIIU to 18,
        SystemId.NDS to 15,
        SystemId.N3DS to 17,
    )

    /** `systemeid` de ScreenScraper para [systemId], o null si no hay equivalente. */
    fun idFor(systemId: SystemId): Int? = ids[systemId]

    /** Extensiones (en minúsculas, sin punto) que ScreenScraper trata como imagen de disco: `romtype=iso`. */
    private val discExtensions = setOf(
        "iso", "cue", "chd", "gdi", "cdi", "rvz", "wbfs", "ciso", "gcm", "gcz", "wia", "mdf", "nrg", "cso",
    )

    /**
     * Valor de `romtype` para un archivo: `dossier` si el juego es una carpeta (Switch), `iso` si es una
     * imagen de disco, `rom` en cualquier otro caso. Los tres valores salen del mensaje de error de la
     * API ("Rom/Iso/Dossier non trouvée"); la lista de extensiones de disco es una aproximación.
     */
    fun romTypeFor(isFolder: Boolean, fileName: String): String = when {
        isFolder -> "dossier"
        fileName.substringAfterLast('.', "").lowercase() in discExtensions -> "iso"
        else -> "rom"
    }
}
