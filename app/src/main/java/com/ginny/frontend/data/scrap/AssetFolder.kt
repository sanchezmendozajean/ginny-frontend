package com.ginny.frontend.data.scrap

/**
 * Las carpetas de `Assets/<sistema>/` que escribe el scrapper. Es parte del **contrato con Ginny
 * Assets Manager**: cada archivo se llama `<romFileName>.<extensión>`, donde `romFileName` es el
 * nombre del archivo de la ROM **con su extensión** (o el de la carpeta, en Switch), igual que los
 * íconos de siempre (`Mario.nes.png`).
 */
enum class AssetFolder(
    val dirName: String,
    /** Extensiones válidas (minúsculas, sin punto) de lo que se guarda aquí. */
    val extensions: Set<String>,
) {
    /** Ícono cuadrado: siempre PNG (convención de siempre; lo lee `AssetRepository.loadIconIndex`). */
    ICONS("icons", setOf("png")),
    WHEEL("wheel", IMAGE_EXTENSIONS),
    BOX_FRONT("box-front", IMAGE_EXTENSIONS),
    BOX_BACK("box-back", IMAGE_EXTENSIONS),
    BOX_SPINE("box-spine", IMAGE_EXTENSIONS),
    VIDEO("video", setOf("mp4")),

    /** Un JSON por juego ([GameMetadata]). */
    METADATA("metadata", setOf("json")),
    ;

    companion object {
        /** La carpeta donde va cada tipo de [ScrapMedia]. */
        fun of(media: ScrapMedia): AssetFolder = when (media) {
            ScrapMedia.METADATA -> METADATA
            ScrapMedia.ICON -> ICONS
            ScrapMedia.WHEEL -> WHEEL
            ScrapMedia.BOX_FRONT -> BOX_FRONT
            ScrapMedia.BOX_BACK -> BOX_BACK
            ScrapMedia.BOX_SPINE -> BOX_SPINE
            ScrapMedia.VIDEO -> VIDEO
        }
    }
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
