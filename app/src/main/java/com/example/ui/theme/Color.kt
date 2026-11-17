package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemePalette(val label: String) {
  MONOCHROME("Monochrome"),
  PHOSPHOR("Phosphor"),
  COBALT("Cobalt"),
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
)

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
