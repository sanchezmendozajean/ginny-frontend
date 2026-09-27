package com.ginny.frontend.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ginny.frontend.R

/**
 * M PLUS Rounded 1c (OFL-licensed, see /licenses) ships as separate static-weight files rather
 * than a single variable font, so each weight below points at its own resource — no proprietary
 * typeface used. Reserved for headings/badges/titles. Body copy stays on the platform default
 * (Roboto) for maximum legibility/compatibility across whatever Android build a given
 * retro-console ships, same split the app already had with Baloo2.
 */
val MPlusRoundedFamily = FontFamily(
    Font(resId = R.font.mplus_rounded1c_medium, weight = FontWeight(500)),
    Font(resId = R.font.mplus_rounded1c_bold, weight = FontWeight(700)),
    Font(resId = R.font.mplus_rounded1c_extrabold, weight = FontWeight(800)),
    Font(resId = R.font.mplus_rounded1c_black, weight = FontWeight(900)),
)

val GinnyTypography = Typography(
    displaySmall = TextStyle(fontFamily = MPlusRoundedFamily, fontWeight = FontWeight(900), fontSize = 30.sp),
    titleLarge = TextStyle(fontFamily = MPlusRoundedFamily, fontWeight = FontWeight(800), fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = MPlusRoundedFamily, fontWeight = FontWeight(800), fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = MPlusRoundedFamily, fontWeight = FontWeight(700), fontSize = 15.sp),
    labelLarge = TextStyle(fontFamily = MPlusRoundedFamily, fontWeight = FontWeight(800), fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = MPlusRoundedFamily, fontWeight = FontWeight(700), fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = MPlusRoundedFamily, fontWeight = FontWeight(500), fontSize = 11.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
)
