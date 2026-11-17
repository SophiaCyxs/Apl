package com.example.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.ScrollSpeed
import com.example.ui.icons.LucideIcons
import com.example.ui.theme.CodeFontFamily
import com.example.ui.theme.LocalBloxPalette
import com.example.ui.theme.ThemePalette
import kotlin.math.roundToInt

private val IntervalPresets = listOf(16, 33, 50, 100, 250, 500)
private val TerminalColumnPresets = listOf(40, 52, 64)

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
  onResetDefaults: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalBloxPalette.current
  val previewFont = remember(settings.codeFont) { settings.codeFont.toComposeFontFamily() }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Appearance & Palette
    SettingsGroup(title = "THEME & PALETTE") {
      ToggleSettingRow(
        title = "Dark Mode",
        subtitle = if (settings.darkMode) "Dark background" else "Light background",
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

      ToggleSettingRow(
        title = "Syntax Highlighting",
        subtitle = "Lua 5.1 keywords, Roblox types, Executor APIs",
        checked = settings.syntaxHighlighting,
        onCheckedChange = onSyntaxHighlightingChange,
        testTag = "setting_syntax_highlight_switch",
      )
    }

    // Script Execution Intervals
    SettingsGroup(title = "EXECUTION INTERVALS") {
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
    }

    // Terminal & Scroll Speed
    SettingsGroup(title = "TERMINAL & SCROLL") {
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

    // Fonts & Sizing
    SettingsGroup(title = "FONTS & SIZE") {
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

      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(palette.terminalBg, RoundedCornerShape(4.dp))
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .padding(10.dp),
      ) {
        Text(
          text = "local f = Instance.new(\"Frame\", gethui())",
          style =
            TextStyle(
              fontFamily = previewFont,
              fontSize = settings.editorFontSizeSp.sp,
              color = palette.syntax.keyword,
            ),
        )
        Text(
          text = "[FRM #0042] Frame (${settings.terminalFontSizeSp.toInt()}sp)",
          style =
            TextStyle(
              fontFamily = previewFont,
              fontSize = settings.terminalFontSizeSp.sp,
              color = palette.textMuted,
            ),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Editor Font Size", style = MaterialTheme.typography.bodyMedium)
        Text(
          text = "${settings.editorFontSizeSp.roundToInt()} sp",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Slider(
        value = settings.editorFontSizeSp,
        onValueChange = { onEditorFontSizeChange(it.roundToInt().toFloat()) },
        valueRange = 10f..22f,
        modifier = Modifier.testTag("setting_editor_font_size_slider"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Terminal Font Size", style = MaterialTheme.typography.bodyMedium)
        Text(
          text = "${settings.terminalFontSizeSp.roundToInt()} sp",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Slider(
        value = settings.terminalFontSizeSp,
        onValueChange = { onTerminalFontSizeChange(it.roundToInt().toFloat()) },
        valueRange = 9f..20f,
        modifier = Modifier.testTag("setting_terminal_font_size_slider"),
      )

      ToggleSettingRow(
        title = "Line Numbers",
        subtitle = "Show gutter line numbers in editor",
        checked = settings.showLineNumbers,
        onCheckedChange = onShowLineNumbersChange,
        testTag = "setting_line_numbers_switch",
      )
    }

    // Dialect & Reset
    SettingsGroup(title = "COMPILER TARGET") {
      Text(
        text = "Target: Roblox Lua 5.1 + Executor Lua. Lua 5.2+ (goto, ::), Lua 5.3+ (//, &, |, <<, >>), and += are rejected.",
        style = MaterialTheme.typography.bodySmall,
        color = palette.textMuted,
      )
      OutlinedButton(
        onClick = onResetDefaults,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reset_settings_button"),
        shape = RoundedCornerShape(4.dp),
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

    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun SettingsGroup(
  title: String,
  content: @Composable () -> Unit,
) {
  val palette = LocalBloxPalette.current
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelSmall,
      color = palette.textMuted,
    )
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
          .border(1.dp, palette.border, RoundedCornerShape(4.dp))
          .padding(12.dp),
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
        .clickable { onCheckedChange(!checked) },
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
        .clip(RoundedCornerShape(4.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
    color =
      if (selected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.background,
    shape = RoundedCornerShape(4.dp),
    border =
      BorderStroke(
        1.dp,
        if (selected) MaterialTheme.colorScheme.primary else palette.border,
      ),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
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
