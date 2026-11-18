package com.example.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.ScriptEntity
import com.example.ui.icons.LucideIcons
import com.example.ui.theme.LocalBloxPalette
import com.example.ui.theme.LocalBloxVisuals
import com.example.ui.theme.LocalGradientPhaseState
import com.example.ui.theme.MovingGradientText
import com.example.ui.theme.bloxSurface
import com.example.ui.theme.buildMovingGradientBrush
import kotlinx.coroutines.delay

private val QuickSnippets =
  listOf(
    "Instance.new" to "local f = Instance.new(\"Frame\", gethui())",
    "UDim2" to "UDim2.new(0.1, 0, 0.1, 0)",
    "Color3" to "Color3.fromRGB(220, 220, 220)",
    "gethui()" to "gethui()",
    "getgenv()" to "getgenv()",
    "LocalScript" to "local ls = Instance.new(\"LocalScript\")\nls.Source = \"print('local ok')\"\nls.Parent = gethui()",
    "Notify" to "game:GetService(\"StarterGui\"):SetCore(\"SendNotification\", { Title = \"Blox\", Text = \"Ready\" })",
    "Drawing.new" to "local d = Drawing.new(\"Square\")",
    "task.wait()" to "task.wait(0.1)",
  )

@Suppress("DEPRECATION")
@Composable
fun EditorScreen(
  scripts: List<ScriptEntity>,
  activeScriptId: Int?,
  editorCode: String,
  compileStatus: CompileStatus,
  compileTriggerSeq: Long = 0L,
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
  onInspectBytecode: () -> Unit = {},
  onRun: () -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalBloxPalette.current
  val visuals = LocalBloxVisuals.current
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }

  val activeScript = scripts.firstOrNull { it.id == activeScriptId }
  var showNewDialog by remember { mutableStateOf(false) }
  var newScriptTitle by remember { mutableStateOf("") }
  var undoClearedCode by remember { mutableStateOf<String?>(null) }
  var compileFlashActive by remember { mutableStateOf(false) }

  LaunchedEffect(compileTriggerSeq) {
    if (compileTriggerSeq > 0L) {
      compileFlashActive = true
      val toastMsg =
        when (compileStatus) {
          is CompileStatus.Success ->
            "Lua 5.1 Compiled OK · ${compileStatus.instructionCount} ops (${compileStatus.compileTimeUs}µs)"
          is CompileStatus.Error ->
            "Compile Error L${compileStatus.line}: ${compileStatus.message}"
          CompileStatus.Idle -> "Compiled"
        }
      Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
      delay(420L)
      compileFlashActive = false
    }
  }

  var textFieldValue by remember { mutableStateOf(TextFieldValue(editorCode)) }
  LaunchedEffect(editorCode) {
    if (textFieldValue.text != editorCode) {
      val safeCursor = textFieldValue.selection.end.coerceIn(0, editorCode.length)
      textFieldValue = TextFieldValue(editorCode, selection = TextRange(safeCursor))
    }
  }

  val codeFontFamily = remember(settings.codeFont) { settings.codeFont.toComposeFontFamily() }
  val fontSize = settings.editorFontSizeSp.sp
  val lineHeight = (settings.editorFontSizeSp * 1.45f).sp

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 3.dp),
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    // Modern Script Carousel + File Actions
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      LazyRow(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(end = 4.dp),
      ) {
        items(scripts, key = { it.id }) { script ->
          val selected = script.id == activeScriptId
          Surface(
            modifier =
              Modifier
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                  undoClearedCode = null
                  onSelectScript(script)
                }
                .testTag("script_tab_${script.id}"),
            color =
              if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surface.copy(alpha = visuals.effectivePanelAlpha()),
            shape = RoundedCornerShape(10.dp),
            border =
              BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else palette.border.copy(alpha = 0.75f),
              ),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                      if (selected) MaterialTheme.colorScheme.onPrimary
                      else palette.textMuted.copy(alpha = 0.6f),
                    ),
              )
              Text(
                text = script.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
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

      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = visuals.effectivePanelAlpha()),
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.75f)),
      ) {
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
      }

      if (hasUnsavedChanges) {
        Surface(
          shape = CircleShape,
          color = palette.success.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, palette.success.copy(alpha = 0.45f)),
        ) {
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
      }

      if (activeScript != null && !activeScript.isBuiltIn) {
        Surface(
          shape = CircleShape,
          color = palette.error.copy(alpha = 0.12f),
          border = BorderStroke(1.dp, palette.error.copy(alpha = 0.45f)),
        ) {
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
    }

    // Clipboard & Selection Action Pills
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      EditorActionChip(
        label = "Select All",
        icon = LucideIcons.CheckSquare,
        modifier = Modifier.weight(1f).testTag("editor_select_all_btn"),
        onClick = {
          textFieldValue =
            textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
          runCatching { focusRequester.requestFocus() }
        },
      )
      EditorActionChip(
        label = "Copy All",
        icon = LucideIcons.Copy,
        modifier = Modifier.weight(1f).testTag("editor_copy_all_btn"),
        onClick = {
          val content = textFieldValue.text
          clipboardManager.setText(AnnotatedString(content))
          Toast.makeText(context, "Copied ${content.length} chars", Toast.LENGTH_SHORT).show()
        },
      )
      EditorActionChip(
        label = "Paste",
        icon = LucideIcons.ClipboardPaste,
        modifier = Modifier.weight(1f).testTag("editor_paste_btn"),
        onClick = {
          val clipText = clipboardManager.getText()?.text ?: ""
          if (clipText.isNotEmpty()) {
            undoClearedCode = null
            val cur = textFieldValue
            val start = cur.selection.min.coerceIn(0, cur.text.length)
            val end = cur.selection.max.coerceIn(start, cur.text.length)
            val updated = cur.text.replaceRange(start, end, clipText)
            val nextCursor = start + clipText.length
            textFieldValue = TextFieldValue(updated, TextRange(nextCursor))
            onCodeChange(updated)
          }
        },
      )
      EditorActionChip(
        label = if (undoClearedCode != null && textFieldValue.text.isEmpty()) "Undo" else "Clear",
        icon = if (undoClearedCode != null && textFieldValue.text.isEmpty()) LucideIcons.RotateCcw else LucideIcons.Eraser,
        isDanger = !(undoClearedCode != null && textFieldValue.text.isEmpty()),
        modifier = Modifier.weight(1f).testTag("editor_clear_btn"),
        onClick = {
          val prev = undoClearedCode
          if (prev != null && textFieldValue.text.isEmpty()) {
            undoClearedCode = null
            textFieldValue = TextFieldValue(prev, TextRange(prev.length))
            onCodeChange(prev)
          } else if (textFieldValue.text.isNotEmpty()) {
            undoClearedCode = textFieldValue.text
            textFieldValue = TextFieldValue("", TextRange(0))
            onCodeChange("")
          }
        },
      )
    }

    // Compiler Status Pill (tap to view Lua 5.1 Bytecode)
    if (compileStatus !is CompileStatus.Idle) {
      val (icon, textColor, msg) =
        when (compileStatus) {
          is CompileStatus.Success ->
            Triple(
              LucideIcons.CheckCircle2,
              palette.success,
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

      val bannerBg by
        animateColorAsState(
          targetValue =
            when {
              compileFlashActive && compileStatus is CompileStatus.Success ->
                MaterialTheme.colorScheme.primaryContainer
              compileStatus is CompileStatus.Error ->
                MaterialTheme.colorScheme.surfaceVariant
              else -> MaterialTheme.colorScheme.surface
            },
          animationSpec = tween(220),
          label = "compile_banner_bg",
        )
      val bannerBorder =
        when {
          compileFlashActive && compileStatus is CompileStatus.Success -> MaterialTheme.colorScheme.primary
          compileStatus is CompileStatus.Error -> palette.error
          else -> palette.border
        }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .bloxSurface(
              shape = RoundedCornerShape(10.dp),
              color = bannerBg,
              borderColor = bannerBorder,
            )
            .clip(RoundedCornerShape(10.dp))
            .clickable { onInspectBytecode() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("compile_status_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
          color = if (compileStatus is CompileStatus.Error) palette.error else MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = "Bytecode >",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = palette.textMuted,
        )
      }
    }

    // Modern Code Editor Card with Window Chrome Header
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val lineCount = remember(textFieldValue.text) { textFieldValue.text.count { it == '\n' } + 1 }

    Column(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .bloxSurface(shape = RoundedCornerShape(16.dp), color = palette.terminalBg),
    ) {
      // Modern Editor Header Bar
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = LucideIcons.FileCode2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
          )
          MovingGradientText(
            text = (activeScript?.title ?: "script") + if (hasUnsavedChanges) " *" else "",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = "$lineCount ln · ${textFieldValue.text.length} ch",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = codeFontFamily),
          color = palette.textMuted,
        )
      }
      Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.border.copy(alpha = 0.5f)))

      Row(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(verticalScroll)
            .height(IntrinsicSize.Min)
            .padding(vertical = 6.dp),
      ) {
        if (settings.showLineNumbers) {
          val lineNumbersText =
            remember(lineCount) {
              buildString(lineCount * 4) {
                for (ln in 1..lineCount) {
                  if (ln > 1) append('\n')
                  append(ln)
                }
              }
            }
          Text(
            text = lineNumbersText,
            style =
              TextStyle(
                fontFamily = codeFontFamily,
                fontSize = fontSize,
                lineHeight = lineHeight,
                textAlign = TextAlign.End,
                color = palette.textMuted.copy(alpha = 0.55f),
              ),
            modifier = Modifier.padding(start = 10.dp, end = 8.dp),
          )
          Box(
            modifier =
              Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(palette.border.copy(alpha = 0.5f)),
          )
        }

        Box(
          modifier =
            Modifier
              .weight(1f)
              .horizontalScroll(horizontalScroll)
              .padding(horizontal = 10.dp),
        ) {
          LuaCodeEditorField(
            value = textFieldValue,
            onValueChange = { next ->
              undoClearedCode = null
              val textChanged = next.text != textFieldValue.text
              textFieldValue = next
              if (textChanged) onCodeChange(next.text)
            },
            focusRequester = focusRequester,
            codeFontFamily = codeFontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
            syntaxHighlighting = settings.syntaxHighlighting,
          )
        }
      }
    }

    // Quick Snippet Strip
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      items(QuickSnippets) { (label, snippet) ->
        Surface(
          modifier =
            Modifier
              .heightIn(min = 36.dp)
              .clip(RoundedCornerShape(999.dp))
              .clickable {
                undoClearedCode = null
                onInsertSnippet(snippet)
              }
              .testTag("snippet_$label"),
          color = MaterialTheme.colorScheme.surface.copy(alpha = visuals.effectivePanelAlpha()),
          shape = RoundedCornerShape(999.dp),
          border = BorderStroke(1.dp, palette.border.copy(alpha = 0.7f)),
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = codeFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
          )
        }
      }
    }

    // Modern Compile / Run / Stop Dock
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(
        onClick = {
          focusManager.clearFocus()
          keyboardController?.hide()
          onCompile()
        },
        modifier = Modifier.weight(0.85f).heightIn(min = 48.dp).testTag("compile_button"),
        shape = RoundedCornerShape(12.dp),
        colors =
          ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = visuals.effectivePanelAlpha()),
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
        border = BorderStroke(1.dp, palette.border),
      ) {
        Icon(
          imageVector = LucideIcons.Braces,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("Compile", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
      }

      if (isRunning) {
        OutlinedButton(
          onClick = onStop,
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("stop_button"),
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor = MaterialTheme.colorScheme.surface.copy(alpha = visuals.effectivePanelAlpha()),
              contentColor = palette.error,
            ),
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
        onClick = {
          focusManager.clearFocus()
          keyboardController?.hide()
          onRun()
        },
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("run_button"),
        shape = RoundedCornerShape(12.dp),
      ) {
        Icon(
          imageVector = LucideIcons.Play,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = if (isRunning) "Restart" else "Execute",
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }

  if (showNewDialog) {
    AlertDialog(
      onDismissRequest = { showNewDialog = false },
      shape = RoundedCornerShape(18.dp),
      title = { Text("New Lua Script") },
      text = {
        OutlinedTextField(
          value = newScriptTitle,
          onValueChange = { newScriptTitle = it },
          label = { Text("Script Name") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
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
          shape = RoundedCornerShape(10.dp),
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

@Composable
private fun EditorActionChip(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isDanger: Boolean = false,
) {
  val palette = LocalBloxPalette.current
  val visuals = LocalBloxVisuals.current
  Surface(
    modifier =
      modifier
        .heightIn(min = 38.dp)
        .clip(RoundedCornerShape(10.dp))
        .clickable(onClick = onClick),
    color = MaterialTheme.colorScheme.surface.copy(alpha = visuals.effectivePanelAlpha()),
    shape = RoundedCornerShape(10.dp),
    border =
      BorderStroke(
        1.dp,
        if (isDanger) palette.error.copy(alpha = 0.5f) else palette.border.copy(alpha = 0.72f),
      ),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isDanger) palette.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(13.dp),
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (isDanger) palette.error else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun LuaCodeEditorField(
  value: TextFieldValue,
  onValueChange: (TextFieldValue) -> Unit,
  focusRequester: FocusRequester,
  codeFontFamily: FontFamily,
  fontSize: TextUnit,
  lineHeight: TextUnit,
  syntaxHighlighting: Boolean,
) {
  val palette = LocalBloxPalette.current
  val visuals = LocalBloxVisuals.current
  val phaseState = LocalGradientPhaseState.current

  // Tokenize spans ONLY when text or syntaxHighlighting toggle changes (zero re-lexing on animation ticks)
  val cachedSpans =
    remember(value.text, syntaxHighlighting) {
      if (syntaxHighlighting) LuaSyntaxHighlighter.tokenizeSpans(value.text) else emptyList()
    }

  val animateSyntaxGradient =
    syntaxHighlighting && visuals.syntaxGradientEnabled && visuals.syntaxGradientMoving && value.text.length <= 4000
  val animatePlainFontGradient = !syntaxHighlighting && visuals.fontGradientMoving

  // Quantize animation ticks (~10 steps per cycle) cleanly inside this isolated leaf only when enabled
  val quantizedPhase by
    remember(animateSyntaxGradient, animatePlainFontGradient) {
      derivedStateOf {
        if (animateSyntaxGradient || animatePlainFontGradient) {
          ((phaseState.value * 10f).toInt() / 10f)
        } else {
          0f
        }
      }
    }

  val syntaxVisualTransformation =
    remember(
      value.text,
      cachedSpans,
      syntaxHighlighting,
      palette.syntax,
      visuals.syntaxGradientEnabled,
      visuals.gradientColors,
      quantizedPhase,
    ) {
      VisualTransformation { text ->
        val highlighted =
          if (!syntaxHighlighting) {
            AnnotatedString(text.text)
          } else if (text.text == value.text) {
            LuaSyntaxHighlighter.renderSpans(
              source = text.text,
              spans = cachedSpans,
              colors = palette.syntax,
              enabled = true,
              gradientEnabled = visuals.syntaxGradientEnabled,
              gradientColors = visuals.gradientColors,
              gradientPhase = quantizedPhase,
            )
          } else {
            LuaSyntaxHighlighter.highlight(
              source = text.text,
              colors = palette.syntax,
              enabled = true,
              gradientEnabled = visuals.syntaxGradientEnabled,
              gradientColors = visuals.gradientColors,
              gradientPhase = quantizedPhase,
            )
          }
        TransformedText(highlighted, OffsetMapping.Identity)
      }
    }

  val baseTextStyle =
    TextStyle(
      fontFamily = codeFontFamily,
      fontSize = fontSize,
      lineHeight = lineHeight,
      color = palette.syntax.defaultText,
    ).let { style ->
      if (animatePlainFontGradient) {
        style.copy(brush = buildMovingGradientBrush(visuals.gradientColors, quantizedPhase, spanPx = 560f))
      } else {
        style
      }
    }

  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    modifier =
      Modifier
        .widthIn(min = 240.dp)
        .fillMaxWidth()
        .focusRequester(focusRequester)
        .testTag("lua_code_editor"),
    textStyle = baseTextStyle,
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    visualTransformation = syntaxVisualTransformation,
  )
}
