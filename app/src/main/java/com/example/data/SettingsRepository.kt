package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.BackgroundMode
import com.example.ui.theme.CodeFontFamily
import com.example.ui.theme.SyntaxGradientPreset
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
    val ENABLE_HTTP_GET = booleanPreferencesKey("enable_http_get")

    val SYNTAX_GRADIENT_ENABLED = booleanPreferencesKey("syntax_gradient_enabled")
    val SYNTAX_GRADIENT_MOVING = booleanPreferencesKey("syntax_gradient_moving")
    val FONT_GRADIENT_MOVING = booleanPreferencesKey("font_gradient_moving")
    val GRADIENT_PRESET = stringPreferencesKey("gradient_preset")
    val GRADIENT_SPEED = floatPreferencesKey("gradient_speed")

    val SYNTAX_KEYWORD_ARGB = longPreferencesKey("syntax_keyword_argb")
    val SYNTAX_TYPE_ARGB = longPreferencesKey("syntax_type_argb")
    val SYNTAX_EXECUTOR_ARGB = longPreferencesKey("syntax_executor_argb")
    val SYNTAX_BUILTIN_ARGB = longPreferencesKey("syntax_builtin_argb")
    val SYNTAX_STRING_ARGB = longPreferencesKey("syntax_string_argb")
    val SYNTAX_NUMBER_ARGB = longPreferencesKey("syntax_number_argb")
    val SYNTAX_COMMENT_ARGB = longPreferencesKey("syntax_comment_argb")
    val SYNTAX_OPERATOR_ARGB = longPreferencesKey("syntax_operator_argb")

    val BACKGROUND_MODE = stringPreferencesKey("background_mode")
    val CUSTOM_BACKGROUND_PATH = stringPreferencesKey("custom_background_path")
    val CUSTOM_VIDEO_PATH = stringPreferencesKey("custom_video_path")
    val BACKGROUND_VIDEO_MUTED = booleanPreferencesKey("background_video_muted")
    val GALAXY_SPEED = floatPreferencesKey("galaxy_speed")
    val GALAXY_STAR_DENSITY = floatPreferencesKey("galaxy_star_density")
    val BACKGROUND_BLUR_DP = floatPreferencesKey("background_blur_dp")
    val BACKGROUND_DIM_ALPHA = floatPreferencesKey("background_dim_alpha")
    val PANEL_OPACITY = floatPreferencesKey("panel_opacity")
    val PANEL_SHADOW_DP = floatPreferencesKey("panel_shadow_dp")
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
      enableHttpGet = prefs[Keys.ENABLE_HTTP_GET] ?: true,
      syntaxGradientEnabled = prefs[Keys.SYNTAX_GRADIENT_ENABLED] ?: false,
      syntaxGradientMoving = prefs[Keys.SYNTAX_GRADIENT_MOVING] ?: true,
      fontGradientMoving = prefs[Keys.FONT_GRADIENT_MOVING] ?: false,
      gradientPreset =
        prefs[Keys.GRADIENT_PRESET]?.let { name ->
          SyntaxGradientPreset.entries.firstOrNull { it.name == name }
        } ?: SyntaxGradientPreset.SILVER_MIST,
      gradientSpeed = (prefs[Keys.GRADIENT_SPEED] ?: 1.0f).coerceIn(0.2f, 3.0f),
      syntaxKeywordArgb = prefs[Keys.SYNTAX_KEYWORD_ARGB] ?: 0L,
      syntaxTypeArgb = prefs[Keys.SYNTAX_TYPE_ARGB] ?: 0L,
      syntaxExecutorArgb = prefs[Keys.SYNTAX_EXECUTOR_ARGB] ?: 0L,
      syntaxBuiltinArgb = prefs[Keys.SYNTAX_BUILTIN_ARGB] ?: 0L,
      syntaxStringArgb = prefs[Keys.SYNTAX_STRING_ARGB] ?: 0L,
      syntaxNumberArgb = prefs[Keys.SYNTAX_NUMBER_ARGB] ?: 0L,
      syntaxCommentArgb = prefs[Keys.SYNTAX_COMMENT_ARGB] ?: 0L,
      syntaxOperatorArgb = prefs[Keys.SYNTAX_OPERATOR_ARGB] ?: 0L,
      backgroundMode =
        prefs[Keys.BACKGROUND_MODE]?.let { name ->
          BackgroundMode.entries.firstOrNull { it.name == name }
        } ?: BackgroundMode.GALAXY,
      customBackgroundPath = prefs[Keys.CUSTOM_BACKGROUND_PATH] ?: "",
      customVideoPath = prefs[Keys.CUSTOM_VIDEO_PATH] ?: "",
      backgroundVideoMuted = prefs[Keys.BACKGROUND_VIDEO_MUTED] ?: true,
      galaxySpeed = (prefs[Keys.GALAXY_SPEED] ?: 1.0f).coerceIn(0.1f, 3.0f),
      galaxyStarDensity = (prefs[Keys.GALAXY_STAR_DENSITY] ?: 1.0f).coerceIn(0.3f, 2.0f),
      backgroundBlurDp = (prefs[Keys.BACKGROUND_BLUR_DP] ?: 0f).coerceIn(0f, 24f),
      backgroundDimAlpha = (prefs[Keys.BACKGROUND_DIM_ALPHA] ?: 0.28f).coerceIn(0f, 0.85f),
      panelOpacity = (prefs[Keys.PANEL_OPACITY] ?: 0.86f).coerceIn(0.30f, 1.0f),
      panelShadowDp = (prefs[Keys.PANEL_SHADOW_DP] ?: 8f).coerceIn(0f, 24f),
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
      prefs[Keys.ENABLE_HTTP_GET] = next.enableHttpGet
      prefs[Keys.SYNTAX_GRADIENT_ENABLED] = next.syntaxGradientEnabled
      prefs[Keys.SYNTAX_GRADIENT_MOVING] = next.syntaxGradientMoving
      prefs[Keys.FONT_GRADIENT_MOVING] = next.fontGradientMoving
      prefs[Keys.GRADIENT_PRESET] = next.gradientPreset.name
      prefs[Keys.GRADIENT_SPEED] = next.gradientSpeed
      prefs[Keys.SYNTAX_KEYWORD_ARGB] = next.syntaxKeywordArgb
      prefs[Keys.SYNTAX_TYPE_ARGB] = next.syntaxTypeArgb
      prefs[Keys.SYNTAX_EXECUTOR_ARGB] = next.syntaxExecutorArgb
      prefs[Keys.SYNTAX_BUILTIN_ARGB] = next.syntaxBuiltinArgb
      prefs[Keys.SYNTAX_STRING_ARGB] = next.syntaxStringArgb
      prefs[Keys.SYNTAX_NUMBER_ARGB] = next.syntaxNumberArgb
      prefs[Keys.SYNTAX_COMMENT_ARGB] = next.syntaxCommentArgb
      prefs[Keys.SYNTAX_OPERATOR_ARGB] = next.syntaxOperatorArgb
      prefs[Keys.BACKGROUND_MODE] = next.backgroundMode.name
      prefs[Keys.CUSTOM_BACKGROUND_PATH] = next.customBackgroundPath
      prefs[Keys.CUSTOM_VIDEO_PATH] = next.customVideoPath
      prefs[Keys.BACKGROUND_VIDEO_MUTED] = next.backgroundVideoMuted
      prefs[Keys.GALAXY_SPEED] = next.galaxySpeed
      prefs[Keys.GALAXY_STAR_DENSITY] = next.galaxyStarDensity
      prefs[Keys.BACKGROUND_BLUR_DP] = next.backgroundBlurDp
      prefs[Keys.BACKGROUND_DIM_ALPHA] = next.backgroundDimAlpha
      prefs[Keys.PANEL_OPACITY] = next.panelOpacity
      prefs[Keys.PANEL_SHADOW_DP] = next.panelShadowDp
    }
  }

  suspend fun resetDefaults() {
    context.settingsDataStore.edit { it.clear() }
  }
}
