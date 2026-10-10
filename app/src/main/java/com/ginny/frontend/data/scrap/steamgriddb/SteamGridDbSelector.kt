package com.ginny.frontend.data.scrap.steamgriddb

import com.ginny.frontend.data.scrap.NameNormalizer
import kotlin.math.abs

/**
 * Decide qué juego de SteamGridDB corresponde al que se busca y qué imagen cuadrada usar. Código puro.
 *
 * SteamGridDB no filtra por plataforma (mezcla PC, eShop y retro), así que **no se acepta cualquier
 * resultado**: solo los que se llaman casi igual, y es preferible no encontrar arte antes que poner el
 * de otro juego.
 */
object SteamGridDbSelector {

    /** Lado final del ícono. 512 es el preferido; no se agranda nada que sea menor. */
    const val TARGET_SIZE = 512

    /** Parecido mínimo (0.0-1.0) entre el nombre buscado y el de un resultado. */
    const val MIN_NAME_SIMILARITY = 0.92

    private val ACCEPTED_MIMES = setOf("image/png", "image/jpeg", "image/webp")
    private val LANGUAGE_PRIORITY = listOf("es", "en")

    /** Del mejor al peor estilo para un tile; `white_logo` (logo blanco sobre transparente) queda al final. */
    private val STYLE_PRIORITY = listOf("alternate", "official", "no_logo", "material", "custom", "blurred", "white_logo")

    // ── Qué juego ─────────────────────────────────────────────────────────────────────────────

    /**
     * Los resultados que se parecen lo bastante a [name], del que más al que menos, desempatando por:
     * el año más cercano a [expectedYear] (si se conoce; distingue un juego de su remake con el mismo
     * nombre), que esté verificado y, por último, el id más bajo (el más antiguo).
     */
    fun rankGames(candidates: List<SteamGridDbGame>, name: String, expectedYear: Int?): List<SteamGridDbGame> =
        candidates
            .map { it to NameNormalizer.similarity(it.name, name) }
            .filter { (_, similarity) -> similarity >= MIN_NAME_SIMILARITY }
            .sortedWith(
                compareByDescending<Pair<SteamGridDbGame, Double>> { it.second }
                    .thenBy { (game, _) -> yearDistance(game.releaseYear, expectedYear) }
                    .thenByDescending { it.first.verified }
                    .thenBy { it.first.id },
            )
            .map { it.first }

    private fun yearDistance(year: Int?, expected: Int?): Int =
        if (year == null || expected == null) Int.MAX_VALUE / 2 else abs(year - expected)

    // ── Qué imagen ────────────────────────────────────────────────────────────────────────────

    /**
     * La mejor imagen cuadrada de [images], o null si no hay ninguna utilizable.
     *
     * Solo cuentan las cuadradas, en PNG/JPEG/WebP (los `.ico` de SteamGridDB vienen con 0×0 y Android
     * no los abre bien). Orden de preferencia:
     * 1. **Tamaño:** exactamente 512; si no, las más grandes que 512 (la menor primero: se reducen sin
     *    perder calidad); si no, las más chicas (la mayor primero: **no se agrandan**).
     * 2. **Idioma:** español, inglés, otro.
     * 3. **Estilo:** `alternate` antes que `white_logo`, etc.
     * 4. **Puntuación**, y por último el id, para que el resultado sea estable.
     */
    fun pickImage(images: List<SteamGridDbImage>): SteamGridDbImage? =
        images
            .filter { it.isSquare && it.mime in ACCEPTED_MIMES }
            .sortedWith(
                compareBy<SteamGridDbImage> { sizeGroup(it.width) }
                    .thenBy { sizeWithinGroup(it.width) }
                    .thenBy { rank(LANGUAGE_PRIORITY, it.language) }
                    .thenBy { rank(STYLE_PRIORITY, it.style) }
                    .thenByDescending { it.score }
                    .thenBy { it.id },
            )
            .firstOrNull()

    /** 0 = justo el tamaño final, 1 = más grande (se reduce), 2 = más chico (se deja como está). */
    private fun sizeGroup(side: Int): Int = when {
        side == TARGET_SIZE -> 0
        side > TARGET_SIZE -> 1
        else -> 2
    }

    /** Dentro del grupo: los grandes, de menor a mayor; los chicos, de mayor a menor. */
    private fun sizeWithinGroup(side: Int): Int = if (side > TARGET_SIZE) side else -side

    /** Posición en [priority]; lo que no figura va después de todo lo que sí. */
    private fun rank(priority: List<String>, value: String?): Int =
        priority.indexOf(value).let { if (it >= 0) it else priority.size }
}
