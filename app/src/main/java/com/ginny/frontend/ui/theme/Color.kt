package com.ginny.frontend.ui.theme

import androidx.compose.ui.graphics.Color

// Palette translated 1:1 from docs/mockup-visual.html's CSS custom properties.
object GinnyColors {
    val BgPage = Color(0xFFDFE6EE)
    val TextPrimary = Color(0xFF33414D)
    val TextSecondary = Color(0xFF7C8A96)
    val TextInverse = Color(0xFFFFFFFF)

    val AccentCeleste = Color(0xFF7DD3F0)
    val AccentBlue = Color(0xFF2F6FED)
    val AccentRed = Color(0xFFE2665F)
    val AccentRedSoft = Color(0xFFEA8781)
    val AccentYellow = Color(0xFFF2B134)
    val AccentYellowSoft = Color(0xFFF6C766)

    val CardBg = Color(0xFFFFFFFF)
    val BarBg = Color(0xB8FFFFFF) // rgba(255,255,255,0.72)
    val BarBorder = Color(0xE6FFFFFF) // rgba(255,255,255,0.9)
    val UnfocusedBorder = Color(0xFFFFFFFF)

    // Aurora background blobs (radial gradient stops), from --shell-bg.
    val AuroraLilac = Color(0x47A88CD6)
    val AuroraGreen = Color(0x4D78C8AF)
    val AuroraCream = Color(0x52F0D696)
    val AuroraBlue = Color(0x427896D7)
    val AuroraPink = Color(0x42DC8CB9)

    // System tile color rotation.
    val SystemColors = listOf(
        Color(0xFFFF8A65),
        Color(0xFF4FC3A1),
        Color(0xFF7E8CE0),
        Color(0xFFF2B134),
        Color(0xFFEC6F9C),
        Color(0xFF5BB8D4),
    )
}
