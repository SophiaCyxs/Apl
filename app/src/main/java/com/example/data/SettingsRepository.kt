package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.CodeFontFamily
import com.example.ui.theme.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "bloxlua_settings")

class SettingsRepository(private val context: Context) {
  private object Keys {
    val DARK_MODE = booleanPreferencesKey("dark_mode")
    val THEME_PALETTE = stringPreferencesKey("theme_palette")
    val EXECUTION_INTERVAL_MS = intPreferencesKey("execution_interval_ms")
    val SCROLL_SPEED = stringPreferencesKey("scroll_speed")
    val CODE_FONT = stringPreferencesKey("code_font")
    val EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size")
    val TERMINAL_FONT_SIZE = floatPreferencesKey("terminal_font_size")
    val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
    val SYNTAX_HIGHLIGHTING = booleanPreferencesKey("syntax_highlighting")
    val AUTO_SCROLL_TERMINAL = booleanPreferencesKey("auto_scroll_terminal")
    val TERMINAL_COLUMNS = intPreferencesKey("terminal_columns")
    val TERMINAL_ROWS = intPreferencesKey("terminal_rows")
    val SHOW_FRAME_DIFF_LOGS = booleanPreferencesKey("show_frame_diff_logs")
    val MAX_OPS = intPreferencesKey("max_ops")
    val MONOCHROME_GUI_PREVIEW = booleanPreferencesKey("monochrome_gui_preview")
  }

  private fun parseSettings(prefs: Preferences): AppSettings =
    AppSettings(
      darkMode = prefs[Keys.DARK_MODE] ?: true,
      themePalette =
        prefs[Keys.THEME_PALETTE]?.let { name ->
          ThemePalette.entries.firstOrNull { it.name == name }
        } ?: ThemePalette.MONOCHROME,
      executionIntervalMs = (prefs[Keys.EXECUTION_INTERVAL_MS] ?: 50).coerceIn(16, 1000),
      scrollSpeed =
        prefs[Keys.SCROLL_SPEED]?.let { name ->
          ScrollSpeed.entries.firstOrNull { it.name == name }
        } ?: ScrollSpeed.SMOOTH,
      codeFont =
        prefs[Keys.CODE_FONT]?.let { name ->
          CodeFontFamily.entries.firstOrNull { it.name == name }
        } ?: CodeFontFamily.JETBRAINS_MONO,
      editorFontSizeSp = (prefs[Keys.EDITOR_FONT_SIZE] ?: 13f).coerceIn(10f, 22f),
      terminalFontSizeSp = (prefs[Keys.TERMINAL_FONT_SIZE] ?: 11.5f).coerceIn(9f, 20f),
      showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
      syntaxHighlighting = prefs[Keys.SYNTAX_HIGHLIGHTING] ?: true,
      autoScrollTerminal = prefs[Keys.AUTO_SCROLL_TERMINAL] ?: true,
      terminalColumns = (prefs[Keys.TERMINAL_COLUMNS] ?: 52).coerceIn(36, 72),
      terminalRows = (prefs[Keys.TERMINAL_ROWS] ?: 18).coerceIn(12, 26),
      showFrameDiffLogs = prefs[Keys.SHOW_FRAME_DIFF_LOGS] ?: true,
      maxOpsPerExecution = (prefs[Keys.MAX_OPS] ?: 500_000).coerceIn(50_000, 2_000_000),
      monochromeGuiPreview = prefs[Keys.MONOCHROME_GUI_PREVIEW] ?: false,
    )

  val settingsFlow: Flow<AppSettings> =
    context.settingsDataStore.data.map { prefs -> parseSettings(prefs) }

  suspend fun update(transform: (AppSettings) -> AppSettings) {
    context.settingsDataStore.edit { prefs ->
      val next = transform(parseSettings(prefs))
      prefs[Keys.DARK_MODE] = next.darkMode
      prefs[Keys.THEME_PALETTE] = next.themePalette.name
      prefs[Keys.EXECUTION_INTERVAL_MS] = next.executionIntervalMs
      prefs[Keys.SCROLL_SPEED] = next.scrollSpeed.name
      prefs[Keys.CODE_FONT] = next.codeFont.name
      prefs[Keys.EDITOR_FONT_SIZE] = next.editorFontSizeSp
      prefs[Keys.TERMINAL_FONT_SIZE] = next.terminalFontSizeSp
      prefs[Keys.SHOW_LINE_NUMBERS] = next.showLineNumbers
      prefs[Keys.SYNTAX_HIGHLIGHTING] = next.syntaxHighlighting
      prefs[Keys.AUTO_SCROLL_TERMINAL] = next.autoScrollTerminal
      prefs[Keys.TERMINAL_COLUMNS] = next.terminalColumns
      prefs[Keys.TERMINAL_ROWS] = next.terminalRows
      prefs[Keys.SHOW_FRAME_DIFF_LOGS] = next.showFrameDiffLogs
      prefs[Keys.MAX_OPS] = next.maxOpsPerExecution
      prefs[Keys.MONOCHROME_GUI_PREVIEW] = next.monochromeGuiPreview
    }
  }

  suspend fun resetDefaults() {
    context.settingsDataStore.edit { it.clear() }
  }
}
