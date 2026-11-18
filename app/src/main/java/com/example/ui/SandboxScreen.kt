package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.ui.icons.LucideIcons
import com.example.ui.theme.LocalBloxPalette
import com.example.ui.theme.LocalBloxVisuals
import com.example.ui.theme.MovingGradientText
import com.example.ui.theme.bloxSurface

private enum class InspectorTab(val label: String) {
  BYTECODE("Bytecode"),
  UI_TREE("UI Tree"),
  GENV("getgenv()"),
  VFS("Virtual FS"),
}

@Composable
fun SandboxScreen(
  compileStatus: CompileStatus,
  inspectorState: SandboxInspectorState,
  isRunning: Boolean,
  settings: AppSettings,
  onDeleteVirtualFile: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalBloxPalette.current
  val visuals = LocalBloxVisuals.current
  val fontFamily = remember(settings.codeFont) { settings.codeFont.toComposeFontFamily() }
  var activeTab by remember { mutableStateOf(InspectorTab.BYTECODE) }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Modern KPI Telemetry Cards
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      KpiStatTile(
        label = "VM OPS",
        value = formatCompactOps(inspectorState.opsExecuted),
        icon = LucideIcons.Activity,
        fontFamily = fontFamily,
        highlight = isRunning,
        modifier = Modifier.weight(1f),
      )
      KpiStatTile(
        label = "UI TREE",
        value = inspectorState.uiTree.size.toString(),
        icon = LucideIcons.Layers,
        fontFamily = fontFamily,
        modifier = Modifier.weight(1f),
      )
      KpiStatTile(
        label = "DRAWING",
        value = inspectorState.drawingCount.toString(),
        icon = LucideIcons.Sparkles,
        fontFamily = fontFamily,
        modifier = Modifier.weight(1f),
      )
      KpiStatTile(
        label = "ENGINE",
        value = if (isRunning) "LIVE" else "IDLE",
        icon = LucideIcons.Cpu,
        fontFamily = fontFamily,
        highlight = isRunning,
        modifier = Modifier.weight(1f),
      )
    }

    // Segmented Pill Inspector Switcher
    Surface(
      color = MaterialTheme.colorScheme.surface.copy(alpha = visuals.effectivePanelAlpha() * 0.9f),
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, palette.border.copy(alpha = 0.7f)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        InspectorTab.entries.forEach { tab ->
          val selected = tab == activeTab
          Surface(
            modifier =
              Modifier
                .weight(1f)
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(11.dp))
                .clickable { activeTab = tab }
                .testTag("inspector_tab_${tab.name}"),
            color =
              if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            shape = RoundedCornerShape(11.dp),
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
              Text(
                text = tab.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color =
                  if (selected) MaterialTheme.colorScheme.onPrimary
                  else palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }

    // Inspector Body Card
    Box(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .bloxSurface(shape = RoundedCornerShape(16.dp), color = palette.terminalBg)
          .padding(12.dp),
    ) {
      when (activeTab) {
        InspectorTab.BYTECODE -> {
          when (compileStatus) {
            is CompileStatus.Success -> {
              val vScroll = rememberScrollState()
              val hScroll = rememberScrollState()
              Text(
                text = compileStatus.disassembly,
                style =
                  TextStyle(
                    fontFamily = fontFamily,
                    fontSize = settings.terminalFontSizeSp.sp,
                    lineHeight = (settings.terminalFontSizeSp * 1.35f).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                  ),
                modifier =
                  Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
                    .testTag("bytecode_disassembly"),
              )
            }
            is CompileStatus.Error -> {
              Text(
                text = "ERR ${compileStatus.stage} L${compileStatus.line}:${compileStatus.column}\n${compileStatus.message}",
                style =
                  TextStyle(
                    fontFamily = fontFamily,
                    fontSize = settings.terminalFontSizeSp.sp,
                    color = palette.error,
                  ),
              )
            }
            CompileStatus.Idle -> {
              EmptyInspectorHint(
                icon = LucideIcons.Braces,
                text = "Compile a Lua 5.1 script to inspect VM instructions",
                fontFamily = fontFamily,
                fontSize = settings.terminalFontSizeSp,
              )
            }
          }
        }

        InspectorTab.UI_TREE -> {
          if (inspectorState.uiTree.isEmpty()) {
            EmptyInspectorHint(
              icon = LucideIcons.Layers,
              text = "Run a GUI script to inspect live ScreenGui / Frame nodes",
              fontFamily = fontFamily,
              fontSize = settings.terminalFontSizeSp,
            )
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
              items(inspectorState.uiTree, key = { it.id }) { node ->
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .padding(start = (node.depth * 14).dp)
                      .border(1.dp, palette.border.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                      .padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = LucideIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = palette.textMuted,
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "${node.name} <${node.className}>",
                      style =
                        TextStyle(
                          fontFamily = fontFamily,
                          fontSize = settings.terminalFontSizeSp.sp,
                          fontWeight = FontWeight.SemiBold,
                          color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    if (node.summary.isNotEmpty()) {
                      Text(
                        text = node.summary,
                        style =
                          TextStyle(
                            fontFamily = fontFamily,
                            fontSize = (settings.terminalFontSizeSp - 1f).coerceAtLeast(9f).sp,
                            color = palette.textMuted,
                          ),
                      )
                    }
                  }
                }
              }
            }
          }
        }

        InspectorTab.GENV -> {
          if (inspectorState.genvEntries.isEmpty()) {
            EmptyInspectorHint(
              icon = LucideIcons.Zap,
              text = "getgenv() table is empty",
              fontFamily = fontFamily,
              fontSize = settings.terminalFontSizeSp,
            )
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
              items(inspectorState.genvEntries) { (k, v) ->
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .border(1.dp, palette.border.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                      .padding(horizontal = 10.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = k,
                    style =
                      TextStyle(
                        fontFamily = fontFamily,
                        fontSize = settings.terminalFontSizeSp.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                      ),
                  )
                  Spacer(Modifier.width(12.dp))
                  Text(
                    text = v,
                    style =
                      TextStyle(
                        fontFamily = fontFamily,
                        fontSize = settings.terminalFontSizeSp.sp,
                        color = palette.textMuted,
                      ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }

        InspectorTab.VFS -> {
          if (inspectorState.virtualFiles.isEmpty()) {
            EmptyInspectorHint(
              icon = LucideIcons.FileCode2,
              text = "No virtual files written yet (writefile / appendfile)",
              fontFamily = fontFamily,
              fontSize = settings.terminalFontSizeSp,
            )
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
              items(inspectorState.virtualFiles, key = { it.first }) { (path, content) ->
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .border(1.dp, palette.border.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                      .padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = path,
                      style =
                        TextStyle(
                          fontFamily = fontFamily,
                          fontSize = settings.terminalFontSizeSp.sp,
                          fontWeight = FontWeight.SemiBold,
                          color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    Text(
                      text = content,
                      style =
                        TextStyle(
                          fontFamily = fontFamily,
                          fontSize = (settings.terminalFontSizeSp - 1f).coerceAtLeast(9f).sp,
                          color = palette.textMuted,
                        ),
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  IconButton(
                    onClick = { onDeleteVirtualFile(path) },
                    modifier = Modifier.size(48.dp).testTag("delete_vfs_$path"),
                  ) {
                    Icon(
                      imageVector = LucideIcons.Trash2,
                      contentDescription = "Delete $path",
                      tint = palette.error,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun KpiStatTile(
  label: String,
  value: String,
  icon: ImageVector,
  fontFamily: FontFamily,
  modifier: Modifier = Modifier,
  highlight: Boolean = false,
) {
  val palette = LocalBloxPalette.current
  val visuals = LocalBloxVisuals.current
  Column(
    modifier =
      modifier
        .bloxSurface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          borderColor = if (highlight) MaterialTheme.colorScheme.primary else palette.border,
        )
        .padding(horizontal = 9.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (highlight) MaterialTheme.colorScheme.primary else palette.textMuted,
        modifier = Modifier.size(11.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = palette.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    MovingGradientText(
      text = value,
      style =
        TextStyle(
          fontFamily = fontFamily,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        ),
      maxLines = 1,
    )
  }
}

@Composable
private fun EmptyInspectorHint(
  icon: ImageVector,
  text: String,
  fontFamily: FontFamily,
  fontSize: Float,
) {
  val palette = LocalBloxPalette.current
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(16.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = palette.textMuted,
        modifier = Modifier.size(26.dp),
      )
      Text(
        text = text,
        style =
          TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            color = palette.textMuted,
          ),
      )
    }
  }
}

private fun formatCompactOps(ops: Long): String =
  when {
    ops >= 1_000_000 -> "${"%.1f".format(ops / 1_000_000.0)}M"
    ops >= 1_000 -> "${"%.1f".format(ops / 1_000.0)}K"
    else -> ops.toString()
  }
