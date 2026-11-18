package com.example.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

enum class ThemePalette(val label: String) {
  MONOCHROME("Monochrome"),
  PHOSPHOR("Phosphor"),
  COBALT("Cobalt"),
}

enum class BackgroundMode(val label: String) {
  GALAXY("Galaxy"),
  SOLID("Minimal"),
  CUSTOM_IMAGE("Photo"),
  CUSTOM_VIDEO("Video MP4"),
}

enum class SyntaxGradientPreset(val label: String, val colors: List<Color>) {
  SILVER_MIST(
    "Silver Mist",
    listOf(Color(0xFFFFFFFF), Color(0xFFA1A1AA), Color(0xFFE4E4E7), Color(0xFF71717A)),
  ),
  COSMIC_NEBULA(
    "Cosmic",
    listOf(Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFF38BDF8)),
  ),
  AURORA(
    "Aurora",
    listOf(Color(0xFF34D399), Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFF34D399)),
  ),
  SOLAR_PULSE(
    "Solar",
    listOf(Color(0xFFFBBF24), Color(0xFFF87171), Color(0xFFE879F9), Color(0xFFFBBF24)),
  ),
  PRISM_CYAN(
    "Cyan Ice",
    listOf(Color(0xFF67E8F9), Color(0xFF3B82F6), Color(0xFF22D3EE), Color(0xFFA5F3FC)),
  ),
}

fun buildMovingGradientBrush(
  colors: List<Color>,
  phase: Float,
  spanPx: Float = 900f,
): Brush {
  val safeColors = if (colors.size >= 2) colors else listOf(Color.White, Color.Gray)
  val shift = (phase % 1f) * spanPx
  return Brush.linearGradient(
    colors = safeColors,
    start = Offset(shift, shift * 0.35f),
    end = Offset(shift + spanPx * 0.72f, shift * 0.35f + spanPx * 0.28f),
    tileMode = TileMode.Mirror,
  )
}

// Monochrome Dark (Pure Zinc / Grayscale)
val MonoDarkBg = Color(0xFF09090B)
val MonoDarkSurface = Color(0xFF111114)
val MonoDarkSurfaceElev = Color(0xFF1A1A1E)
val MonoDarkTerminal = Color(0xFF050507)
val MonoDarkBorder = Color(0xFF27272A)
val MonoDarkText = Color(0xFFF4F4F5)
val MonoDarkMuted = Color(0xFF71717A)

// Monochrome Light (Paper / Ink)
val MonoLightBg = Color(0xFFF4F4F5)
val MonoLightSurface = Color(0xFFFFFFFF)
val MonoLightSurfaceElev = Color(0xFFE4E4E7)
val MonoLightTerminal = Color(0xFFFAFAFA)
val MonoLightBorder = Color(0xFFD4D4D8)
val MonoLightText = Color(0xFF09090B)
val MonoLightMuted = Color(0xFF52525B)

data class SyntaxColors(
  val keyword: Color,
  val robloxType: Color,
  val executorApi: Color,
  val builtin: Color,
  val stringLiteral: Color,
  val numberLiteral: Color,
  val comment: Color,
  val operator: Color,
  val defaultText: Color,
) {
  fun withOverrides(
    keywordArgb: Long = 0L,
    typeArgb: Long = 0L,
    executorArgb: Long = 0L,
    builtinArgb: Long = 0L,
    stringArgb: Long = 0L,
    numberArgb: Long = 0L,
    commentArgb: Long = 0L,
    operatorArgb: Long = 0L,
  ): SyntaxColors =
    copy(
      keyword = if (keywordArgb != 0L) Color(keywordArgb) else keyword,
      robloxType = if (typeArgb != 0L) Color(typeArgb) else robloxType,
      executorApi = if (executorArgb != 0L) Color(executorArgb) else executorApi,
      builtin = if (builtinArgb != 0L) Color(builtinArgb) else builtin,
      stringLiteral = if (stringArgb != 0L) Color(stringArgb) else stringLiteral,
      numberLiteral = if (numberArgb != 0L) Color(numberArgb) else numberLiteral,
      comment = if (commentArgb != 0L) Color(commentArgb) else comment,
      operator = if (operatorArgb != 0L) Color(operatorArgb) else operator,
    )
}

val MonoDarkSyntax =
  SyntaxColors(
    keyword = Color(0xFFFFFFFF),
    robloxType = Color(0xFFD4D4D8),
    executorApi = Color(0xFFE4E4E7),
    builtin = Color(0xFFA1A1AA),
    stringLiteral = Color(0xFFB8B8C2),
    numberLiteral = Color(0xFFE4E4E7),
    comment = Color(0xFF52525B),
    operator = Color(0xFF9F9FA9),
    defaultText = Color(0xFFE4E4E7),
  )

val MonoLightSyntax =
  SyntaxColors(
    keyword = Color(0xFF09090B),
    robloxType = Color(0xFF27272A),
    executorApi = Color(0xFF18181B),
    builtin = Color(0xFF3F3F46),
    stringLiteral = Color(0xFF52525B),
    numberLiteral = Color(0xFF27272A),
    comment = Color(0xFFA1A1AA),
    operator = Color(0xFF52525B),
    defaultText = Color(0xFF18181B),
  )

val PhosphorDarkSyntax =
  SyntaxColors(
    keyword = Color(0xFF4ADE80),
    robloxType = Color(0xFF86EFAC),
    executorApi = Color(0xFFFACC15),
    builtin = Color(0xFF34D399),
    stringLiteral = Color(0xFFBEF264),
    numberLiteral = Color(0xFFFDE047),
    comment = Color(0xFF52665A),
    operator = Color(0xFF6EE7B7),
    defaultText = Color(0xFFD1FAE5),
  )

val PhosphorLightSyntax =
  SyntaxColors(
    keyword = Color(0xFF15803D),
    robloxType = Color(0xFF047857),
    executorApi = Color(0xFFB45309),
    builtin = Color(0xFF059669),
    stringLiteral = Color(0xFF3F6212),
    numberLiteral = Color(0xFFA16207),
    comment = Color(0xFF6B7280),
    operator = Color(0xFF065F46),
    defaultText = Color(0xFF064E3B),
  )

val CobaltDarkSyntax =
  SyntaxColors(
    keyword = Color(0xFFFF7B72),
    robloxType = Color(0xFF38BDF8),
    executorApi = Color(0xFFA78BFA),
    builtin = Color(0xFF34D399),
    stringLiteral = Color(0xFFA5D6FF),
    numberLiteral = Color(0xFFFBBF24),
    comment = Color(0xFF6E7681),
    operator = Color(0xFF79C0FF),
    defaultText = Color(0xFFE6EDF3),
  )

val CobaltLightSyntax =
  SyntaxColors(
    keyword = Color(0xFFCF222E),
    robloxType = Color(0xFF0284C7),
    executorApi = Color(0xFF6D28D9),
    builtin = Color(0xFF059669),
    stringLiteral = Color(0xFF0A3069),
    numberLiteral = Color(0xFFB45309),
    comment = Color(0xFF6E7781),
    operator = Color(0xFF0550AE),
    defaultText = Color(0xFF0F172A),
  )
