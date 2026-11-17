package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.EditorScreen
import com.example.ui.LuaIdeViewModel
import com.example.ui.SandboxScreen
import com.example.ui.SettingsScreen
import com.example.ui.TerminalScreen
import com.example.ui.icons.LucideIcons
import com.example.ui.theme.BloxLuaTheme
import com.example.ui.theme.LocalBloxPalette

enum class MainDestination(
  val label: String,
  val icon: ImageVector,
  val tag: String,
) {
  EDITOR("Editor", LucideIcons.Code2, "nav_editor"),
  TERMINAL("Terminal", LucideIcons.Terminal, "nav_terminal"),
  SANDBOX("Sandbox", LucideIcons.Cpu, "nav_sandbox"),
  SETTINGS("Settings", LucideIcons.Settings, "nav_settings"),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val vm: LuaIdeViewModel = viewModel()
      val settings by vm.settings.collectAsStateWithLifecycle()
      val scripts by vm.scripts.collectAsStateWithLifecycle()
      val activeScriptId by vm.activeScriptId.collectAsStateWithLifecycle()
      val editorCode by vm.editorCode.collectAsStateWithLifecycle()
      val compileStatus by vm.compileStatus.collectAsStateWithLifecycle()
      val isRunning by vm.isRunning.collectAsStateWithLifecycle()
      val logs by vm.logs.collectAsStateWithLifecycle()
      val frameSnapshot by vm.frameSnapshot.collectAsStateWithLifecycle()
      val guiScene by vm.guiScene.collectAsStateWithLifecycle()
      val inspectorState by vm.inspectorState.collectAsStateWithLifecycle()
      val hasUnsaved by vm.hasUnsavedChanges.collectAsStateWithLifecycle()

      var currentDestination by rememberSaveable { mutableStateOf(MainDestination.EDITOR) }

      BackHandler(enabled = currentDestination != MainDestination.EDITOR) {
        currentDestination = MainDestination.EDITOR
      }

      BloxLuaTheme(
        darkTheme = settings.darkMode,
        themePalette = settings.themePalette,
      ) {
        Scaffold(
          modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
          contentWindowInsets = WindowInsets.safeDrawing,
          topBar = {
            BloxTopNavigationBar(
              currentDestination = currentDestination,
              isRunning = isRunning,
              darkMode = settings.darkMode,
              executionIntervalMs = settings.executionIntervalMs,
              onSelectDestination = { currentDestination = it },
              onToggleDarkMode = { vm.setDarkMode(!settings.darkMode) },
              onRunOrStop = {
                if (isRunning) {
                  vm.stopExecution()
                } else {
                  vm.runScript()
                  currentDestination = MainDestination.TERMINAL
                }
              },
            )
          },
        ) { innerPadding ->
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
          ) {
            when (currentDestination) {
              MainDestination.EDITOR ->
                EditorScreen(
                  scripts = scripts,
                  activeScriptId = activeScriptId,
                  editorCode = editorCode,
                  compileStatus = compileStatus,
                  isRunning = isRunning,
                  hasUnsavedChanges = hasUnsaved,
                  settings = settings,
                  onSelectScript = vm::selectScript,
                  onCodeChange = vm::updateCode,
                  onInsertSnippet = vm::insertSnippet,
                  onCreateScript = vm::createNewScript,
                  onSaveScript = vm::saveActiveScript,
                  onDeleteScript = vm::deleteActiveScript,
                  onCompile = { vm.compileNow() },
                  onRun = {
                    vm.runScript()
                    currentDestination = MainDestination.TERMINAL
                  },
                  onStop = { vm.stopExecution() },
                )

              MainDestination.TERMINAL ->
                TerminalScreen(
                  guiScene = guiScene,
                  frameSnapshot = frameSnapshot,
                  logs = logs,
                  isRunning = isRunning,
                  settings = settings,
                  onViewportSizeDp = vm::updateGuiViewportDp,
                  onTriggerButton = vm::triggerInteractiveButton,
                  onDragCanvasTarget = vm::onGuiCanvasDrag,
                  onRun = vm::runScript,
                  onStop = { vm.stopExecution() },
                  onToggleAutoScroll = vm::setAutoScrollTerminal,
                  onCopyLogs = vm::copyLogsToClipboard,
                  onClearLogs = vm::clearTerminal,
                )

              MainDestination.SANDBOX ->
                SandboxScreen(
                  compileStatus = compileStatus,
                  inspectorState = inspectorState,
                  isRunning = isRunning,
                  settings = settings,
                  onDeleteVirtualFile = vm::deleteVirtualFile,
                )

              MainDestination.SETTINGS ->
                SettingsScreen(
                  settings = settings,
                  onDarkModeChange = vm::setDarkMode,
                  onThemePaletteChange = vm::setThemePalette,
                  onExecutionIntervalChange = vm::setExecutionIntervalMs,
                  onScrollSpeedChange = vm::setScrollSpeed,
                  onCodeFontChange = vm::setCodeFont,
                  onEditorFontSizeChange = vm::setEditorFontSize,
                  onTerminalFontSizeChange = vm::setTerminalFontSize,
                  onShowLineNumbersChange = vm::setShowLineNumbers,
                  onSyntaxHighlightingChange = vm::setSyntaxHighlighting,
                  onAutoScrollChange = vm::setAutoScrollTerminal,
                  onTerminalColumnsChange = vm::setTerminalColumns,
                  onShowFrameDiffLogsChange = vm::setShowFrameDiffLogs,
                  onMaxOpsChange = vm::setMaxOpsPerExecution,
                  onMonochromeGuiPreviewChange = vm::setMonochromeGuiPreview,
                  onResetDefaults = vm::resetSettings,
                )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BloxTopNavigationBar(
  currentDestination: MainDestination,
  isRunning: Boolean,
  darkMode: Boolean,
  executionIntervalMs: Int,
  onSelectDestination: (MainDestination) -> Unit,
  onToggleDarkMode: () -> Unit,
  onRunOrStop: () -> Unit,
) {
  val palette = LocalBloxPalette.current
  Surface(
    color = MaterialTheme.colorScheme.surface,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Compact utility header row
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "BloxLua",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "5.1 · ${executionIntervalMs}ms",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
          )
          Surface(
            color =
              if (isRunning) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(3.dp),
          ) {
            Text(
              text = if (isRunning) "RUN" else "IDLE",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (isRunning) MaterialTheme.colorScheme.onPrimary
                else palette.textMuted,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onRunOrStop,
            modifier = Modifier.size(48.dp).testTag("topbar_run_stop"),
          ) {
            Icon(
              imageVector = if (isRunning) LucideIcons.Square else LucideIcons.Play,
              contentDescription = if (isRunning) "Stop execution" else "Run script",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onToggleDarkMode,
            modifier = Modifier.size(48.dp).testTag("topbar_dark_mode_toggle"),
          ) {
            Icon(
              imageVector = if (darkMode) LucideIcons.Sun else LucideIcons.Moon,
              contentDescription = if (darkMode) "Switch to light mode" else "Switch to dark mode",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      // Top Navigation Tab Bar (replaces bottom navigation bar)
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        MainDestination.entries.forEach { dest ->
          val selected = currentDestination == dest
          Column(
            modifier =
              Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable { onSelectDestination(dest) }
                .testTag(dest.tag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.padding(top = 10.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = dest.icon,
                contentDescription = dest.label,
                tint =
                  if (selected) MaterialTheme.colorScheme.primary
                  else palette.textMuted,
                modifier = Modifier.size(15.dp),
              )
              Spacer(Modifier.width(5.dp))
              Text(
                text = dest.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                  if (selected) MaterialTheme.colorScheme.onSurface
                  else palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(2.dp)
                  .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent
                  ),
            )
          }
        }
      }

      Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.border))
    }
  }
}
