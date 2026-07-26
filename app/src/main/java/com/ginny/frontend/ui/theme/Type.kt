package com.ginny.frontend.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ginny.frontend.R

/**
 * Baloo2 (OFL-licensed, see /licenses) is a single variable font file; each weight below points at
 * the same resource with a different `wght` axis instance — no proprietary Nintendo typeface used.
 * Reserved for headings/badges/titles. Body copy uses the platform default (Roboto) for maximum
 * legibility/compatibility across whatever Android build a given retro-console ships.
 */
@OptIn(ExperimentalTextApi::class)
private fun balooWeight(weight: Int) = Font(
    resId = R.font.baloo2,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val BalooFamily = FontFamily(
    balooWeight(500),
    balooWeight(600),
    balooWeight(700),
    balooWeight(800),
)

val GinnyTypography = Typography(
    displaySmall = TextStyle(fontFamily = BalooFamily, fontWeight = FontWeight(800), fontSize = 30.sp),
    titleLarge = TextStyle(fontFamily = BalooFamily, fontWeight = FontWeight(700), fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = BalooFamily, fontWeight = FontWeight(700), fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = BalooFamily, fontWeight = FontWeight(600), fontSize = 15.sp),
    labelLarge = TextStyle(fontFamily = BalooFamily, fontWeight = FontWeight(700), fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = BalooFamily, fontWeight = FontWeight(600), fontSize = 13.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
)
