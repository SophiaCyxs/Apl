package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val SpaceGroteskFontFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
val InterFontFamily = FontFamily(Font(R.font.inter, FontWeight.Normal))

val JetBrainsMonoFontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))
val FiraCodeFontFamily = FontFamily(Font(R.font.fira_code, FontWeight.Normal))
val RobotoMonoFontFamily = FontFamily(Font(R.font.roboto_mono, FontWeight.Normal))
val SourceCodeProFontFamily = FontFamily(Font(R.font.source_code_pro, FontWeight.Normal))
val IbmPlexMonoFontFamily = FontFamily(Font(R.font.ibm_plex_mono, FontWeight.Normal))

enum class CodeFontFamily(val displayName: String, val tag: String) {
  JETBRAINS_MONO("JetBrains Mono", "Default IDE"),
  FIRA_CODE("Fira Code", "Ligature Style"),
  ROBOTO_MONO("Roboto Mono", "Clean Geometric"),
  SOURCE_CODE_PRO("Source Code Pro", "Adobe Classic"),
  IBM_PLEX_MONO("IBM Plex Mono", "Technical Crisp");

  fun toComposeFontFamily(): FontFamily =
    when (this) {
      JETBRAINS_MONO -> JetBrainsMonoFontFamily
      FIRA_CODE -> FiraCodeFontFamily
      ROBOTO_MONO -> RobotoMonoFontFamily
      SOURCE_CODE_PRO -> SourceCodeProFontFamily
      IBM_PLEX_MONO -> IbmPlexMonoFontFamily
    }
}

val AppTypography =
  Typography(
    headlineMedium =
      TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
      ),
  )
