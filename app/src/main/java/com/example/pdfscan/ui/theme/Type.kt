package com.example.pdfscan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Default: system font (Roboto) so the project builds with zero extra files.
 *
 * To use Inter: drop inter_regular.ttf, inter_medium.ttf, inter_semibold.ttf into
 * app/src/main/res/font/, then replace the line below with:
 *
 *   val Inter = FontFamily(
 *       Font(R.font.inter_regular, FontWeight.Normal),
 *       Font(R.font.inter_medium, FontWeight.Medium),
 *       Font(R.font.inter_semibold, FontWeight.SemiBold),
 *   )
 * and add: import androidx.compose.ui.text.font.Font / import com.example.pdfscan.R
 */
val Inter: FontFamily = FontFamily.Default

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, letterSpacing = tracking.sp,
)

val AppTypography = Typography(
    titleLarge = style(22, 28, FontWeight.SemiBold),            // screen titles
    titleMedium = style(16, 24, FontWeight.Medium),             // file names
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),               // timestamps
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),         // buttons
    labelSmall = style(11, 16, FontWeight.Medium, 0.3),         // size badges
)
