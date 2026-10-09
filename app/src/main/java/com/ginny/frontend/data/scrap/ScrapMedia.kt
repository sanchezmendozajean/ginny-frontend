package com.ginny.frontend.data.scrap

/**
 * Cada cosa que el scrapper puede obtener de un juego. El usuario elige cuáles descargar en
 * Configuración > Scrapper > "Qué descargar" ([ScrapperSettingsRepository.enabledMedia]).
 */
enum class ScrapMedia(
    val label: String,
    val description: String,
    /** Si está activado cuando el usuario todavía no eligió nada. */
    val defaultEnabled: Boolean = true,
) {
    METADATA("Metadatos", "Descripción, sistema, año y estudio (ScreenScraper)"),
    ICON("Ícono", "Imagen cuadrada del juego (SteamGridDB)"),
    WHEEL("Wheel", "Logo del juego (ScreenScraper)"),
    BOX_FRONT("Caja: frente", "Cara frontal de la caja (ScreenScraper)"),
    BOX_BACK("Caja: trasera", "Cara trasera de la caja (ScreenScraper)"),
    BOX_SPINE("Caja: lomo", "Lomo de la caja (ScreenScraper)"),
    VIDEO("Video", "Demostración del juego (ScreenScraper). Pesa varios MB por juego", defaultEnabled = false),
}
