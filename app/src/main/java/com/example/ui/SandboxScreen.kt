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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.ui.icons.LucideIcons
import com.example.ui.theme.LocalBloxPalette

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
  val fontFamily = remember(settings.codeFont) { settings.codeFont.toComposeFontFamily() }
  var activeTab by remember { mutableStateOf(InspectorTab.BYTECODE) }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Compact single-line telemetry bar
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
          .border(1.dp, palette.border, RoundedCornerShape(4.dp))
          .padding(horizontal = 10.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "OPS ${inspectorState.opsExecuted}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontFamily),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = "UI ${inspectorState.uiTree.size}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontFamily),
        color = palette.textMuted,
      )
      Text(
        text = "DRAW ${inspectorState.drawingCount}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontFamily),
        color = palette.textMuted,
      )
      Text(
        text = if (isRunning) "ACTIVE" else "IDLE",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontFamily),
        color = if (isRunning) MaterialTheme.colorScheme.primary else palette.textMuted,
      )
    }

    // Inspector Tab Strip
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      InspectorTab.entries.forEach { tab ->
        val selected = tab == activeTab
        Surface(
          modifier =
            Modifier
              .weight(1f)
              .heightIn(min = 40.dp)
              .clip(RoundedCornerShape(4.dp))
              .clickable { activeTab = tab }
              .testTag("inspector_tab_${tab.name}"),
          color =
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(4.dp),
          border =
            BorderStroke(
              1.dp,
              if (selected) MaterialTheme.colorScheme.primary else palette.border,
            ),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text(
              text = tab.label,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
              color =
                if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    // Inspector Body
    Box(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .background(palette.terminalBg, RoundedCornerShape(4.dp))
          .border(1.dp, palette.border, RoundedCornerShape(4.dp))
          .padding(10.dp),
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
              Text(
                text = "> no bytecode compiled",
                style =
                  TextStyle(
                    fontFamily = fontFamily,
                    fontSize = settings.terminalFontSizeSp.sp,
                    color = palette.textMuted,
                  ),
              )
            }
          }
        }

        InspectorTab.UI_TREE -> {
          if (inspectorState.uiTree.isEmpty()) {
            Text(
              text = "> ui tree empty",
              style =
                TextStyle(
                  fontFamily = fontFamily,
                  fontSize = settings.terminalFontSizeSp.sp,
                  color = palette.textMuted,
                ),
            )
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              items(inspectorState.uiTree, key = { it.id }) { node ->
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .padding(start = (node.depth * 14).dp)
                      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                      .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                      .padding(horizontal = 8.dp, vertical = 6.dp),
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
            Text(
              text = "> getgenv() empty",
              style =
                TextStyle(
                  fontFamily = fontFamily,
                  fontSize = settings.terminalFontSizeSp.sp,
                  color = palette.textMuted,
                ),
            )
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              items(inspectorState.genvEntries) { (k, v) ->
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                      .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                      .padding(horizontal = 8.dp, vertical = 6.dp),
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
            Text(
              text = "> virtual fs empty",
              style =
                TextStyle(
                  fontFamily = fontFamily,
                  fontSize = settings.terminalFontSizeSp.sp,
                  color = palette.textMuted,
                ),
            )
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              items(inspectorState.virtualFiles, key = { it.first }) { (path, content) ->
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                      .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                      .padding(horizontal = 8.dp, vertical = 6.dp),
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
                    Spacer(Modifier.height(2.dp))
                    Text(
                      text = content,
                      style =
                        TextStyle(
                          fontFamily = fontFamily,
                          fontSize = (settings.terminalFontSizeSp - 1f).coerceAtLeast(9f).sp,
                          color = palette.textMuted,
                        ),
                      maxLines = 3,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  IconButton(
                    onClick = { onDeleteVirtualFile(path) },
                    modifier = Modifier.size(48.dp),
                  ) {
                    Icon(
                      imageVector = LucideIcons.Trash2,
                      contentDescription = "Delete virtual file",
                      tint = palette.textMuted,
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
