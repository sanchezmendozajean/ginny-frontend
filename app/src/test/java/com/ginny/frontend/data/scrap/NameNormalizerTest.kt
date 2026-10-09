package com.ginny.frontend.data.scrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameNormalizerTest {

    @Test
    fun `quita region extension y puntuacion de un nombre de archivo`() {
        assertEquals("super mario bros", NameNormalizer.normalize("Super Mario Bros. (World).nes", stripExtension = true))
        assertEquals("adventure island 2", NameNormalizer.normalize("Adventure Island 2.nes", stripExtension = true))
        assertEquals("sonic the hedgehog", NameNormalizer.normalize("Sonic The Hedgehog (USA, Europe) [!].md", stripExtension = true))
    }

    @Test
    fun `sin stripExtension no recorta puntos que son del titulo`() {
        assertEquals("dr mario", NameNormalizer.normalize("Dr. Mario"))
    }

    @Test
    fun `articulo inicial y final dan lo mismo`() {
        assertEquals(NameNormalizer.normalize("Legend of Zelda, The"), NameNormalizer.normalize("The Legend of Zelda"))
        assertEquals("legend of zelda", NameNormalizer.normalize("The Legend of Zelda"))
    }

    @Test
    fun `acentos ampersand y apostrofes`() {
        assertEquals("pokemon rojo", NameNormalizer.normalize("Pokémon Rojo"))
        assertEquals("rock and roll racing", NameNormalizer.normalize("Rock & Roll Racing"))
        assertEquals("yoshis island", NameNormalizer.normalize("Yoshi's Island"))
    }

    @Test
    fun `nombres iguales al normalizar valen 1`() {
        assertEquals(1.0, NameNormalizer.similarity("Super Mario Bros. (World).nes", "Super Mario Bros.", stripExtension = true), 0.0)
    }

    @Test
    fun `numeros distintos son juegos distintos aunque el texto casi no cambie`() {
        assertEquals(0.0, NameNormalizer.similarity("Super Mario Bros", "Super Mario Bros 3"), 0.0)
        assertEquals(0.0, NameNormalizer.similarity("Adventure Island", "Adventure Island 2"), 0.0)
    }

    @Test
    fun `una variante cercana puntua alto y un juego distinto puntua bajo`() {
        assertTrue(NameNormalizer.similarity("Teenage Mutant Ninja Turtles", "Teenage Mutant Ninja Turtle") > 0.9)
        assertTrue(NameNormalizer.similarity("Super Mario Bros", "Metroid") < 0.5)
    }

    @Test
    fun `vacio no coincide con nada`() {
        assertEquals(0.0, NameNormalizer.similarity("", "Metroid"), 0.0)
        assertEquals(0.0, NameNormalizer.similarity("(USA)", "Metroid"), 0.0)
    }
}
