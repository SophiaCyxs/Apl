package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import com.example.data.AppSettings
import com.example.data.ScrollSpeed
import com.example.ui.icons.LucideIcons
import com.example.ui.theme.BackgroundMode
import com.example.ui.theme.CodeFontFamily
import com.example.ui.theme.LocalBloxPalette
import com.example.ui.theme.LocalBloxVisuals
import com.example.ui.theme.LocalGradientPhaseState
import com.example.ui.theme.MovingGradientText
import com.example.ui.theme.SyntaxGradientPreset
import com.example.ui.theme.ThemePalette
import com.example.ui.theme.bloxSurface
import com.example.ui.theme.buildMovingGradientBrush
import kotlin.math.roundToInt

private val IntervalPresets = listOf(16, 33, 50, 100, 250, 500)
private val TerminalColumnPresets = listOf(40, 52, 64)

private val SyntaxColorSwatches: List<Pair<String, Long>> =
  listOf(
    "Default" to 0L,
    "Pure White" to 0xFFFFFFFFL,
    "Silver" to 0xFFD4D4D8L,
    "Slate" to 0xFF94A3B8L,
    "Charcoal" to 0xFF18181BL,
    "Cyan" to 0xFF38BDF8L,
    "Mint" to 0xFF34D399L,
    "Lime" to 0xFF4ADE80L,
    "Gold" to 0xFFFACC15L,
    "Coral" to 0xFFF87171L,
    "Violet" to 0xFFA78BFAL,
    "Rose" to 0xFFF472B6L,
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  settings: AppSettings,
  onDarkModeChange: (Boolean) -> Unit,
  onThemePaletteChange: (ThemePalette) -> Unit,
  onExecutionIntervalChange: (Int) -> Unit,
  onScrollSpeedChange: (ScrollSpeed) -> Unit,
  onCodeFontChange: (CodeFontFamily) -> Unit,
  onEditorFontSizeChange: (Float) -> Unit,
  onTerminalFontSizeChange: (Float) -> Unit,
  onShowLineNumbersChange: (Boolean) -> Unit,
  onSyntaxHighlightingChange: (Boolean) -> Unit,
  onAutoScrollChange: (Boolean) -> Unit,
  onTerminalColumnsChange: (Int) -> Unit,
  onShowFrameDiffLogsChange: (Boolean) -> Unit,
  onMaxOpsChange: (Int) -> Unit,
  onMonochromeGuiPreviewChange: (Boolean) -> Unit,
  onEnableHttpGetChange: (Boolean) -> Unit,
  onSyntaxGradientEnabledChange: (Boolean) -> Unit,
  onSyntaxGradientMovingChange: (Boolean) -> Unit,
  onFontGradientMovingChange: (Boolean) -> Unit,
  onGradientPresetChange: (SyntaxGradientPreset) -> Unit,
  onGradientSpeedChange: (Float) -> Unit,
  onSyntaxTokenColorChange: (String, Long) -> Unit,
  onResetSyntaxColors: () -> Unit,
  onBackgroundModeChange: (BackgroundMode) -> Unit,
  onPickBackgroundUri: (Uri) -> Unit,
  onClearCustomBackground: () -> Unit,
  onBackgroundVideoMutedChange: (Boolean) -> Unit,
  onGalaxySpeedChange: (Float) -> Unit,
  onGalaxyStarDensityChange: (Float) -> Unit,
  onBackgroundBlurChange: (Float) -> Unit,
  onBackgroundDimChange: (Float) -> Unit,
  onPanelOpacityChange: (Float) -> Unit,
  onPanelShadowChange: (Float) -> Unit,
  onResetDefaults: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalBloxPalette.current
  val visuals = LocalBloxVisuals.current
  val previewFont = remember(settings.codeFont) { settings.codeFont.toComposeFontFamily() }

  val mediaPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        onPickBackgroundUri(uri)
      }
    }

  var editingToken by remember { mutableStateOf<Pair<String, String>?>(null) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Background, MP4 Video & Glass Surface FX
    SettingsGroup(title = "BACKDROP, MP4 VIDEO & GLASS FX", icon = LucideIcons.Film) {
      Text(
        text = "Workspace Backdrop Mode",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        BackgroundMode.entries.forEach { mode ->
          PresetChip(
            label = mode.label,
            selected = settings.backgroundMode == mode,
            onClick = { onBackgroundModeChange(mode) },
            testTag = "bg_mode_${mode.name}",
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = {
            mediaPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("upload_bg_button"),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, palette.border),
        ) {
          Icon(imageVector = LucideIcons.ImageIcon, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(if (settings.customBackgroundPath.isNotEmpty()) "Change Photo" else "Upload Photo")
        }

        OutlinedButton(
          onClick = {
            mediaPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
          },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("upload_video_bg_button"),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, palette.border),
        ) {
          Icon(imageVector = LucideIcons.Film, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(if (settings.customVideoPath.isNotEmpty()) "Change MP4" else "Upload MP4")
        }
      }

      if (settings.customBackgroundPath.isNotEmpty() || settings.customVideoPath.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val activeMediaStatus =
            when {
              settings.backgroundMode == BackgroundMode.CUSTOM_VIDEO && settings.customVideoPath.isNotEmpty() ->
                "Active: Looping MP4 Video"
              settings.backgroundMode == BackgroundMode.CUSTOM_IMAGE && settings.customBackgroundPath.isNotEmpty() ->
                "Active: Custom Image"
              else -> "Saved custom media ready"
            }
          Text(
            text = activeMediaStatus,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            modifier = Modifier.weight(1f),
          )
          OutlinedButton(
            onClick = onClearCustomBackground,
            modifier = Modifier.heightIn(min = 42.dp).testTag("clear_bg_button"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, palette.border),
          ) {
            Icon(imageVector = LucideIcons.Trash2, contentDescription = "Remove media", modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reset to Galaxy", style = MaterialTheme.typography.labelSmall)
          }
        }
      }

      if (settings.backgroundMode == BackgroundMode.CUSTOM_VIDEO || settings.customVideoPath.isNotEmpty()) {
        ToggleSettingRow(
          title = "Mute MP4 Video Audio",
          subtitle = "Play background video silently while coding",
          checked = settings.backgroundVideoMuted,
          onCheckedChange = onBackgroundVideoMutedChange,
          testTag = "setting_video_muted_switch",
        )
      }

      if (settings.backgroundMode == BackgroundMode.GALAXY) {
        SliderRow(
          label = "Galaxy Drift Speed",
          valueText = "${"%.1f".format(settings.galaxySpeed)}x",
          value = settings.galaxySpeed,
          range = 0.2f..3f,
          onValueChange = onGalaxySpeedChange,
          testTag = "setting_galaxy_speed_slider",
        )
        SliderRow(
          label = "Starfield Density",
          valueText = "${(settings.galaxyStarDensity * 100).roundToInt()}%",
          value = settings.galaxyStarDensity,
          range = 0.25f..2f,
          onValueChange = onGalaxyStarDensityChange,
          testTag = "setting_galaxy_density_slider",
        )
      }

      if (settings.backgroundMode != BackgroundMode.SOLID) {
        SliderRow(
          label = "Backdrop Blur",
          valueText = "${settings.backgroundBlurDp.roundToInt()} dp",
          value = settings.backgroundBlurDp,
          range = 0f..24f,
          onValueChange = { onBackgroundBlurChange(it.roundToInt().toFloat()) },
          testTag = "setting_bg_blur_slider",
        )
        SliderRow(
          label = "Backdrop Dim",
          valueText = "${(settings.backgroundDimAlpha * 100).roundToInt()}%",
          value = settings.backgroundDimAlpha,
          range = 0f..0.85f,
          onValueChange = onBackgroundDimChange,
          testTag = "setting_bg_dim_slider",
        )
        SliderRow(
          label = "Panel Transparency",
          valueText = "${(settings.panelOpacity * 100).roundToInt()}%",
          value = settings.panelOpacity,
          range = 0.35f..1f,
          onValueChange = onPanelOpacityChange,
          testTag = "setting_panel_opacity_slider",
        )
      }

      SliderRow(
        label = "Panel Shadow Elevation",
        valueText = "${settings.panelShadowDp.roundToInt()} dp",
        value = settings.panelShadowDp,
        range = 0f..24f,
        onValueChange = { onPanelShadowChange(it.roundToInt().toFloat()) },
        testTag = "setting_panel_shadow_slider",
      )
    }

    // 2. Theme & Palette
    SettingsGroup(title = "THEME & PALETTE", icon = LucideIcons.Palette) {
      ToggleSettingRow(
        title = "Dark Mode",
        subtitle = if (settings.darkMode) "Dark studio theme" else "Light paper theme",
        checked = settings.darkMode,
        onCheckedChange = onDarkModeChange,
        testTag = "setting_dark_mode_switch",
      )

      Text(
        text = "Color Palette",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        ThemePalette.entries.forEach { pal ->
          PresetChip(
            label = pal.label,
            selected = settings.themePalette == pal,
            onClick = { onThemePaletteChange(pal) },
            testTag = "palette_option_${pal.name}",
          )
        }
      }

      ToggleSettingRow(
        title = "Monochrome GUI Canvas",
        subtitle = "Desaturate Roblox Color3 & UIGradient inside preview",
        checked = settings.monochromeGuiPreview,
        onCheckedChange = onMonochromeGuiPreviewChange,
        testTag = "setting_monochrome_gui_switch",
      )
    }

    // 3. Syntax Color Changer & Moving Gradients
    SettingsGroup(title = "SYNTAX COLORS & GRADIENTS", icon = LucideIcons.Sparkles) {
      ToggleSettingRow(
        title = "Syntax Highlighting",
        subtitle = "Lua 5.1 keywords, Roblox types, Executor APIs",
        checked = settings.syntaxHighlighting,
        onCheckedChange = onSyntaxHighlightingChange,
        testTag = "setting_syntax_highlight_switch",
      )

      ToggleSettingRow(
        title = "Gradient Syntax Colors",
        subtitle = "Render Lua keywords, types & strings with multi-stop gradients",
        checked = settings.syntaxGradientEnabled,
        onCheckedChange = onSyntaxGradientEnabledChange,
        testTag = "setting_syntax_gradient_switch",
      )

      ToggleSettingRow(
        title = "Moving Gradient Flow",
        subtitle = "Continuously animate gradient sweeps across syntax tokens",
        checked = settings.syntaxGradientMoving,
        onCheckedChange = onSyntaxGradientMovingChange,
        testTag = "setting_syntax_gradient_moving_switch",
      )

      ToggleSettingRow(
        title = "Moving Font Gradient",
        subtitle = "Apply animated gradient brush across IDE labels & console output",
        checked = settings.fontGradientMoving,
        onCheckedChange = onFontGradientMovingChange,
        testTag = "setting_font_gradient_moving_switch",
      )

      Text(
        text = "Gradient Spectrum",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        SyntaxGradientPreset.entries.forEach { gp ->
          val selected = settings.gradientPreset == gp
          Surface(
            modifier =
              Modifier
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onGradientPresetChange(gp) }
                .testTag("gradient_preset_${gp.name}"),
            color =
              if (selected) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else palette.border.copy(alpha = 0.7f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(gp.colors))
                    .border(1.dp, palette.border, CircleShape),
              )
              Text(
                text = gp.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      SliderRow(
        label = "Gradient Motion Speed",
        valueText = "${"%.1f".format(settings.gradientSpeed)}x",
        value = settings.gradientSpeed,
        range = 0.2f..3f,
        onValueChange = onGradientSpeedChange,
        testTag = "setting_gradient_speed_slider",
      )

      LiveSyntaxGradientPreview(
        previewFont = previewFont,
        editorFontSizeSp = settings.editorFontSizeSp,
        syntaxHighlighting = settings.syntaxHighlighting,
      )

      Text(
        text = "Token Color Changer (tap to customize)",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      val tokenRows =
        listOf(
          Triple("keyword", "Keywords (local/function)", palette.syntax.keyword),
          Triple("type", "Roblox Types (Instance/UDim2)", palette.syntax.robloxType),
          Triple("executor", "Executor APIs (gethui/syn)", palette.syntax.executorApi),
          Triple("builtin", "Builtins (print/table)", palette.syntax.builtin),
          Triple("string", "Strings (\"text\")", palette.syntax.stringLiteral),
          Triple("number", "Numbers (0xFF / 42)", palette.syntax.numberLiteral),
          Triple("comment", "Comments (-- note)", palette.syntax.comment),
          Triple("operator", "Operators (+ = ..)", palette.syntax.operator),
        )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        tokenRows.forEach { (key, label, col) ->
          Surface(
            modifier =
              Modifier
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { editingToken = key to label }
                .testTag("syntax_token_btn_$key"),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.7f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(col)
                    .border(1.dp, palette.border, CircleShape),
              )
              Text(
                text = label.substringBefore(" ("),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      OutlinedButton(
        onClick = onResetSyntaxColors,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("reset_syntax_colors_btn"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
      ) {
        Text("Reset Syntax Token Colors", style = MaterialTheme.typography.labelMedium)
      }
    }

    // 4. Script Execution Intervals & Network
    SettingsGroup(title = "EXECUTION & SANDBOX", icon = LucideIcons.Cpu) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Frame & Task Interval",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = "${settings.executionIntervalMs} ms",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Slider(
        value = settings.executionIntervalMs.toFloat(),
        onValueChange = { onExecutionIntervalChange(it.roundToInt()) },
        valueRange = 16f..1000f,
        modifier = Modifier.testTag("setting_execution_interval_slider"),
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IntervalPresets.forEach { preset ->
          PresetChip(
            label = "${preset}ms",
            selected = settings.executionIntervalMs == preset,
            onClick = { onExecutionIntervalChange(preset) },
            testTag = "interval_preset_$preset",
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Loop Instruction Guard", style = MaterialTheme.typography.bodyMedium)
        Text(
          text = "${settings.maxOpsPerExecution / 1000}k ops",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Slider(
        value = settings.maxOpsPerExecution.toFloat(),
        onValueChange = { onMaxOpsChange((it / 50_000f).roundToInt() * 50_000) },
        valueRange = 100_000f..2_000_000f,
        modifier = Modifier.testTag("setting_max_ops_slider"),
      )

      ToggleSettingRow(
        title = "Allow game:HttpGet / request()",
        subtitle = "Fetch remote UI libraries like Rayfield, Orion, or Kavo over HTTPS",
        checked = settings.enableHttpGet,
        onCheckedChange = onEnableHttpGetChange,
        testTag = "setting_enable_httpget_switch",
      )
    }

    // 5. Terminal & Scroll Speed
    SettingsGroup(title = "TERMINAL & SCROLL", icon = LucideIcons.Terminal) {
      Text(
        text = "Scroll Speed",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        ScrollSpeed.entries.forEach { speed ->
          PresetChip(
            label = "${speed.label} (${speed.description})",
            selected = settings.scrollSpeed == speed,
            onClick = { onScrollSpeedChange(speed) },
            testTag = "scroll_speed_${speed.name}",
          )
        }
      }

      ToggleSettingRow(
        title = "Auto-Scroll Terminal",
        subtitle = "Follow incoming output lines",
        checked = settings.autoScrollTerminal,
        onCheckedChange = onAutoScrollChange,
        testTag = "setting_autoscroll_switch",
      )

      ToggleSettingRow(
        title = "Frame Diff Logs",
        subtitle = "Print [FRM] updates on property changes",
        checked = settings.showFrameDiffLogs,
        onCheckedChange = onShowFrameDiffLogsChange,
        testTag = "setting_frame_diff_switch",
      )

      Text(
        text = "Frame Buffer Width",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TerminalColumnPresets.forEach { cols ->
          PresetChip(
            label = "$cols cols",
            selected = settings.terminalColumns == cols,
            onClick = { onTerminalColumnsChange(cols) },
            testTag = "terminal_cols_$cols",
          )
        }
      }
    }

    // 6. Fonts & Sizing
    SettingsGroup(title = "FONTS & SIZE", icon = LucideIcons.TypeIcon) {
      Text(
        text = "Font Family",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        CodeFontFamily.entries.forEach { font ->
          PresetChip(
            label = font.displayName,
            selected = settings.codeFont == font,
            onClick = { onCodeFontChange(font) },
            testTag = "font_option_${font.name}",
          )
        }
      }

      LiveFontGradientPreview(
        previewFont = previewFont,
        editorFontSizeSp = settings.editorFontSizeSp,
        terminalFontSizeSp = settings.terminalFontSizeSp,
      )

      SliderRow(
        label = "Editor Font Size",
        valueText = "${settings.editorFontSizeSp.roundToInt()} sp",
        value = settings.editorFontSizeSp,
        range = 10f..22f,
        onValueChange = { onEditorFontSizeChange(it.roundToInt().toFloat()) },
        testTag = "setting_editor_font_size_slider",
      )

      SliderRow(
        label = "Terminal Font Size",
        valueText = "${settings.terminalFontSizeSp.roundToInt()} sp",
        value = settings.terminalFontSizeSp,
        range = 9f..20f,
        onValueChange = { onTerminalFontSizeChange(it.roundToInt().toFloat()) },
        testTag = "setting_terminal_font_size_slider",
      )

      ToggleSettingRow(
        title = "Line Numbers",
        subtitle = "Show gutter line numbers in editor",
        checked = settings.showLineNumbers,
        onCheckedChange = onShowLineNumbersChange,
        testTag = "setting_line_numbers_switch",
      )
    }

    // 7. Dialect & Reset
    SettingsGroup(title = "COMPILER TARGET", icon = LucideIcons.ShieldCheck) {
      Text(
        text = "Target: Roblox Lua 5.1 + Executor Lua + LocalScript/ModuleScript. Lua 5.2+ (goto), Lua 5.3+ (//, &, |), and += are rejected.",
        style = MaterialTheme.typography.bodySmall,
        color = palette.textMuted,
      )
      OutlinedButton(
        onClick = onResetDefaults,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reset_settings_button"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
      ) {
        Icon(
          imageVector = LucideIcons.RotateCcw,
          contentDescription = null,
          modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Reset Defaults")
      }
    }

    Spacer(Modifier.height(14.dp))
  }

  val targetToken = editingToken
  if (targetToken != null) {
    AlertDialog(
      onDismissRequest = { editingToken = null },
      shape = RoundedCornerShape(18.dp),
      title = { Text("Color · ${targetToken.second}") },
      text = {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SyntaxColorSwatches.forEach { (name, argb) ->
            Surface(
              modifier =
                Modifier
                  .heightIn(min = 38.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .clickable {
                    onSyntaxTokenColorChange(targetToken.first, argb)
                    editingToken = null
                  },
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, palette.border),
              color = MaterialTheme.colorScheme.surface,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box(
                  modifier =
                    Modifier
                      .size(14.dp)
                      .clip(CircleShape)
                      .background(if (argb == 0L) palette.accent else Color(argb))
                      .border(1.dp, palette.border, CircleShape),
                )
                Text(name, style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { editingToken = null }, shape = RoundedCornerShape(10.dp)) { Text("Done") }
      },
      dismissButton = {
        TextButton(onClick = {
          onSyntaxTokenColorChange(targetToken.first, 0L)
          editingToken = null
        }) {
          Text("Reset Token")
        }
      },
    )
  }
}

@Composable
private fun SliderRow(
  label: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
  testTag: String,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label, style = MaterialTheme.typography.bodyMedium)
      Text(
        text = valueText,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    Slider(
      value = value.coerceIn(range.start, range.endInclusive),
      onValueChange = onValueChange,
      valueRange = range,
      modifier = Modifier.testTag(testTag),
    )
  }
}

@Composable
private fun SettingsGroup(
  title: String,
  icon: ImageVector,
  content: @Composable () -> Unit,
) {
  val palette = LocalBloxPalette.current
  val visuals = LocalBloxVisuals.current
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
      modifier = Modifier.padding(horizontal = 2.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = visuals.effectivePanelAlpha()),
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.6f)),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(5.dp).size(13.dp),
        )
      }
      MovingGradientText(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = palette.textMuted,
      )
    }
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .bloxSurface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface)
          .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      content()
    }
  }
}

@Composable
private fun ToggleSettingRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  val palette = LocalBloxPalette.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(10.dp))
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
      Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}

@Composable
private fun PresetChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val palette = LocalBloxPalette.current
  Surface(
    modifier =
      Modifier
        .heightIn(min = 38.dp)
        .clip(RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
    color =
      if (selected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
    shape = RoundedCornerShape(10.dp),
    border =
      BorderStroke(
        1.dp,
        if (selected) MaterialTheme.colorScheme.primary else palette.border.copy(alpha = 0.7f),
      ),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun LiveSyntaxGradientPreview(
  previewFont: FontFamily,
  editorFontSizeSp: Float,
  syntaxHighlighting: Boolean,
) {
  val palette = LocalBloxPalette.current
  val visuals = LocalBloxVisuals.current
  val phase =
    if (visuals.syntaxGradientEnabled && visuals.syntaxGradientMoving) {
      LocalGradientPhaseState.current.value
    } else {
      0f
    }
  val liveHighlight =
    remember(
      palette.syntax,
      syntaxHighlighting,
      visuals.syntaxGradientEnabled,
      visuals.gradientColors,
      phase,
    ) {
      LuaSyntaxHighlighter.highlight(
        source = "local ui = Instance.new(\"Frame\", gethui()) -- \"live\"",
        colors = palette.syntax,
        enabled = syntaxHighlighting,
        gradientEnabled = visuals.syntaxGradientEnabled,
        gradientColors = visuals.gradientColors,
        gradientPhase = phase,
      )
    }
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.terminalBg.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
        .border(1.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        .padding(12.dp),
  ) {
    Text(
      text = liveHighlight,
      style =
        TextStyle(
          fontFamily = previewFont,
          fontSize = editorFontSizeSp.sp,
          color = palette.syntax.defaultText,
        ),
    )
  }
}

@Composable
private fun LiveFontGradientPreview(
  previewFont: FontFamily,
  editorFontSizeSp: Float,
  terminalFontSizeSp: Float,
) {
  val palette = LocalBloxPalette.current
  val visuals = LocalBloxVisuals.current
  val phase = if (visuals.fontGradientMoving) LocalGradientPhaseState.current.value else 0f
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.terminalBg, RoundedCornerShape(12.dp))
        .border(1.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        .padding(12.dp),
  ) {
    Text(
      text = "local f = Instance.new(\"Frame\", gethui())",
      style =
        TextStyle(
          fontFamily = previewFont,
          fontSize = editorFontSizeSp.sp,
          color = palette.syntax.keyword,
        ).let {
          if (visuals.fontGradientMoving) it.copy(brush = buildMovingGradientBrush(visuals.gradientColors, phase))
          else it
        },
    )
    MovingGradientText(
      text = "[FRM #0042] Frame (${terminalFontSizeSp.toInt()}sp)",
      style =
        TextStyle(
          fontFamily = previewFont,
          fontSize = terminalFontSizeSp.sp,
          color = palette.textMuted,
        ),
    )
  }
}
