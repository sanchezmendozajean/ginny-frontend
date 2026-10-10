package com.ginny.frontend.data.scrap

import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.data.scrap.GameMetadata.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapPlannerTest {

    private val now = 2_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    /** Lo que se descarga por defecto (todo menos el video). */
    private val defaults = ScrapMedia.entries.filter { it.defaultEnabled }.toSet()
    private val allFiles = defaults - ScrapMedia.METADATA

    private fun found(
        withDetails: Boolean = true,
        missing: Set<ScrapMedia> = emptySet(),
        scrapedAt: Long = now - day,
    ) = GameMetadata(
        status = Status.FOUND, scrapedAt = scrapedAt, name = "Juego",
        synopsis = if (withDetails) "Texto" else null, releaseYear = if (withDetails) 1990 else null,
        missingMedia = missing,
    )

    private fun plan(
        previous: GameMetadata?,
        present: Set<ScrapMedia> = emptySet(),
        wanted: Set<ScrapMedia> = defaults,
        force: Boolean = false,
    ) = ScrapPlanner.plan(previous, present, wanted, force, now)

    @Test
    fun `sobrescribir vuelve a pedir lo que ya esta guardado y los metadatos`() {
        val p = ScrapPlanner.plan(found(), allFiles, defaults, force = false, now = now, overwrite = true)
        assertEquals(allFiles, p.fetch)
        assertTrue(p.needsScreenScraper && p.needsSteamGridDb)
    }

    @Test
    fun `sobrescribir tambien ignora las caches negativas`() {
        val notFound = GameMetadata(status = Status.NOT_FOUND, scrapedAt = now - day)
        assertTrue(plan(notFound).isEmpty.not() || plan(notFound).needsSteamGridDb) // el ícono sigue pendiente
        val p = ScrapPlanner.plan(notFound, emptySet(), defaults, force = false, now = now, overwrite = true)
        assertTrue(p.needsScreenScraper)
        assertEquals(allFiles, p.fetch)
    }

    @Test
    fun `un juego nuevo pide todo a los dos proveedores`() {
        val p = plan(previous = null)
        assertEquals(allFiles, p.fetch)
        assertTrue(p.needsScreenScraper && p.needsSteamGridDb)
        assertFalse(p.isEmpty)
    }

    @Test
    fun `si ya esta todo guardado no se pide nada`() {
        val p = plan(found(), present = allFiles)
        assertTrue(p.isEmpty)
        assertEquals(emptySet<ScrapMedia>(), p.fetch)
    }

    @Test
    fun `solo se pide lo que falta`() {
        val p = plan(found(), present = allFiles - ScrapMedia.WHEEL)
        assertEquals(setOf(ScrapMedia.WHEEL), p.fetch)
        assertTrue(p.needsScreenScraper)
        assertFalse(p.needsSteamGridDb)
    }

    @Test
    fun `si solo falta el icono no se molesta a ScreenScraper`() {
        val p = plan(found(), present = allFiles - ScrapMedia.ICON)
        assertEquals(setOf(ScrapMedia.ICON), p.fetch)
        assertFalse(p.needsScreenScraper)
        assertTrue(p.needsSteamGridDb)
    }

    @Test
    fun `lo que el proveedor ya dijo que no tiene no se vuelve a pedir, salvo al forzar`() {
        val previous = found(missing = setOf(ScrapMedia.WHEEL))
        val present = allFiles - ScrapMedia.WHEEL
        assertTrue(plan(previous, present).isEmpty)
        assertEquals(setOf(ScrapMedia.WHEEL), plan(previous, present, force = true).fetch)
    }

    @Test
    fun `activar un tipo nuevo de media lo pide aunque lo demas este completo`() {
        val wanted = defaults + ScrapMedia.VIDEO
        val p = plan(found(), present = allFiles, wanted = wanted)
        assertEquals(setOf(ScrapMedia.VIDEO), p.fetch)
        assertTrue(p.needsScreenScraper)
    }

    @Test
    fun `un juego que ScreenScraper no conoce no se vuelve a consultar durante 30 dias`() {
        val negative = GameMetadata(Status.NOT_FOUND, scrapedAt = now - 5 * day)
        val p = plan(negative, present = emptySet())
        assertFalse(p.needsScreenScraper)
        // El ícono sí se puede buscar en SteamGridDB, y lo de ScreenScraper no se pide.
        assertEquals(setOf(ScrapMedia.ICON), p.fetch)
        assertTrue(p.needsSteamGridDb)
    }

    @Test
    fun `pasado el plazo, o al forzar, se vuelve a preguntar`() {
        val old = GameMetadata(Status.NOT_FOUND, scrapedAt = now - 31 * day)
        assertTrue(plan(old).needsScreenScraper)
        val recent = GameMetadata(Status.NOT_FOUND, scrapedAt = now - 1 * day)
        assertFalse(plan(recent).needsScreenScraper)
        assertTrue(plan(recent, force = true).needsScreenScraper)
    }

    @Test
    fun `solo el icono de un juego nunca consultado pregunta antes a ScreenScraper por el nombre estandar`() {
        val onlyIcon = setOf(ScrapMedia.ICON)
        assertTrue(plan(previous = null, wanted = onlyIcon).needsScreenScraper)
        assertTrue(plan(GameMetadata(Status.PENDING, now), wanted = onlyIcon).needsScreenScraper)
        // Si ya se conoce el nombre, no hace falta.
        assertFalse(plan(found(), wanted = onlyIcon).needsScreenScraper)
    }

    @Test
    fun `metadatos guardados sin texto se vuelven a pedir cuando se activan los metadatos`() {
        val p = plan(found(withDetails = false), present = allFiles)
        assertTrue(p.needsScreenScraper)
        assertEquals(emptySet<ScrapMedia>(), p.fetch)
        // Con los metadatos desactivados, no hay motivo.
        assertTrue(plan(found(withDetails = false), present = allFiles, wanted = allFiles).isEmpty)
    }

    @Test
    fun `sin nada que descargar no se pide nada`() {
        assertTrue(plan(previous = null, wanted = emptySet()).isEmpty)
    }

    // ── Hash ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `se calcula el hash solo de archivos chicos, sueltos y sin comprimir`() {
        val mb = 1024L * 1024
        assertTrue(ScrapPlanner.shouldHash(SystemId.NES, "Mario.nes", 40 * 1024, isFolder = false))
        assertTrue(ScrapPlanner.shouldHash(SystemId.GBA, "Zelda.GBA", 16 * mb, isFolder = false))
        assertFalse(ScrapPlanner.shouldHash(SystemId.PS2, "Juego.iso", 4000 * mb, isFolder = false)) // demasiado grande
        assertFalse(ScrapPlanner.shouldHash(SystemId.NES, "Mario.zip", 40 * 1024, isFolder = false)) // comprimido
        assertFalse(ScrapPlanner.shouldHash(SystemId.MAME, "pacman.zip", 40 * 1024, isFolder = false)) // arcade
        assertFalse(ScrapPlanner.shouldHash(SystemId.NEOGEO, "kof98.bin", 40 * 1024, isFolder = false))
        assertFalse(ScrapPlanner.shouldHash(SystemId.SWITCH, "Zelda", 1024, isFolder = true))
        assertFalse(ScrapPlanner.shouldHash(SystemId.NES, "Mario.nes", null, isFolder = false)) // tamaño desconocido
        assertFalse(ScrapPlanner.shouldHash(SystemId.NES, "Mario.nes", 0, isFolder = false))
    }
}
