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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.ScriptEntity
import com.example.ui.icons.LucideIcons
import com.example.ui.theme.LocalBloxPalette

private val QuickSnippets =
  listOf(
    "Instance.new" to "local f = Instance.new(\"Frame\", gethui())",
    "UDim2" to "UDim2.new(0.1, 0, 0.1, 0)",
    "Color3" to "Color3.fromRGB(220, 220, 220)",
    "gethui()" to "gethui()",
    "getgenv()" to "getgenv()",
    "hookfunction" to "hookfunction(fn, function(...) return ... end)",
    "Drawing.new" to "local d = Drawing.new(\"Square\")",
    "task.wait()" to "task.wait(0.1)",
  )

@Composable
fun EditorScreen(
  scripts: List<ScriptEntity>,
  activeScriptId: Int?,
  editorCode: String,
  compileStatus: CompileStatus,
  isRunning: Boolean,
  hasUnsavedChanges: Boolean,
  settings: AppSettings,
  onSelectScript: (ScriptEntity) -> Unit,
  onCodeChange: (String) -> Unit,
  onInsertSnippet: (String) -> Unit,
  onCreateScript: (String) -> Unit,
  onSaveScript: () -> Unit,
  onDeleteScript: () -> Unit,
  onCompile: () -> Unit,
  onRun: () -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalBloxPalette.current
  val activeScript = scripts.firstOrNull { it.id == activeScriptId }
  var showNewDialog by remember { mutableStateOf(false) }
  var newScriptTitle by remember { mutableStateOf("") }

  val codeFontFamily = remember(settings.codeFont) { settings.codeFont.toComposeFontFamily() }
  val fontSize = settings.editorFontSizeSp.sp
  val lineHeight = (settings.editorFontSizeSp * 1.45f).sp

  val syntaxVisualTransformation =
    remember(settings.syntaxHighlighting, palette.syntax) {
      VisualTransformation { text ->
        TransformedText(
          LuaSyntaxHighlighter.highlight(text.text, palette.syntax, settings.syntaxHighlighting),
          OffsetMapping.Identity,
        )
      }
    }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // Script Tabs + New / Save / Delete
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      LazyRow(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(end = 4.dp),
      ) {
        items(scripts, key = { it.id }) { script ->
          val selected = script.id == activeScriptId
          Surface(
            modifier =
              Modifier
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { onSelectScript(script) }
                .testTag("script_tab_${script.id}"),
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
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Text(
                text = script.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                  if (selected) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      IconButton(
        onClick = { showNewDialog = true },
        modifier = Modifier.size(48.dp).testTag("new_script_button"),
      ) {
        Icon(
          imageVector = LucideIcons.Plus,
          contentDescription = "New script",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(18.dp),
        )
      }

      if (hasUnsavedChanges) {
        IconButton(
          onClick = onSaveScript,
          modifier = Modifier.size(48.dp).testTag("save_script_button"),
        ) {
          Icon(
            imageVector = LucideIcons.Save,
            contentDescription = "Save script",
            tint = palette.success,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      if (activeScript != null && !activeScript.isBuiltIn) {
        IconButton(
          onClick = onDeleteScript,
          modifier = Modifier.size(48.dp).testTag("delete_script_button"),
        ) {
          Icon(
            imageVector = LucideIcons.Trash2,
            contentDescription = "Delete script",
            tint = palette.error,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }

    // Compact Compiler Status Line
    if (compileStatus !is CompileStatus.Idle) {
      val (icon, textColor, msg) =
        when (compileStatus) {
          is CompileStatus.Success ->
            Triple(
              LucideIcons.Check,
              palette.textMuted,
              "5.1 OK · ${compileStatus.instructionCount} ops · ${compileStatus.protoCount} chunks · ${compileStatus.compileTimeUs}µs",
            )
          is CompileStatus.Error ->
            Triple(
              LucideIcons.AlertTriangle,
              palette.error,
              "ERR ${compileStatus.stage} L${compileStatus.line}:${compileStatus.column} · ${compileStatus.message}",
            )
          CompileStatus.Idle -> Triple(LucideIcons.Check, palette.textMuted, "")
        }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(
              if (compileStatus is CompileStatus.Error) MaterialTheme.colorScheme.surfaceVariant
              else MaterialTheme.colorScheme.surface,
              RoundedCornerShape(4.dp),
            )
            .border(
              1.dp,
              if (compileStatus is CompileStatus.Error) palette.error else palette.border,
              RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("compile_status_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = textColor,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = msg,
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = codeFontFamily),
          color = textColor,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Code Editor Box
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val lineCount = remember(editorCode) { editorCode.count { it == '\n' } + 1 }

    Box(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .background(palette.terminalBg, RoundedCornerShape(4.dp))
          .border(1.dp, palette.border, RoundedCornerShape(4.dp))
          .padding(vertical = 8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxSize().verticalScroll(verticalScroll),
      ) {
        if (settings.showLineNumbers) {
          Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.End,
          ) {
            for (ln in 1..lineCount) {
              Text(
                text = ln.toString(),
                style =
                  TextStyle(
                    fontFamily = codeFontFamily,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = palette.textMuted.copy(alpha = 0.6f),
                  ),
              )
            }
          }
          Box(
            modifier =
              Modifier
                .width(1.dp)
                .height((lineCount * (settings.editorFontSizeSp * 1.45f) + 12f).dp)
                .background(palette.border),
          )
        }

        Box(
          modifier =
            Modifier
              .weight(1f)
              .horizontalScroll(horizontalScroll)
              .padding(horizontal = 10.dp),
        ) {
          BasicTextField(
            value = editorCode,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth().testTag("lua_code_editor"),
            textStyle =
              TextStyle(
                fontFamily = codeFontFamily,
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = MaterialTheme.colorScheme.onBackground,
              ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = syntaxVisualTransformation,
          )
        }
      }
    }

    // Quick Snippet Strip
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      items(QuickSnippets) { (label, snippet) ->
        Surface(
          modifier =
            Modifier
              .heightIn(min = 34.dp)
              .clip(RoundedCornerShape(4.dp))
              .clickable { onInsertSnippet(snippet) }
              .testTag("snippet_$label"),
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(4.dp),
          border = BorderStroke(1.dp, palette.border),
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = codeFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
          )
        }
      }
    }

    // Compile / Run / Stop Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(
        onClick = onCompile,
        modifier = Modifier.heightIn(min = 48.dp).testTag("compile_button"),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, palette.border),
      ) {
        Icon(
          imageVector = LucideIcons.Braces,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("Compile", style = MaterialTheme.typography.labelMedium)
      }

      if (isRunning) {
        OutlinedButton(
          onClick = onStop,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("stop_button"),
          shape = RoundedCornerShape(4.dp),
          border = BorderStroke(1.dp, palette.error),
        ) {
          Icon(
            imageVector = LucideIcons.Square,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = palette.error,
          )
          Spacer(Modifier.width(6.dp))
          Text("Stop", color = palette.error, fontWeight = FontWeight.SemiBold)
        }
      }

      Button(
        onClick = onRun,
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("run_button"),
        shape = RoundedCornerShape(4.dp),
      ) {
        Icon(
          imageVector = LucideIcons.Play,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = if (isRunning) "Restart" else "Run",
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }

  if (showNewDialog) {
    AlertDialog(
      onDismissRequest = { showNewDialog = false },
      shape = RoundedCornerShape(6.dp),
      title = { Text("New Script") },
      text = {
        OutlinedTextField(
          value = newScriptTitle,
          onValueChange = { newScriptTitle = it },
          label = { Text("Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("new_script_title_input"),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onCreateScript(newScriptTitle)
            newScriptTitle = ""
            showNewDialog = false
          },
          shape = RoundedCornerShape(4.dp),
          modifier = Modifier.testTag("confirm_new_script"),
        ) {
          Text("Create")
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewDialog = false }) { Text("Cancel") }
      },
    )
  }
}
