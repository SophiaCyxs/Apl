package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.EditorScreen
import com.example.ui.GalaxyBackdropLayer
import com.example.ui.LuaIdeViewModel
import com.example.ui.SandboxScreen
import com.example.ui.SettingsScreen
import com.example.ui.TerminalScreen
import com.example.ui.icons.LucideIcons
import com.example.ui.theme.BloxLuaTheme
import com.example.ui.theme.LocalBloxPalette
import com.example.ui.theme.LocalBloxVisuals
import com.example.ui.theme.MovingGradientText

enum class MainDestination(
  val label: String,
  val icon: ImageVector,
  val tag: String,
) {
  EDITOR("Editor", LucideIcons.Code2, "nav_editor"),
  TERMINAL("Studio", LucideIcons.Terminal, "nav_terminal"),
  SANDBOX("Inspect", LucideIcons.Cpu, "nav_sandbox"),
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
      val compileTriggerSeq by vm.compileTriggerSeq.collectAsStateWithLifecycle()
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
        settings = settings,
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          GalaxyBackdropLayer(
            settings = settings,
            modifier = Modifier.fillMaxSize(),
          )

          Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
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
              modifier = Modifier.fillMaxSize().padding(innerPadding),
              contentAlignment = Alignment.TopCenter,
            ) {
              AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                  fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                },
                label = "main_nav_transition",
                modifier = Modifier.fillMaxSize().widthIn(max = 920.dp),
              ) { dest ->
                when (dest) {
                  MainDestination.EDITOR ->
                    EditorScreen(
                      scripts = scripts,
                      activeScriptId = activeScriptId,
                      editorCode = editorCode,
                      compileStatus = compileStatus,
                      compileTriggerSeq = compileTriggerSeq,
                      isRunning = isRunning,
                      hasUnsavedChanges = hasUnsaved,
                      settings = settings,
                      onSelectScript = vm::selectScript,
                      onCodeChange = vm::updateCode,
                      onInsertSnippet = vm::insertSnippet,
                      onCreateScript = vm::createNewScript,
                      onSaveScript = vm::saveActiveScript,
                      onDeleteScript = vm::deleteActiveScript,
                      onCompile = vm::compileManually,
                      onInspectBytecode = { currentDestination = MainDestination.SANDBOX },
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
                      onSubmitTextBox = vm::submitTextBoxInput,
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
                      onEnableHttpGetChange = vm::setEnableHttpGet,
                      onSyntaxGradientEnabledChange = vm::setSyntaxGradientEnabled,
                      onSyntaxGradientMovingChange = vm::setSyntaxGradientMoving,
                      onFontGradientMovingChange = vm::setFontGradientMoving,
                      onGradientPresetChange = vm::setGradientPreset,
                      onGradientSpeedChange = vm::setGradientSpeed,
                      onSyntaxTokenColorChange = vm::setSyntaxTokenColor,
                      onResetSyntaxColors = vm::resetSyntaxColors,
                      onBackgroundModeChange = vm::setBackgroundMode,
                      onPickBackgroundUri = vm::pickCustomBackground,
                      onClearCustomBackground = vm::clearCustomBackground,
                      onBackgroundVideoMutedChange = vm::setBackgroundVideoMuted,
                      onGalaxySpeedChange = vm::setGalaxySpeed,
                      onGalaxyStarDensityChange = vm::setGalaxyStarDensity,
                      onBackgroundBlurChange = vm::setBackgroundBlurDp,
                      onBackgroundDimChange = vm::setBackgroundDimAlpha,
                      onPanelOpacityChange = vm::setPanelOpacity,
                      onPanelShadowChange = vm::setPanelShadowDp,
                      onResetDefaults = vm::resetSettings,
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
  val visuals = LocalBloxVisuals.current
  val barAlpha = if (visuals.hasBackdrop) (visuals.panelOpacity * 0.94f).coerceIn(0.45f, 0.96f) else 0.98f

  Box(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = MaterialTheme.colorScheme.surface.copy(alpha = barAlpha),
      shape = RoundedCornerShape(16.dp),
      shadowElevation = visuals.panelShadowDp.coerceIn(0f, 16f).dp,
      border =
        BorderStroke(
          1.dp,
          if (palette.isDark) Color.White.copy(alpha = 0.14f) else palette.border.copy(alpha = 0.9f),
        ),
      modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        // Brand Identity + Telemetry Pill + Quick Actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(
              modifier =
                Modifier
                  .size(30.dp)
                  .clip(RoundedCornerShape(9.dp))
                  .background(MaterialTheme.colorScheme.primary)
                  .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(9.dp)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = LucideIcons.Code2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp),
              )
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              MovingGradientText(
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
                shape = RoundedCornerShape(999.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Box(
                    modifier =
                      Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                          if (isRunning) MaterialTheme.colorScheme.onPrimary
                          else palette.textMuted,
                        ),
                  )
                  Text(
                    text = if (isRunning) "RUN" else "IDLE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color =
                      if (isRunning) MaterialTheme.colorScheme.onPrimary
                      else palette.textMuted,
                  )
                }
              }
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            IconButton(
              onClick = onRunOrStop,
              modifier = Modifier.size(48.dp).testTag("topbar_run_stop"),
            ) {
              Icon(
                imageVector = if (isRunning) LucideIcons.Square else LucideIcons.Play,
                contentDescription = if (isRunning) "Stop execution" else "Run script",
                tint = if (isRunning) palette.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
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
                modifier = Modifier.size(17.dp),
              )
            }
          }
        }

        // Modern Segmented Pill Navigation Strip
        Surface(
          color = MaterialTheme.colorScheme.background.copy(alpha = if (visuals.hasBackdrop) 0.52f else 0.85f),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, palette.border.copy(alpha = 0.55f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            MainDestination.entries.forEach { dest ->
              val selected = currentDestination == dest
              val pillBg by
                animateColorAsState(
                  targetValue =
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                  animationSpec = tween(180),
                  label = "nav_pill_bg",
                )
              val contentColor by
                animateColorAsState(
                  targetValue =
                    if (selected) MaterialTheme.colorScheme.onPrimary
                    else palette.textMuted,
                  animationSpec = tween(180),
                  label = "nav_pill_fg",
                )
              Box(
                modifier =
                  Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(pillBg)
                    .clickable { onSelectDestination(dest) }
                    .testTag(dest.tag),
                contentAlignment = Alignment.Center,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                ) {
                  Icon(
                    imageVector = dest.icon,
                    contentDescription = dest.label,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp),
                  )
                  Spacer(Modifier.width(5.dp))
                  MovingGradientText(
                    text = dest.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
