package com.example.data

import com.example.ui.theme.CodeFontFamily
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
)
