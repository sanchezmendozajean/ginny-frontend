package com.ginny.frontend.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ginny.frontend.R

/**
 * M PLUS Rounded 1c (Google Fonts, OFL — ver licenses/MPLUSRounded1c-OFL.txt). Solo están los 4
 * pesos de res/font/; si se pide otro (ej. [FontWeight.Normal]), Compose usa el más cercano.
 *
 * Uso: `Text("Hola", fontFamily = GinnyFont, fontWeight = FontWeight.Bold)`
 */
val GinnyFont = FontFamily(
    Font(R.font.mplus_rounded1c_medium, FontWeight.Medium),       // 500
    Font(R.font.mplus_rounded1c_bold, FontWeight.Bold),           // 700
    Font(R.font.mplus_rounded1c_extrabold, FontWeight.ExtraBold), // 800
    Font(R.font.mplus_rounded1c_black, FontWeight.Black),         // 900
)
