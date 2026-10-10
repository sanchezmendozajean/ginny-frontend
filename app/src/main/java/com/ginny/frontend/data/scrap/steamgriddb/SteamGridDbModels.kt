package com.ginny.frontend.data.scrap.steamgriddb

/** Un juego de los resultados de `search/autocomplete` de SteamGridDB. */
data class SteamGridDbGame(
    val id: Long,
    val name: String,
    val verified: Boolean,
    /** Año de `release_date` (segundos desde 1970, UTC); null si el juego no la tiene. */
    val releaseYear: Int?,
)

/** Una imagen de SteamGridDB (un *grid* o un *icon*). */
data class SteamGridDbImage(
    val id: Long,
    val url: String,
    val width: Int,
    val height: Int,
    /** `image/png`, `image/jpeg`... */
    val mime: String,
    /** `alternate`, `white_logo`, `official`, `custom`... */
    val style: String?,
    /** Idioma del arte (`en`, `ja`...); null si no lo indica. */
    val language: String?,
    val score: Int,
    /** true si salió de `grids`; false si de `icons` (el respaldo). */
    val isGrid: Boolean,
) {
    val isSquare: Boolean get() = width > 0 && width == height

    /** true si es más grande que el tamaño final: se reduce al guardarla. Nunca se agranda. */
    val needsDownscale: Boolean get() = width > SteamGridDbSelector.TARGET_SIZE
}

/** El resultado de buscar el ícono de un juego: de qué juego de SteamGridDB salió y la imagen elegida. */
data class SteamGridDbMatch(val game: SteamGridDbGame, val image: SteamGridDbImage)
