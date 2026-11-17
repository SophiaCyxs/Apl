package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.ScrollSpeed
import com.example.lua.GuiRenderScene
import com.example.lua.LogLevel
import com.example.lua.TerminalCell
import com.example.lua.TerminalFrameSnapshot
import com.example.lua.TerminalLogEntry
import com.example.ui.icons.LucideIcons
import com.example.ui.theme.LocalBloxPalette
import kotlinx.coroutines.delay

@Composable
fun TerminalScreen(
  guiScene: GuiRenderScene?,
  frameSnapshot: TerminalFrameSnapshot?,
  logs: List<TerminalLogEntry>,
  isRunning: Boolean,
  settings: AppSettings,
  onViewportSizeDp: (Float, Float) -> Unit,
  onTriggerButton: (Int) -> Unit,
  onDragCanvasTarget: (Int, Double, Double, Double, Double) -> Unit,
  onRun: () -> Unit,
  onStop: () -> Unit,
  onToggleAutoScroll: (Boolean) -> Unit,
  onCopyLogs: () -> Unit,
  onClearLogs: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalBloxPalette.current
  val fontFamily = remember(settings.codeFont) { settings.codeFont.toComposeFontFamily() }
  val termFontSize = settings.terminalFontSizeSp.sp
  val termLineHeight = (settings.terminalFontSizeSp * 1.25f).sp
  val listState = rememberLazyListState()
  var ansiMode by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(logs.size, settings.autoScrollTerminal, settings.scrollSpeed) {
    if (settings.autoScrollTerminal && logs.isNotEmpty()) {
      if (settings.scrollSpeed == ScrollSpeed.INSTANT) {
        listState.scrollToItem(logs.lastIndex)
      } else {
        delay(settings.scrollSpeed.delayMs)
        listState.animateScrollToItem(logs.lastIndex)
      }
    }
  }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // GUI Drawing Processor / Frame Viewport Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = if (ansiMode) "ANSI GRID" else "GUI CANVAS",
          style = MaterialTheme.typography.labelMedium,
          color = palette.textMuted,
        )
        val fNum = guiScene?.frameNumber ?: frameSnapshot?.frameNumber ?: 0L
        val gCnt = guiScene?.activeGuiCount ?: frameSnapshot?.activeGuiCount ?: 0
        Text(
          text = "#$fNum · ${settings.executionIntervalMs}ms · $gCnt GUI",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontFamily),
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Surface(
          modifier =
            Modifier
              .heightIn(min = 34.dp)
              .clip(RoundedCornerShape(4.dp))
              .clickable { ansiMode = false }
              .testTag("preview_mode_canvas"),
          color =
            if (!ansiMode) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(4.dp),
          border = BorderStroke(1.dp, if (!ansiMode) MaterialTheme.colorScheme.primary else palette.border),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(
              text = "GUI",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (!ansiMode) FontWeight.SemiBold else FontWeight.Normal,
              color = if (!ansiMode) MaterialTheme.colorScheme.onPrimary else palette.textMuted,
            )
          }
        }
        Surface(
          modifier =
            Modifier
              .heightIn(min = 34.dp)
              .clip(RoundedCornerShape(4.dp))
              .clickable { ansiMode = true }
              .testTag("preview_mode_ansi"),
          color =
            if (ansiMode) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(4.dp),
          border = BorderStroke(1.dp, if (ansiMode) MaterialTheme.colorScheme.primary else palette.border),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(
              text = "ANSI",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (ansiMode) FontWeight.SemiBold else FontWeight.Normal,
              color = if (ansiMode) MaterialTheme.colorScheme.onPrimary else palette.textMuted,
            )
          }
        }
        IconButton(
          onClick = if (isRunning) onStop else onRun,
          modifier = Modifier.size(48.dp).testTag("terminal_play_stop"),
        ) {
          Icon(
            imageVector = if (isRunning) LucideIcons.Square else LucideIcons.Play,
            contentDescription = if (isRunning) "Stop script" else "Run script",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }

    // Real-Time Interactive GUI Canvas OR ANSI Terminal Grid
    if (!ansiMode) {
      Column(
        modifier = Modifier.fillMaxWidth().testTag("terminal_frame_viewport"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        InteractiveGuiCanvas(
          scene = guiScene,
          isRunning = isRunning,
          codeFont = fontFamily,
          onViewportSizeDp = onViewportSizeDp,
          onTapTarget = onTriggerButton,
          onDragTarget = onDragCanvasTarget,
        )
        val targets = guiScene?.hitTargets.orEmpty()
        val clickableCount = targets.count { it.isClickable }
        val dragCount = targets.count { it.isDraggable || it.isScrollable }
        if (clickableCount > 0 || dragCount > 0) {
          Text(
            text =
              buildString {
                if (clickableCount > 0) append("$clickableCount clickable")
                if (clickableCount > 0 && dragCount > 0) append(" · ")
                if (dragCount > 0) append("$dragCount drag/scroll")
                append(" · interact directly on canvas")
              },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontFamily),
            color = palette.textMuted,
          )
        }
      }
    } else {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(palette.terminalBg, RoundedCornerShape(4.dp))
            .border(
              1.dp,
              if (isRunning) MaterialTheme.colorScheme.primary else palette.border,
              RoundedCornerShape(4.dp),
            )
            .padding(8.dp)
            .testTag("terminal_frame_viewport"),
      ) {
        val snapshot = frameSnapshot
        if (snapshot == null || (snapshot.frameNumber == 0L && snapshot.activeGuiCount == 0)) {
          Text(
            text = "> frame buffer idle · run ScreenGui / Frame / Drawing script",
            style =
              TextStyle(
                fontFamily = fontFamily,
                fontSize = termFontSize,
                color = palette.textMuted,
              ),
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 6.dp),
          )
        } else {
          val hScroll = rememberScrollState()
          Column(
            modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll),
          ) {
            snapshot.grid.forEach { rowCells ->
              val annotated = remember(rowCells) { buildTerminalRowAnnotatedString(rowCells) }
              Text(
                text = annotated,
                style =
                  TextStyle(
                    fontFamily = fontFamily,
                    fontSize = termFontSize,
                    lineHeight = termLineHeight,
                  ),
                softWrap = false,
              )
            }
          }
        }
      }

      // Interactive button chips in ANSI mode
      val buttons = frameSnapshot?.interactiveButtons.orEmpty()
      if (buttons.isNotEmpty()) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          contentPadding = PaddingValues(vertical = 2.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          items(buttons, key = { it.instanceId }) { btn ->
            Surface(
              modifier =
                Modifier
                  .heightIn(min = 38.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .clickable { onTriggerButton(btn.instanceId) }
                  .testTag("interactive_btn_${btn.name}"),
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(4.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = LucideIcons.MousePointerClick,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = btn.text,
                  style = MaterialTheme.typography.labelMedium.copy(fontFamily = fontFamily),
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }
      }
    }

    // Console Stream Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "STDOUT (${logs.size})",
        style = MaterialTheme.typography.labelMedium,
        color = palette.textMuted,
      )

      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          modifier =
            Modifier
              .heightIn(min = 34.dp)
              .clip(RoundedCornerShape(4.dp))
              .clickable { onToggleAutoScroll(!settings.autoScrollTerminal) }
              .testTag("toggle_autoscroll_chip"),
          color =
            if (settings.autoScrollTerminal) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(4.dp),
          border = BorderStroke(1.dp, palette.border),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = LucideIcons.ArrowDownToLine,
              contentDescription = "Auto scroll",
              modifier = Modifier.size(13.dp),
              tint =
                if (settings.autoScrollTerminal) MaterialTheme.colorScheme.onPrimaryContainer
                else palette.textMuted,
            )
            Text(
              text = settings.scrollSpeed.label,
              style = MaterialTheme.typography.labelSmall,
              color =
                if (settings.autoScrollTerminal) MaterialTheme.colorScheme.onPrimaryContainer
                else palette.textMuted,
            )
          }
        }
        IconButton(
          onClick = onCopyLogs,
          modifier = Modifier.size(48.dp).testTag("copy_logs_button"),
        ) {
          Icon(
            imageVector = LucideIcons.Copy,
            contentDescription = "Copy terminal logs",
            modifier = Modifier.size(16.dp),
            tint = palette.textMuted,
          )
        }
        IconButton(
          onClick = onClearLogs,
          modifier = Modifier.size(48.dp).testTag("clear_logs_button"),
        ) {
          Icon(
            imageVector = LucideIcons.Trash2,
            contentDescription = "Clear terminal logs",
            modifier = Modifier.size(16.dp),
            tint = palette.textMuted,
          )
        }
      }
    }

    // Terminal Output Console
    Box(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .background(palette.terminalBg, RoundedCornerShape(4.dp))
          .border(1.dp, palette.border, RoundedCornerShape(4.dp))
          .padding(horizontal = 8.dp, vertical = 6.dp)
          .testTag("terminal_log_list"),
    ) {
      if (logs.isEmpty()) {
        Text(
          text = "> ready",
          style =
            TextStyle(
              fontFamily = fontFamily,
              fontSize = termFontSize,
              color = palette.textMuted,
            ),
        )
      } else {
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(2.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(logs, key = { it.id }) { entry ->
            val tagColor =
              if (palette.isMonochrome) {
                when (entry.level) {
                  LogLevel.PRINT, LogLevel.ERROR -> MaterialTheme.colorScheme.onBackground
                  LogLevel.FRAME, LogLevel.EXEC -> MaterialTheme.colorScheme.primary
                  LogLevel.WARN, LogLevel.SYSTEM -> palette.textMuted
                }
              } else {
                when (entry.level) {
                  LogLevel.PRINT -> palette.success
                  LogLevel.WARN -> palette.warning
                  LogLevel.ERROR -> palette.error
                  LogLevel.FRAME -> palette.accent
                  LogLevel.EXEC -> MaterialTheme.colorScheme.secondary
                  LogLevel.SYSTEM -> palette.textMuted
                }
              }
            val tag =
              when (entry.level) {
                LogLevel.PRINT -> "[OUT]"
                LogLevel.WARN -> "[WRN]"
                LogLevel.ERROR -> "[ERR]"
                LogLevel.FRAME -> "[FRM]"
                LogLevel.EXEC -> "[EXE]"
                LogLevel.SYSTEM -> "[SYS]"
              }
            Row(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = "$tag ",
                style =
                  TextStyle(
                    fontFamily = fontFamily,
                    fontSize = termFontSize,
                    fontWeight = FontWeight.Bold,
                    color = tagColor,
                  ),
              )
              Text(
                text = entry.message,
                style =
                  TextStyle(
                    fontFamily = fontFamily,
                    fontSize = termFontSize,
                    fontWeight =
                      if (entry.level == LogLevel.ERROR) FontWeight.SemiBold else FontWeight.Normal,
                    color =
                      if (entry.level == LogLevel.ERROR && !palette.isMonochrome) palette.error
                      else MaterialTheme.colorScheme.onBackground,
                  ),
              )
            }
          }
        }
      }
    }
  }
}

private fun buildTerminalRowAnnotatedString(cells: List<TerminalCell>): AnnotatedString {
  return buildAnnotatedString {
    if (cells.isEmpty()) return@buildAnnotatedString
    var runStart = 0
    var curFg = cells[0].fgArgb
    var curBg = cells[0].bgArgb
    var curBold = cells[0].bold

    for (i in cells.indices) {
      val c = cells[i]
      append(c.ch)
      val changed = c.fgArgb != curFg || c.bgArgb != curBg || c.bold != curBold
      if (changed) {
        addStyle(
          SpanStyle(
            color = Color(curFg),
            background = Color(curBg),
            fontWeight = if (curBold) FontWeight.Bold else FontWeight.Normal,
          ),
          runStart,
          i,
        )
        runStart = i
        curFg = c.fgArgb
        curBg = c.bgArgb
        curBold = c.bold
      }
    }
    addStyle(
      SpanStyle(
        color = Color(curFg),
        background = Color(curBg),
        fontWeight = if (curBold) FontWeight.Bold else FontWeight.Normal,
      ),
      runStart,
      cells.size,
    )
  }
}
