package com.ginny.frontend.ui.screens.settings

import android.net.Uri
import android.provider.DocumentsContract

/*
 * Rutas legibles de las carpetas elegidas con el selector de carpetas del sistema (SAF). Su URI
 * trae un id como "primary:Ginny/ROMs" (memoria interna) o "1A2B-3C4D:ROMs" (tarjeta SD).
 */

/** Id del documento raíz de una carpeta SAF ("primary:Ginny/ROMs"), o null si no es una. */
private fun treeDocId(uri: String): String? =
    runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)) }.getOrNull()

/** "primary:Ginny/ROMs" → "Memoria interna/Ginny/ROMs". Sin carpeta: "Sin elegir". */
fun folderLabel(uri: String?): String {
    if (uri == null) return "Sin elegir"
    val docId = treeDocId(uri) ?: return uri
    val volume = docId.substringBefore(':')
    val path = docId.substringAfter(':', "")
    val root = if (volume == "primary") "Memoria interna" else "Tarjeta SD"
    return if (path.isEmpty()) root else "$root/$path"
}

/**
 * Carpeta de ROMs de un sistema, como se muestra en Configuración > Sistemas:
 * - Sin carpeta propia ([override] null): `<ROMS>/<folderName>`, la subcarpeta de la carpeta de
 *   ROMs de la sección Carpetas.
 * - Con carpeta propia dentro de la carpeta de ROMs: también `<ROMS>/…`.
 * - Con carpeta propia en otro lado: la ruta completa.
 */
fun systemFolderLabel(override: String?, romFolder: String?, folderName: String): String {
    if (override == null) {
        return if (romFolder == null) "<ROMS>/$folderName · elige la carpeta de ROMs en Carpetas" else "<ROMS>/$folderName"
    }
    val romDocId = romFolder?.let(::treeDocId)
    val overrideDocId = treeDocId(override)
    if (romDocId != null && overrideDocId != null && overrideDocId.startsWith("$romDocId/")) {
        return "<ROMS>/" + overrideDocId.removePrefix("$romDocId/")
    }
    return folderLabel(override)
}
