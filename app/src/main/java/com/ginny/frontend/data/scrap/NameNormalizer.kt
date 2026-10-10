package com.ginny.frontend.data.scrap

import java.text.Normalizer
import java.util.Locale

/**
 * Normaliza y compara nombres de juego. Sirve sobre todo para decidir si el resultado de una
 * búsqueda de SteamGridDB (que no filtra por plataforma) es de verdad el juego que se buscaba.
 *
 * Código puro, sin Android.
 */
object NameNormalizer {

    private val bracketed = Regex("""\([^)]*\)|\[[^\]]*]""")
    private val trailingExtension = Regex("""\.[a-z0-9]{1,4}$""")
    private val trailingArticle = Regex("""^(.*),\s*(the|a|an)$""")
    private val leadingArticle = Regex("""^(the|a|an)\s+""")
    private val whitespace = Regex("""\s+""")
    private val diacritics = Regex("""\p{Mn}+""")
    private val nonAlphanumeric = Regex("""[^a-z0-9]+""")

    /**
     * Minúsculas, sin acentos, sin lo que va entre paréntesis o corchetes (`(USA)`, `[!]`), sin
     * artículo inicial (también el "Zelda, The" de los listados), con "&" como "and" y solo letras
     * y números separados por un espacio.
     *
     * [stripExtension]: quitar una extensión final (`.nes`, `.zip`) si el texto es un nombre de archivo.
     */
    fun normalize(name: String, stripExtension: Boolean = false): String {
        var text = name.lowercase(Locale.ROOT).trim()
        text = text.replace(bracketed, " ").trim()
        if (stripExtension) text = text.replace(trailingExtension, "").trim()
        text = Normalizer.normalize(text, Normalizer.Form.NFD).replace(diacritics, "")
        text = text.replace("&", " and ").replace("'", "").replace("’", "")
        trailingArticle.matchEntire(text)?.let { text = "${it.groupValues[2]} ${it.groupValues[1]}" }
        text = text.replace(nonAlphanumeric, " ").trim()
        return text.replace(leadingArticle, "").trim()
    }

    /**
     * El nombre listo para escribirlo en un buscador: sin lo que va entre paréntesis o corchetes
     * (`(USA)`, `[!]`), que confunde la búsqueda, y con los espacios colapsados. Conserva mayúsculas,
     * acentos y puntuación (a diferencia de [normalize], que es para comparar).
     */
    fun stripTags(name: String): String =
        name.replace(bracketed, " ").replace(whitespace, " ").trim()

    /**
     * Parecido entre dos nombres, de 0.0 a 1.0: 1.0 si normalizados son iguales; 0.0 si los
     * **números** que contienen no coinciden ("Super Mario Bros" frente a "Super Mario Bros 3" son
     * juegos distintos, aunque casi no cambie el texto); si no, el coeficiente de Dice sobre pares
     * de letras.
     */
    fun similarity(a: String, b: String, stripExtension: Boolean = false): Double {
        val x = normalize(a, stripExtension)
        val y = normalize(b, stripExtension)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        if (numbersOf(x) != numbersOf(y)) return 0.0
        return dice(x.replace(" ", ""), y.replace(" ", ""))
    }

    private fun numbersOf(normalized: String): List<String> =
        normalized.split(' ').filter { token -> token.isNotEmpty() && token.all { it.isDigit() } }

    private fun dice(x: String, y: String): Double {
        if (x.length < 2 || y.length < 2) return if (x == y) 1.0 else 0.0
        val pairs = HashMap<String, Int>()
        for (i in 0 until x.length - 1) pairs.merge(x.substring(i, i + 2), 1, Int::plus)
        var shared = 0
        for (i in 0 until y.length - 1) {
            val pair = y.substring(i, i + 2)
            val left = pairs[pair] ?: 0
            if (left > 0) {
                pairs[pair] = left - 1
                shared++
            }
        }
        return 2.0 * shared / ((x.length - 1) + (y.length - 1))
    }
}
