package com.kimdev.petmap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val default = Typography()

val Typography = Typography(
    headlineSmall = default.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = default.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = default.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)
