package com.example.data

import com.example.ui.theme.BackgroundMode
import com.example.ui.theme.CodeFontFamily
import com.example.ui.theme.SyntaxGradientPreset
import com.example.ui.theme.ThemePalette

enum class ScrollSpeed(
  val label: String,
  val delayMs: Long,
  val description: String,
) {
  INSTANT("Instant", 0L, "0ms"),
  FAST("Fast", 40L, "40ms"),
  SMOOTH("Smooth", 140L, "140ms"),
  SLOW("Slow", 320L, "320ms"),
}

data class AppSettings(
  val darkMode: Boolean = true,
  val themePalette: ThemePalette = ThemePalette.MONOCHROME,
  val executionIntervalMs: Int = 50,
  val scrollSpeed: ScrollSpeed = ScrollSpeed.SMOOTH,
  val codeFont: CodeFontFamily = CodeFontFamily.JETBRAINS_MONO,
  val editorFontSizeSp: Float = 13f,
  val terminalFontSizeSp: Float = 11.5f,
  val showLineNumbers: Boolean = true,
  val syntaxHighlighting: Boolean = true,
  val autoScrollTerminal: Boolean = true,
  val terminalColumns: Int = 52,
  val terminalRows: Int = 18,
  val showFrameDiffLogs: Boolean = true,
  val maxOpsPerExecution: Int = 500_000,
  val monochromeGuiPreview: Boolean = false,
  val enableHttpGet: Boolean = true,
  // Syntax & Font Gradients + Custom Colors
  val syntaxGradientEnabled: Boolean = false,
  val syntaxGradientMoving: Boolean = true,
  val fontGradientMoving: Boolean = false,
  val gradientPreset: SyntaxGradientPreset = SyntaxGradientPreset.SILVER_MIST,
  val gradientSpeed: Float = 1.0f,
  val syntaxKeywordArgb: Long = 0L,
  val syntaxTypeArgb: Long = 0L,
  val syntaxExecutorArgb: Long = 0L,
  val syntaxBuiltinArgb: Long = 0L,
  val syntaxStringArgb: Long = 0L,
  val syntaxNumberArgb: Long = 0L,
  val syntaxCommentArgb: Long = 0L,
  val syntaxOperatorArgb: Long = 0L,
  // Galaxy & Uploaded Photo/MP4 Video Background + Surface FX
  val backgroundMode: BackgroundMode = BackgroundMode.GALAXY,
  val customBackgroundPath: String = "",
  val customVideoPath: String = "",
  val backgroundVideoMuted: Boolean = true,
  val galaxySpeed: Float = 1.0f,
  val galaxyStarDensity: Float = 1.0f,
  val backgroundBlurDp: Float = 0f,
  val backgroundDimAlpha: Float = 0.28f,
  val panelOpacity: Float = 0.86f,
  val panelShadowDp: Float = 8f,
)
