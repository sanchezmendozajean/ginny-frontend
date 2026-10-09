package com.ginny.frontend.data.scrap

/**
 * Quita las credenciales de cualquier texto antes de mostrarlo o guardarlo en un log.
 *
 * Hace falta porque ScreenScraper pone `devid`, `devpassword`, `ssid` y `sspassword` en la query
 * string de **cada** petición (y en la `url` de cada media que devuelve), así que un mensaje de
 * `IOException` o una URL pegada en un error las filtraría.
 */
object ScrapRedactor {
    private val queryCredentials = Regex(
        """(?i)\b(devid|devpassword|devdebugpassword|ssid|sspassword)=[^&\s"']*""",
    )
    private val bearerToken = Regex("""(?i)\bBearer\s+\S+""")

    fun redact(text: String?): String =
        text.orEmpty()
            .replace(queryCredentials) { "${it.groupValues[1]}=<oculto>" }
            .replace(bearerToken, "Bearer <oculto>")
}
