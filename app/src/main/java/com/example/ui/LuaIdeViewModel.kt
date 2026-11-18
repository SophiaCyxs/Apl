package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.ScriptEntity
import com.example.data.ScriptRepository
import com.example.data.ScrollSpeed
import com.example.data.SettingsRepository
import com.example.lua.FunctionProto
import com.example.lua.GuiDrawingProcessor
import com.example.lua.GuiRenderScene
import com.example.lua.LogLevel
import com.example.lua.LuaCompileException
import com.example.lua.LuaCompiler
import com.example.lua.LuaRuntimeException
import com.example.lua.LuaValue
import com.example.lua.LuaVm
import com.example.lua.RbxColor3
import com.example.lua.RbxInstance
import com.example.lua.RobloxSandboxEnv
import com.example.lua.TerminalFrameRenderer
import com.example.lua.TerminalFrameSnapshot
import com.example.lua.TerminalLogEntry
import com.example.ui.theme.BackgroundMode
import com.example.ui.theme.CodeFontFamily
import com.example.ui.theme.SyntaxGradientPreset
import com.example.ui.theme.ThemePalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

sealed interface CompileStatus {
  object Idle : CompileStatus
  data class Success(
    val instructionCount: Int,
    val protoCount: Int,
    val compileTimeUs: Long,
    val disassembly: String,
  ) : CompileStatus

  data class Error(
    val message: String,
    val line: Int,
    val column: Int,
    val stage: String,
  ) : CompileStatus
}

data class UiTreeNodeItem(
  val id: Int,
  val depth: Int,
  val name: String,
  val className: String,
  val summary: String,
)

data class SandboxInspectorState(
  val genvEntries: List<Pair<String, String>> = emptyList(),
  val virtualFiles: List<Pair<String, String>> = emptyList(),
  val uiTree: List<UiTreeNodeItem> = emptyList(),
  val drawingCount: Int = 0,
  val opsExecuted: Long = 0L,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LuaIdeViewModel(application: Application) : AndroidViewModel(application) {
  private val settingsRepo = SettingsRepository(application)
  private val scriptRepo = ScriptRepository(AppDatabase.get(application).scriptDao())
  private val compiler = LuaCompiler()
  private val frameRenderer = TerminalFrameRenderer()
  private val guiProcessor = GuiDrawingProcessor()

  val settings: StateFlow<AppSettings> =
    settingsRepo.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = AppSettings(),
    )

  val scripts: StateFlow<List<ScriptEntity>> =
    scriptRepo.allScripts.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  private val _activeScriptId = MutableStateFlow<Int?>(null)
  val activeScriptId: StateFlow<Int?> = _activeScriptId.asStateFlow()

  private val _editorTitle = MutableStateFlow("Live Frame HUD")
  val editorTitle: StateFlow<String> = _editorTitle.asStateFlow()

  private val _editorCode = MutableStateFlow("")
  val editorCode: StateFlow<String> = _editorCode.asStateFlow()

  private val _compileStatus = MutableStateFlow<CompileStatus>(CompileStatus.Idle)
  val compileStatus: StateFlow<CompileStatus> = _compileStatus.asStateFlow()

  private val _compileTriggerSeq = MutableStateFlow(0L)
  val compileTriggerSeq: StateFlow<Long> = _compileTriggerSeq.asStateFlow()

  private val _isRunning = MutableStateFlow(false)
  val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

  private val _logs = MutableStateFlow<List<TerminalLogEntry>>(emptyList())
  val logs: StateFlow<List<TerminalLogEntry>> = _logs.asStateFlow()

  private val _frameSnapshot = MutableStateFlow<TerminalFrameSnapshot?>(null)
  val frameSnapshot: StateFlow<TerminalFrameSnapshot?> = _frameSnapshot.asStateFlow()

  private val _guiScene = MutableStateFlow<GuiRenderScene?>(null)
  val guiScene: StateFlow<GuiRenderScene?> = _guiScene.asStateFlow()

  private val _inspectorState = MutableStateFlow(SandboxInspectorState())
  val inspectorState: StateFlow<SandboxInspectorState> = _inspectorState.asStateFlow()

  private val _hasUnsavedChanges = MutableStateFlow(false)
  val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

  private val logSeq = AtomicLong(1L)
  private val frameCounter = AtomicLong(0L)
  private val uiDirty = AtomicBoolean(true)
  @Volatile private var guiViewportW = 380f
  @Volatile private var guiViewportH = 240f

  private val vmDispatcher = Dispatchers.Default.limitedParallelism(1)
  private var sandboxScope: CoroutineScope? = null
  private var activeVm: LuaVm? = null
  private var activeEnv: RobloxSandboxEnv? = null
  private var compileDebounceJob: Job? = null

  init {
    viewModelScope.launch {
      scriptRepo.ensureSeeded()
    }
    viewModelScope.launch {
      scripts.collect { list ->
        if (list.isNotEmpty() && _activeScriptId.value == null) {
          selectScript(list.first())
        }
      }
    }
    refreshEmptyFrame()
  }

  private fun refreshEmptyFrame() {
    val s = settings.value
    _frameSnapshot.value =
      frameRenderer.render(
        roots = emptyList(),
        drawings = emptyList(),
        columns = s.terminalColumns,
        rows = s.terminalRows,
        frameNumber = 0L,
        deltaMs = 0L,
        darkTheme = s.darkMode,
        monochrome = s.themePalette == ThemePalette.MONOCHROME,
      )
    _guiScene.value =
      guiProcessor.process(
        roots = emptyList(),
        drawings = emptyList(),
        viewportW = guiViewportW,
        viewportH = guiViewportH,
        frameNumber = 0L,
        deltaMs = 0L,
        monochromeGui = s.monochromeGuiPreview,
        notifications = emptyList(),
      )
  }

  fun selectScript(script: ScriptEntity) {
    _activeScriptId.value = script.id
    _editorTitle.value = script.title
    _editorCode.value = script.code
    _hasUnsavedChanges.value = false
    compileNow(script.code, script.title)
  }

  fun updateCode(newCode: String) {
    _editorCode.value = newCode
    _hasUnsavedChanges.value = true
    compileDebounceJob?.cancel()
    compileDebounceJob =
      viewModelScope.launch {
        delay(220)
        compileNow(newCode, _editorTitle.value)
      }
  }

  fun insertSnippet(snippet: String) {
    val current = _editorCode.value
    val next = if (current.isEmpty() || current.endsWith("\n")) "$current$snippet" else "$current\n$snippet"
    updateCode(next)
  }

  fun createNewScript(title: String) {
    viewModelScope.launch {
      val cleanTitle = title.trim().ifEmpty { "Script ${scripts.value.size + 1}" }
      val template =
        """-- $cleanTitle (Roblox Lua 5.1)
local gui = Instance.new("ScreenGui", gethui())
local frame = Instance.new("Frame", gui)
frame.Position = UDim2.new(0.1, 0, 0.15, 0)
frame.Size = UDim2.new(0.8, 0, 0.5, 0)
frame.BackgroundColor3 = Color3.fromRGB(24, 34, 48)
Instance.new("UICorner", frame)

local label = Instance.new("TextLabel", frame)
label.Position = UDim2.new(0.1, 0, 0.25, 0)
label.Size = UDim2.new(0.8, 0, 0.5, 0)
label.Text = "Hello from $cleanTitle"
print("Running $cleanTitle")
"""
      val newId =
        scriptRepo.saveScript(
          ScriptEntity(
            title = cleanTitle,
            category = "Custom",
            code = template,
            isBuiltIn = false,
          )
        )
      _activeScriptId.value = newId
      _editorTitle.value = cleanTitle
      _editorCode.value = template
      _hasUnsavedChanges.value = false
      compileNow(template, cleanTitle)
    }
  }

  fun saveActiveScript() {
    val id = _activeScriptId.value ?: return
    val existing = scripts.value.firstOrNull { it.id == id } ?: return
    viewModelScope.launch {
      scriptRepo.saveScript(
        existing.copy(
          title = _editorTitle.value,
          code = _editorCode.value,
          updatedAt = System.currentTimeMillis(),
        )
      )
      _hasUnsavedChanges.value = false
      appendLog(LogLevel.SYSTEM, "Saved '${_editorTitle.value}' to local workspace")
    }
  }

  fun deleteActiveScript() {
    val id = _activeScriptId.value ?: return
    val existing = scripts.value.firstOrNull { it.id == id } ?: return
    if (existing.isBuiltIn) return
    viewModelScope.launch {
      scriptRepo.deleteScript(id)
      _activeScriptId.value = null
      val fallback = scripts.value.firstOrNull { it.id != id }
      if (fallback != null) selectScript(fallback)
    }
  }

  fun compileManually() {
    compileDebounceJob?.cancel()
    val code = _editorCode.value
    val name = _editorTitle.value
    viewModelScope.launch {
      val result =
        withContext(Dispatchers.Default) {
          val t0 = System.nanoTime()
          try {
            val proto = compiler.compile(code, name)
            val elapsedUs = (System.nanoTime() - t0) / 1000L
            CompileStatus.Success(
              instructionCount = countInstructions(proto),
              protoCount = countProtos(proto),
              compileTimeUs = elapsedUs,
              disassembly = proto.disassemble(),
            )
          } catch (e: LuaCompileException) {
            CompileStatus.Error(
              message = e.message,
              line = e.line,
              column = e.column,
              stage = e.stage,
            )
          } catch (e: Exception) {
            CompileStatus.Error(
              message = e.message ?: "Unexpected compiler error",
              line = 1,
              column = 1,
              stage = "Compiler",
            )
          }
        }
      _compileStatus.value = result
      _compileTriggerSeq.value = _compileTriggerSeq.value + 1L
      when (result) {
        is CompileStatus.Success ->
          appendLog(
            LogLevel.SYSTEM,
            "Compiled '$name' -> Lua 5.1 OK (${result.instructionCount} opcodes, ${result.protoCount} chunks, ${result.compileTimeUs}µs)",
          )
        is CompileStatus.Error ->
          appendLog(
            LogLevel.ERROR,
            "Compile failed [${result.stage} L${result.line}:${result.column}]: ${result.message}",
          )
        CompileStatus.Idle -> Unit
      }
    }
  }

  fun compileNow(code: String = _editorCode.value, name: String = _editorTitle.value): FunctionProto? {
    val t0 = System.nanoTime()
    return try {
      val proto = compiler.compile(code, name)
      val elapsedUs = (System.nanoTime() - t0) / 1000L
      val totalInstrs = countInstructions(proto)
      val totalProtos = countProtos(proto)
      _compileStatus.value =
        CompileStatus.Success(
          instructionCount = totalInstrs,
          protoCount = totalProtos,
          compileTimeUs = elapsedUs,
          disassembly = proto.disassemble(),
        )
      proto
    } catch (e: LuaCompileException) {
      _compileStatus.value =
        CompileStatus.Error(
          message = e.message,
          line = e.line,
          column = e.column,
          stage = e.stage,
        )
      null
    }
  }

  private fun countInstructions(p: FunctionProto): Int =
    p.code.size + p.protos.sumOf { countInstructions(it) }

  private fun countProtos(p: FunctionProto): Int =
    1 + p.protos.sumOf { countProtos(it) }

  fun runScript() {
    stopExecution(silent = true)
    val code = _editorCode.value
    val name = _editorTitle.value
    val proto = compileNow(code, name)
    if (proto == null) {
      val err = _compileStatus.value as? CompileStatus.Error
      appendLog(
        LogLevel.ERROR,
        "Compile failed [${err?.stage} L${err?.line}:${err?.column}]: ${err?.message}",
      )
      return
    }

    val currentId = _activeScriptId.value
    val existing = scripts.value.firstOrNull { it.id == currentId }
    if (existing != null && _hasUnsavedChanges.value) {
      viewModelScope.launch {
        scriptRepo.saveScript(existing.copy(code = code, updatedAt = System.currentTimeMillis()))
        _hasUnsavedChanges.value = false
      }
    }

    frameRenderer.reset()
    guiProcessor.reset()
    frameCounter.set(0L)
    uiDirty.set(true)

    val scope = CoroutineScope(SupervisorJob() + vmDispatcher)
    sandboxScope = scope
    val vm = LuaVm(maxOpsPerExecution = settings.value.maxOpsPerExecution)
    val env =
      RobloxSandboxEnv(
        scope = scope,
        getExecutionIntervalMs = { settings.value.executionIntervalMs.toLong() },
        onLog = { lvl, msg -> appendLog(lvl, msg) },
        onUiDirty = { uiDirty.set(true) },
        onClipboard = { text -> copyToSystemClipboard(text) },
        getViewportSize = { guiViewportW to guiViewportH },
        isHttpGetEnabled = { settings.value.enableHttpGet },
      )
    env.attachToVm(vm)
    vm.globalEnv.rawSet(LuaValue.Str("__env_root"), env.genv)
    activeVm = vm
    activeEnv = env
    _isRunning.value = true

    appendLog(
      LogLevel.SYSTEM,
      "Compiled '$name' (${countInstructions(proto)} opcodes) • Executing RBX 5.1 @ ${settings.value.executionIntervalMs}ms",
    )

    // Render loop: updates whenever UI is dirty (and slows smoothly if idle)
    scope.launch {
      var lastFrameTime = System.currentTimeMillis()
      while (isActive) {
        val currentSettings = settings.value
        val intervalMs = currentSettings.executionIntervalMs.toLong().coerceIn(16L, 2000L)
        val hasTimedNotifs = env.notifications.isNotEmpty()
        if (uiDirty.getAndSet(false) || hasTimedNotifs) {
          val now = System.currentTimeMillis()
          val dt = (now - lastFrameTime).coerceAtLeast(1L)
          lastFrameTime = now
          emitSceneAndSnapshot(env, vm, dt)
        }
        delay(intervalMs)
      }
    }

    // Start RunService loop; auto-stops if loop errors out
    env.startServiceLoop(vm) {
      _isRunning.value = false
    }

    // Execute compiled main chunk + manage RUN/IDLE lifecycle accurately
    scope.launch {
      try {
        vm.resetOpCounters()
        vm.execute(proto, env.genv)
        uiDirty.set(true)
        emitSceneAndSnapshot(env, vm, settings.value.executionIntervalMs.toLong())

        if (!env.hasActiveLoopListeners()) {
          _isRunning.value = false
          appendLog(
            LogLevel.SYSTEM,
            "Completed (${vm.cumulativeOpsExecuted} ops). Canvas widgets remain interactive.",
          )
        } else {
          appendLog(
            LogLevel.SYSTEM,
            "Main chunk ready (${vm.cumulativeOpsExecuted} ops) • Active loop/tasks running.",
          )
          while (isActive && _isRunning.value && env.hasActiveLoopListeners()) {
            delay(140L)
          }
          if (isActive && _isRunning.value) {
            _isRunning.value = false
            uiDirty.set(true)
            emitSceneAndSnapshot(env, vm, settings.value.executionIntervalMs.toLong())
            appendLog(LogLevel.SYSTEM, "All RunService loops & tasks completed.")
          }
        }
      } catch (_: CancellationException) {
        // Halted explicitly by user
      } catch (e: LuaRuntimeException) {
        appendLog(LogLevel.ERROR, e.message)
        _isRunning.value = false
      } catch (e: Exception) {
        appendLog(LogLevel.ERROR, e.message ?: "Unknown VM error")
        _isRunning.value = false
      }
    }
  }

  private fun emitSceneAndSnapshot(env: RobloxSandboxEnv, vm: LuaVm, deltaMs: Long) {
    val currentSettings = settings.value
    val fNum = frameCounter.incrementAndGet()
    val scene =
      guiProcessor.process(
        roots = env.uiRoots(),
        drawings = env.drawings,
        viewportW = guiViewportW,
        viewportH = guiViewportH,
        frameNumber = fNum,
        deltaMs = deltaMs,
        monochromeGui = currentSettings.monochromeGuiPreview,
        notifications = env.activeNotifications(),
      )
    _guiScene.value = scene
    val snapshot =
      frameRenderer.render(
        roots = env.uiRoots(),
        drawings = env.drawings,
        columns = currentSettings.terminalColumns,
        rows = currentSettings.terminalRows,
        frameNumber = fNum,
        deltaMs = deltaMs,
        darkTheme = currentSettings.darkMode,
        monochrome = currentSettings.themePalette == ThemePalette.MONOCHROME,
      )
    _frameSnapshot.value = snapshot
    val diffLog = scene.diffSummary ?: snapshot.diffSummary
    if (currentSettings.showFrameDiffLogs && diffLog != null) {
      appendLog(LogLevel.FRAME, diffLog)
    }
    refreshInspector(env, vm)
  }

  fun stopExecution(silent: Boolean = false) {
    sandboxScope?.cancel()
    sandboxScope = null
    activeVm = null
    activeEnv = null
    val wasRunning = _isRunning.value
    _isRunning.value = false
    if (!silent && wasRunning) {
      appendLog(LogLevel.SYSTEM, "Execution halted by user.")
    }
  }

  fun triggerInteractiveButton(instanceId: Int) {
    val scope = sandboxScope ?: return
    val vm = activeVm ?: return
    val env = activeEnv ?: return
    scope.launch {
      env.triggerButtonClick(vm, instanceId)
      uiDirty.set(true)
      emitSceneAndSnapshot(env, vm, settings.value.executionIntervalMs.toLong())
    }
  }

  fun submitTextBoxInput(instanceId: Int, text: String) {
    val scope = sandboxScope ?: return
    val vm = activeVm ?: return
    val env = activeEnv ?: return
    scope.launch {
      env.submitTextBoxInput(vm, instanceId, text)
      uiDirty.set(true)
      emitSceneAndSnapshot(env, vm, settings.value.executionIntervalMs.toLong())
    }
  }

  fun updateGuiViewportDp(widthDp: Float, heightDp: Float) {
    if (kotlin.math.abs(widthDp - guiViewportW) < 2f && kotlin.math.abs(heightDp - guiViewportH) < 2f) {
      return
    }
    guiViewportW = widthDp.coerceAtLeast(120f)
    guiViewportH = heightDp.coerceAtLeast(80f)
    val env = activeEnv
    if (env == null) {
      refreshEmptyFrame()
    } else {
      val s = settings.value
      _guiScene.value =
        guiProcessor.process(
          roots = env.uiRoots(),
          drawings = env.drawings,
          viewportW = guiViewportW,
          viewportH = guiViewportH,
          frameNumber = frameCounter.get(),
          deltaMs = s.executionIntervalMs.toLong(),
          monochromeGui = s.monochromeGuiPreview,
          notifications = env.activeNotifications(),
        )
    }
  }

  fun onGuiCanvasDrag(
    instanceId: Int,
    dxNorm: Double,
    dyNorm: Double,
    dxLogical: Double,
    dyLogical: Double,
  ) {
    val scope = sandboxScope ?: return
    val vm = activeVm ?: return
    val env = activeEnv ?: return
    scope.launch {
      env.applyDragDelta(instanceId, dxNorm, dyNorm, dxLogical, dyLogical)
      val s = settings.value
      _guiScene.value =
        guiProcessor.process(
          roots = env.uiRoots(),
          drawings = env.drawings,
          viewportW = guiViewportW,
          viewportH = guiViewportH,
          frameNumber = frameCounter.get(),
          deltaMs = s.executionIntervalMs.toLong(),
          monochromeGui = s.monochromeGuiPreview,
          notifications = env.activeNotifications(),
        )
      refreshInspector(env, vm)
    }
  }

  fun deleteVirtualFile(path: String) {
    activeEnv?.virtualFs?.remove(path)
    val env = activeEnv
    val vm = activeVm
    if (env != null && vm != null) refreshInspector(env, vm)
  }

  fun clearTerminal() {
    _logs.value = emptyList()
  }

  fun copyLogsToClipboard() {
    val text = _logs.value.joinToString("\n") { "[${it.level.name}] ${it.message}" }
    copyToSystemClipboard(text)
    appendLog(LogLevel.SYSTEM, "Copied ${_logs.value.size} terminal lines to clipboard.")
  }

  fun reRenderFrameTheme(
    darkTheme: Boolean,
    cols: Int,
    rows: Int,
    monochrome: Boolean = settings.value.themePalette == ThemePalette.MONOCHROME,
    monochromeGui: Boolean = settings.value.monochromeGuiPreview,
  ) {
    val env = activeEnv
    val snapshot =
      frameRenderer.render(
        roots = env?.uiRoots() ?: emptyList(),
        drawings = env?.drawings ?: emptyList(),
        columns = cols,
        rows = rows,
        frameNumber = frameCounter.get(),
        deltaMs = settings.value.executionIntervalMs.toLong(),
        darkTheme = darkTheme,
        monochrome = monochrome,
      )
    _frameSnapshot.value = snapshot
    _guiScene.value =
      guiProcessor.process(
        roots = env?.uiRoots() ?: emptyList(),
        drawings = env?.drawings ?: emptyList(),
        viewportW = guiViewportW,
        viewportH = guiViewportH,
        frameNumber = frameCounter.get(),
        deltaMs = settings.value.executionIntervalMs.toLong(),
        monochromeGui = monochromeGui,
        notifications = env?.activeNotifications() ?: emptyList(),
      )
  }

  // Settings mutations
  fun setDarkMode(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepo.update { it.copy(darkMode = enabled) }
      val s = settings.value
      reRenderFrameTheme(enabled, s.terminalColumns, s.terminalRows, s.themePalette == ThemePalette.MONOCHROME)
    }
  }

  fun setThemePalette(palette: ThemePalette) {
    viewModelScope.launch {
      settingsRepo.update { it.copy(themePalette = palette) }
      val s = settings.value
      reRenderFrameTheme(s.darkMode, s.terminalColumns, s.terminalRows, palette == ThemePalette.MONOCHROME)
    }
  }

  fun setExecutionIntervalMs(ms: Int) {
    viewModelScope.launch {
      settingsRepo.update { it.copy(executionIntervalMs = ms.coerceIn(16, 1000)) }
    }
  }

  fun setScrollSpeed(speed: ScrollSpeed) {
    viewModelScope.launch { settingsRepo.update { it.copy(scrollSpeed = speed) } }
  }

  fun setCodeFont(font: CodeFontFamily) {
    viewModelScope.launch { settingsRepo.update { it.copy(codeFont = font) } }
  }

  fun setEditorFontSize(sizeSp: Float) {
    viewModelScope.launch { settingsRepo.update { it.copy(editorFontSizeSp = sizeSp) } }
  }

  fun setTerminalFontSize(sizeSp: Float) {
    viewModelScope.launch { settingsRepo.update { it.copy(terminalFontSizeSp = sizeSp) } }
  }

  fun setShowLineNumbers(show: Boolean) {
    viewModelScope.launch { settingsRepo.update { it.copy(showLineNumbers = show) } }
  }

  fun setSyntaxHighlighting(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.update { it.copy(syntaxHighlighting = enabled) } }
  }

  fun setAutoScrollTerminal(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.update { it.copy(autoScrollTerminal = enabled) } }
  }

  fun setTerminalColumns(cols: Int) {
    viewModelScope.launch {
      settingsRepo.update { it.copy(terminalColumns = cols) }
      reRenderFrameTheme(settings.value.darkMode, cols, settings.value.terminalRows)
    }
  }

  fun setShowFrameDiffLogs(show: Boolean) {
    viewModelScope.launch { settingsRepo.update { it.copy(showFrameDiffLogs = show) } }
  }

  fun setMaxOpsPerExecution(maxOps: Int) {
    viewModelScope.launch { settingsRepo.update { it.copy(maxOpsPerExecution = maxOps) } }
  }

  fun setMonochromeGuiPreview(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepo.update { it.copy(monochromeGuiPreview = enabled) }
      val s = settings.value
      reRenderFrameTheme(
        darkTheme = s.darkMode,
        cols = s.terminalColumns,
        rows = s.terminalRows,
        monochrome = s.themePalette == ThemePalette.MONOCHROME,
        monochromeGui = enabled,
      )
    }
  }

  fun setEnableHttpGet(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.update { it.copy(enableHttpGet = enabled) } }
  }

  fun setSyntaxGradientEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.update { it.copy(syntaxGradientEnabled = enabled) } }
  }

  fun setSyntaxGradientMoving(moving: Boolean) {
    viewModelScope.launch { settingsRepo.update { it.copy(syntaxGradientMoving = moving) } }
  }

  fun setFontGradientMoving(moving: Boolean) {
    viewModelScope.launch { settingsRepo.update { it.copy(fontGradientMoving = moving) } }
  }

  fun setGradientPreset(preset: SyntaxGradientPreset) {
    viewModelScope.launch { settingsRepo.update { it.copy(gradientPreset = preset) } }
  }

  fun setGradientSpeed(speed: Float) {
    viewModelScope.launch { settingsRepo.update { it.copy(gradientSpeed = speed.coerceIn(0.2f, 3f)) } }
  }

  fun setSyntaxTokenColor(tokenKey: String, argb: Long) {
    viewModelScope.launch {
      settingsRepo.update { cur ->
        when (tokenKey) {
          "keyword" -> cur.copy(syntaxKeywordArgb = argb)
          "type" -> cur.copy(syntaxTypeArgb = argb)
          "executor" -> cur.copy(syntaxExecutorArgb = argb)
          "builtin" -> cur.copy(syntaxBuiltinArgb = argb)
          "string" -> cur.copy(syntaxStringArgb = argb)
          "number" -> cur.copy(syntaxNumberArgb = argb)
          "comment" -> cur.copy(syntaxCommentArgb = argb)
          "operator" -> cur.copy(syntaxOperatorArgb = argb)
          else -> cur
        }
      }
    }
  }

  fun resetSyntaxColors() {
    viewModelScope.launch {
      settingsRepo.update {
        it.copy(
          syntaxKeywordArgb = 0L,
          syntaxTypeArgb = 0L,
          syntaxExecutorArgb = 0L,
          syntaxBuiltinArgb = 0L,
          syntaxStringArgb = 0L,
          syntaxNumberArgb = 0L,
          syntaxCommentArgb = 0L,
          syntaxOperatorArgb = 0L,
        )
      }
    }
  }

  fun setBackgroundMode(mode: BackgroundMode) {
    viewModelScope.launch { settingsRepo.update { it.copy(backgroundMode = mode) } }
  }

  fun setGalaxySpeed(speed: Float) {
    viewModelScope.launch { settingsRepo.update { it.copy(galaxySpeed = speed.coerceIn(0.2f, 3f)) } }
  }

  fun setGalaxyStarDensity(density: Float) {
    viewModelScope.launch { settingsRepo.update { it.copy(galaxyStarDensity = density.coerceIn(0.25f, 2f)) } }
  }

  fun setBackgroundBlurDp(blurDp: Float) {
    viewModelScope.launch { settingsRepo.update { it.copy(backgroundBlurDp = blurDp.coerceIn(0f, 24f)) } }
  }

  fun setBackgroundDimAlpha(alpha: Float) {
    viewModelScope.launch { settingsRepo.update { it.copy(backgroundDimAlpha = alpha.coerceIn(0f, 0.85f)) } }
  }

  fun setPanelOpacity(opacity: Float) {
    viewModelScope.launch { settingsRepo.update { it.copy(panelOpacity = opacity.coerceIn(0.35f, 1f)) } }
  }

  fun setPanelShadowDp(shadowDp: Float) {
    viewModelScope.launch { settingsRepo.update { it.copy(panelShadowDp = shadowDp.coerceIn(0f, 24f)) } }
  }

  fun setBackgroundVideoMuted(muted: Boolean) {
    viewModelScope.launch { settingsRepo.update { it.copy(backgroundVideoMuted = muted) } }
  }

  fun pickCustomBackground(uri: Uri) {
    viewModelScope.launch {
      val app = getApplication<Application>()
      val mime = runCatching { app.contentResolver.getType(uri) }.getOrNull().orEmpty()
      val isVideo =
        mime.startsWith("video/", ignoreCase = true) ||
          uri.toString().lowercase().let {
            it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".webm") || it.endsWith(".mov")
          }
      val oldImagePath = settings.value.customBackgroundPath
      val oldVideoPath = settings.value.customVideoPath
      val savedPath =
        withContext(Dispatchers.IO) {
          try {
            val ext = if (isVideo) "mp4" else "img"
            val dest = File(app.filesDir, "custom_bg_${System.currentTimeMillis()}.$ext")
            app.contentResolver.openInputStream(uri)?.use { input ->
              dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            val previous = if (isVideo) oldVideoPath else oldImagePath
            if (previous.isNotEmpty() && previous != dest.absolutePath) {
              runCatching { File(previous).delete() }
            }
            dest.absolutePath
          } catch (_: Exception) {
            null
          }
        }
      if (savedPath != null) {
        if (isVideo) {
          settingsRepo.update {
            it.copy(
              customVideoPath = savedPath,
              backgroundMode = BackgroundMode.CUSTOM_VIDEO,
            )
          }
          appendLog(LogLevel.SYSTEM, "Updated custom MP4 video wallpaper.")
        } else {
          settingsRepo.update {
            it.copy(
              customBackgroundPath = savedPath,
              backgroundMode = BackgroundMode.CUSTOM_IMAGE,
            )
          }
          appendLog(LogLevel.SYSTEM, "Updated custom workspace wallpaper.")
        }
      } else {
        appendLog(LogLevel.WARN, "Failed to read selected media.")
      }
    }
  }

  fun clearCustomBackground() {
    viewModelScope.launch {
      val oldImagePath = settings.value.customBackgroundPath
      val oldVideoPath = settings.value.customVideoPath
      withContext(Dispatchers.IO) {
        if (oldImagePath.isNotEmpty()) runCatching { File(oldImagePath).delete() }
        if (oldVideoPath.isNotEmpty()) runCatching { File(oldVideoPath).delete() }
      }
      settingsRepo.update {
        it.copy(
          customBackgroundPath = "",
          customVideoPath = "",
          backgroundMode = BackgroundMode.GALAXY,
        )
      }
    }
  }

  fun resetSettings() {
    viewModelScope.launch {
      settingsRepo.resetDefaults()
    }
  }

  private fun appendLog(level: LogLevel, message: String) {
    val entry =
      TerminalLogEntry(
        id = logSeq.getAndIncrement(),
        timestampMs = System.currentTimeMillis(),
        level = level,
        message = message,
      )
    val current = _logs.value
    val next = if (current.size >= 400) current.drop(current.size - 399) + entry else current + entry
    _logs.value = next
  }

  private fun refreshInspector(env: RobloxSandboxEnv, vm: LuaVm) {
    val genvList =
      env.genv.entries.entries.map { (k, v) ->
        k.toLuaValue().toString() to v.toString().take(80)
      }
    val fsList = env.virtualFs.entries.map { it.key to it.value }.sortedBy { it.first }
    val tree = mutableListOf<UiTreeNodeItem>()
    fun walk(inst: RbxInstance, depth: Int) {
      val text = (inst.properties["Text"] as? LuaValue.Str)?.value
      val color = (inst.properties["BackgroundColor3"] as? LuaValue.Userdata)?.data as? RbxColor3
      val summary =
        buildString {
          if (text != null) append("\"$text\" ")
          if (color != null) append(color.rgbLabel())
        }.trim()
      tree.add(UiTreeNodeItem(inst.id, depth, inst.name, inst.className, summary))
      inst.children.toList().forEach { walk(it, depth + 1) }
    }
    env.uiRoots().forEach { walk(it, 0) }
    _inspectorState.value =
      SandboxInspectorState(
        genvEntries = genvList,
        virtualFiles = fsList,
        uiTree = tree,
        drawingCount = env.drawings.size,
        opsExecuted = vm.cumulativeOpsExecuted,
      )
  }

  private fun copyToSystemClipboard(text: String) {
    val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("BloxLua", text))
  }

  override fun onCleared() {
    super.onCleared()
    sandboxScope?.cancel()
  }
}
