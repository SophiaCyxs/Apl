package com.example.lua

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

enum class LogLevel {
  PRINT,
  WARN,
  ERROR,
  SYSTEM,
  FRAME,
  EXEC,
}

data class TerminalLogEntry(
  val id: Long,
  val timestampMs: Long,
  val level: LogLevel,
  val message: String,
)

private class LuaCoroutine(
  val inChannel: Channel<List<LuaValue>> = Channel(1),
  val outChannel: Channel<List<LuaValue>> = Channel(1),
  @Volatile var status: String = "suspended",
)

class RobloxSandboxEnv(
  private val scope: CoroutineScope,
  private val getExecutionIntervalMs: () -> Long,
  private val onLog: (LogLevel, String) -> Unit,
  private val onUiDirty: () -> Unit,
  private val onClipboard: (String) -> Unit,
  private val getViewportSize: () -> Pair<Float, Float> = { 380f to 236f },
  private val isHttpGetEnabled: () -> Boolean = { true },
) {
  val renv = LuaValue.Table()
  val genv = LuaValue.Table()
  val virtualFs = ConcurrentHashMap<String, String>()
  val virtualFolders = mutableSetOf("workspace", "configs")
  val drawings = mutableListOf<RbxDrawingObject>()
  val allInstances = mutableListOf<RbxInstance>()
  val notifications = mutableListOf<GuiNotification>()
  private val notifIdSeq = AtomicInteger(1)

  val gameInstance = RbxInstance("DataModel", "game")
  val workspaceInstance = RbxInstance("Workspace", "Workspace")
  val currentCamera = RbxInstance("Camera", "CurrentCamera")
  val playersService = RbxInstance("Players", "Players")
  val localPlayer = RbxInstance("Player", "LocalPlayer")
  val playerGui = RbxInstance("PlayerGui", "PlayerGui")
  val coreGui = RbxInstance("CoreGui", "CoreGui")
  val hiddenGui = RbxInstance("HiddenGui", "BloxHiddenGui")
  val replicatedStorage = RbxInstance("ReplicatedStorage", "ReplicatedStorage")
  val starterGui = RbxInstance("StarterGui", "StarterGui")
  val runService = RbxInstance("RunService", "RunService")
  val tweenService = RbxInstance("TweenService", "TweenService")
  val userInputService = RbxInstance("UserInputService", "UserInputService")
  val httpService = RbxInstance("HttpService", "HttpService")
  val textService = RbxInstance("TextService", "TextService")
  val guiService = RbxInstance("GuiService", "GuiService")
  val debrisService = RbxInstance("Debris", "Debris")
  val contentProvider = RbxInstance("ContentProvider", "ContentProvider")
  val collectionService = RbxInstance("CollectionService", "CollectionService")
  val lightingService = RbxInstance("Lighting", "Lighting")
  val soundService = RbxInstance("SoundService", "SoundService")
  val localScriptInstance = RbxInstance("LocalScript", "ExecutorScript")

  val renderStepped = RbxSignal("RenderStepped")
  val heartbeat = RbxSignal("Heartbeat")
  val stepped = RbxSignal("Stepped")
  val inputBegan = RbxSignal("InputBegan")
  val inputEnded = RbxSignal("InputEnded")
  val inputChanged = RbxSignal("InputChanged")

  val activeTaskCount = AtomicInteger(0)
  private val startTimeMs = System.currentTimeMillis()
  private val compiler = LuaCompiler()
  private val hooks = IdentityHashMap<LuaValue, LuaValue>()
  private val moduleCache = IdentityHashMap<RbxInstance, LuaValue>()
  private val executedLocalScripts = mutableSetOf<Int>()
  private val collectionTags = mutableMapOf<String, MutableSet<RbxInstance>>()
  private val protectedGuis = mutableSetOf<Int>()
  private var attachedVm: LuaVm? = null
  private var runningCoroutine: LuaCoroutine? = null

  init {
    workspaceInstance.setParent(gameInstance)
    currentCamera.setParent(workspaceInstance)
    playersService.setParent(gameInstance)
    localPlayer.setParent(playersService)
    localPlayer.properties["UserId"] = LuaValue.Num(1337420.0)
    localPlayer.properties["DisplayName"] = LuaValue.Str("BloxDev")
    playerGui.setParent(localPlayer)
    coreGui.setParent(gameInstance)
    replicatedStorage.setParent(gameInstance)
    starterGui.setParent(gameInstance)
    runService.setParent(gameInstance)
    tweenService.setParent(gameInstance)
    userInputService.setParent(gameInstance)
    httpService.setParent(gameInstance)
    textService.setParent(gameInstance)
    guiService.setParent(gameInstance)
    debrisService.setParent(gameInstance)
    contentProvider.setParent(gameInstance)
    collectionService.setParent(gameInstance)
    lightingService.setParent(gameInstance)
    soundService.setParent(gameInstance)

    allInstances.addAll(
      listOf(
        gameInstance,
        workspaceInstance,
        currentCamera,
        playersService,
        localPlayer,
        playerGui,
        coreGui,
        hiddenGui,
        replicatedStorage,
        starterGui,
        runService,
        tweenService,
        userInputService,
        httpService,
        textService,
        guiService,
        debrisService,
        contentProvider,
        collectionService,
        lightingService,
        soundService,
        localScriptInstance,
      )
    )
  }

  fun resolveHook(fn: LuaValue): LuaValue = hooks[fn] ?: fn

  fun uiRoots(): List<RbxInstance> = listOf(playerGui, coreGui, hiddenGui, starterGui)

  fun hasActiveLoopListeners(): Boolean =
    renderStepped.hasListeners() ||
      heartbeat.hasListeners() ||
      stepped.hasListeners() ||
      activeTaskCount.get() > 0

  fun activeNotifications(): List<GuiNotification> {
    val now = System.currentTimeMillis()
    notifications.removeAll { (now - it.createdAtMs) > (it.durationSec * 1000.0).toLong() }
    return notifications.takeLast(4)
  }

  fun attachToVm(vm: LuaVm) {
    attachedVm = vm
    vm.hookResolver = { fn -> resolveHook(fn) }
    populateLua51Builtins(vm, renv)
    populateRobloxTypes(renv)
    populateRobloxGlobals(vm, renv)
    populateExecutorApis(vm, renv)

    // genv inherits from renv via __index so scripts can read Roblox globals while writing to getgenv()
    val genvMeta = LuaValue.Table()
    genvMeta.rawSet(LuaValue.Str("__index"), renv)
    genv.metatable = genvMeta
  }

  fun startServiceLoop(vm: LuaVm, onLoopError: ((String) -> Unit)? = null) {
    scope.launch {
      var lastTick = System.currentTimeMillis()
      while (isActive) {
        val interval = getExecutionIntervalMs().coerceIn(16L, 2000L)
        delay(interval)
        if (!renderStepped.hasListeners() && !heartbeat.hasListeners() && !stepped.hasListeners()) {
          lastTick = System.currentTimeMillis()
          continue
        }
        val now = System.currentTimeMillis()
        val dt = ((now - lastTick).coerceAtLeast(1L)) / 1000.0
        lastTick = now
        try {
          vm.resetFrameOps()
          stepped.fire(vm, listOf(LuaValue.Num((now - startTimeMs) / 1000.0), LuaValue.Num(dt)))
          renderStepped.fire(vm, listOf(LuaValue.Num(dt)))
          heartbeat.fire(vm, listOf(LuaValue.Num(dt)))
        } catch (e: Exception) {
          val msg = e.message ?: "RunService error"
          onLog(LogLevel.ERROR, "RunService halted: $msg")
          renderStepped.connections.clear()
          heartbeat.connections.clear()
          stepped.connections.clear()
          onLoopError?.invoke(msg)
        }
      }
    }
  }

  private fun isScriptRunnableContainer(inst: RbxInstance): Boolean {
    var cur = inst.parent
    while (cur != null) {
      if (cur === playerGui || cur === coreGui || cur === hiddenGui || cur === starterGui || cur === localPlayer || cur === workspaceInstance) {
        return true
      }
      cur = cur.parent
    }
    return false
  }

  private fun maybeScheduleLocalScript(inst: RbxInstance) {
    if (inst.className != "LocalScript") return
    if (inst === localScriptInstance) return
    val disabled = (inst.properties["Disabled"] as? LuaValue.Bool)?.value == true
    val enabled = (inst.properties["Enabled"] as? LuaValue.Bool)?.value ?: true
    val src = (inst.properties["Source"] as? LuaValue.Str)?.value ?: ""
    if (disabled || !enabled || src.isBlank() || !isScriptRunnableContainer(inst)) return
    if (!executedLocalScripts.add(inst.id)) return
    val vm = attachedVm ?: return

    activeTaskCount.incrementAndGet()
    scope.launch {
      try {
        val proto = compiler.compile(src, inst.name)
        val localEnv = LuaValue.Table()
        val mt = LuaValue.Table().apply { rawSet(LuaValue.Str("__index"), genv) }
        localEnv.metatable = mt
        localEnv.rawSet(LuaValue.Str("script"), wrapInstance(inst))
        val upvals = Array(proto.upvalues.size) { UpvalueCell(LuaValue.Nil) }
        vm.callFunction(LuaValue.Closure(proto, upvals, localEnv), emptyList())
        onUiDirty()
      } catch (e: Exception) {
        onLog(LogLevel.ERROR, "LocalScript '${inst.name}': ${e.message ?: "runtime error"}")
      } finally {
        activeTaskCount.decrementAndGet()
      }
    }
  }

  suspend fun submitTextBoxInput(vm: LuaVm, instanceId: Int, text: String) {
    val target = allInstances.firstOrNull { it.id == instanceId } ?: return
    target.properties["Text"] = LuaValue.Str(text)
    onLog(LogLevel.EXEC, "TextBox -> ${target.name} = \"$text\"")
    try {
      vm.resetFrameOps()
      target.focused.fire(vm, emptyList())
      target.changed.fire(vm, listOf(LuaValue.Str("Text")))
      target.propertyChangedSignals["Text"]?.fire(vm, emptyList())
      target.focusLost.fire(vm, listOf(LuaValue.Bool(true), LuaValue.Nil))
      onUiDirty()
    } catch (e: Exception) {
      onLog(LogLevel.ERROR, e.message ?: "TextBox callback error")
    }
  }

  suspend fun triggerButtonClick(vm: LuaVm, instanceId: Int) {
    val target = allInstances.firstOrNull { it.id == instanceId } ?: return
    onLog(LogLevel.EXEC, "Interact -> ${target.name} (${target.className})")
    try {
      vm.resetFrameOps()
      target.mouseEnter.fire(vm, listOf(LuaValue.Num(target.liveAbsolutePosition.x), LuaValue.Num(target.liveAbsolutePosition.y)))
      val inputObj = LuaValue.Table().apply {
        rawSet(LuaValue.Str("UserInputType"), LuaValue.Userdata("EnumItem", RbxEnumItem("UserInputType", "Touch", 3)))
        rawSet(
          LuaValue.Str("Position"),
          LuaValue.Userdata(
            "Vector3",
            RbxVector3(
              target.liveAbsolutePosition.x + target.liveAbsoluteSize.x * 0.5,
              target.liveAbsolutePosition.y + target.liveAbsoluteSize.y * 0.5,
              0.0,
            ),
          ),
        )
      }
      target.inputBegan.fire(vm, listOf(inputObj))
      target.mouseButton1Down.fire(vm, emptyList())
      target.mouseButton1Up.fire(vm, emptyList())
      target.mouseButton1Click.fire(vm, emptyList())
      target.activated.fire(vm, listOf(inputObj, LuaValue.Num(1.0)))
      target.inputEnded.fire(vm, listOf(inputObj))
      target.mouseLeave.fire(vm, listOf(LuaValue.Num(target.liveAbsolutePosition.x), LuaValue.Num(target.liveAbsolutePosition.y)))
      inputBegan.fire(vm, listOf(inputObj, LuaValue.Bool(true)))
      inputEnded.fire(vm, listOf(inputObj, LuaValue.Bool(true)))
      onUiDirty()
    } catch (e: Exception) {
      onLog(LogLevel.ERROR, e.message ?: "Interaction callback error")
    }
  }

  fun applyDragDelta(instanceId: Int, dxScale: Double, dyScale: Double, dxPx: Double, dyPx: Double) {
    val target = allInstances.firstOrNull { it.id == instanceId } ?: return
    if (target.className == "ScrollingFrame") {
      val cur = (target.properties["CanvasPosition"] as? LuaValue.Userdata)?.data as? RbxVector2 ?: RbxVector2()
      val nextY = (cur.y - dyPx).coerceAtLeast(0.0)
      val nextX = (cur.x - dxPx).coerceAtLeast(0.0)
      target.properties["CanvasPosition"] = wrapVector2(RbxVector2(nextX, nextY))
      onUiDirty()
    } else if ((target.properties["Draggable"] as? LuaValue.Bool)?.value == true) {
      val cur = (target.properties["Position"] as? LuaValue.Userdata)?.data as? RbxUDim2 ?: RbxUDim2()
      val next = RbxUDim2(
        xScale = (cur.xScale + dxScale).coerceIn(-0.5, 0.95),
        xOffset = cur.xOffset,
        yScale = (cur.yScale + dyScale).coerceIn(-0.5, 0.95),
        yOffset = cur.yOffset,
      )
      target.properties["Position"] = wrapUDim2(next)
      onUiDirty()
    }
  }

  private fun populateLua51Builtins(vm: LuaVm, env: LuaValue.Table) {
    fun reg(name: String, fn: suspend (LuaVm, List<LuaValue>) -> List<LuaValue>) {
      env.rawSet(LuaValue.Str(name), LuaValue.NativeFunc(name, true, fn))
    }

    reg("print") { _, args ->
      onLog(LogLevel.PRINT, args.joinToString("\t") { it.toString() })
      emptyList()
    }
    reg("warn") { _, args ->
      onLog(LogLevel.WARN, args.joinToString("\t") { it.toString() })
      emptyList()
    }
    reg("error") { _, args ->
      val msg = args.firstOrNull()?.toString() ?: "error"
      throw LuaRuntimeException(msg)
    }
    reg("assert") { _, args ->
      val cond = args.firstOrNull() ?: LuaValue.Nil
      if (!cond.isTruthy()) {
        val msg = args.getOrNull(1)?.toString() ?: "assertion failed!"
        throw LuaRuntimeException(msg)
      }
      args
    }
    reg("type") { _, args ->
      listOf(LuaValue.Str(args.firstOrNull()?.luaTypeName() ?: "nil"))
    }
    reg("typeof") { _, args ->
      listOf(LuaValue.Str(args.firstOrNull()?.typeofName() ?: "nil"))
    }
    reg("tostring") { callVm, args ->
      val v = args.firstOrNull() ?: LuaValue.Nil
      val ts = (v as? LuaValue.Table)?.metatable?.get(LuaValue.Str("__tostring"))
        ?: (v as? LuaValue.Userdata)?.metatable?.get(LuaValue.Str("__tostring"))
      if (ts != null && ts !is LuaValue.Nil) {
        callVm.callFunction(ts, listOf(v))
      } else {
        listOf(LuaValue.Str(v.toString()))
      }
    }
    reg("tonumber") { _, args ->
      val v = args.firstOrNull() ?: LuaValue.Nil
      val base = args.getOrNull(1)?.asInt() ?: 10
      val res =
        when (v) {
          is LuaValue.Num -> v
          is LuaValue.Str -> {
            val s = v.value.trim()
            if (base == 10) s.toDoubleOrNull()?.let { LuaValue.Num(it) } ?: LuaValue.Nil
            else s.toLongOrNull(base)?.let { LuaValue.Num(it.toDouble()) } ?: LuaValue.Nil
          }
          else -> LuaValue.Nil
        }
      listOf(res)
    }
    reg("select") { _, args ->
      val first = args.firstOrNull() ?: LuaValue.Nil
      val rest = if (args.size > 1) args.subList(1, args.size) else emptyList()
      if (first is LuaValue.Str && first.value == "#") {
        listOf(LuaValue.Num(rest.size.toDouble()))
      } else {
        val idx = first.asInt()
        val start = if (idx < 0) (rest.size + idx).coerceAtLeast(0) else (idx - 1).coerceAtLeast(0)
        if (start >= rest.size) emptyList() else rest.subList(start, rest.size)
      }
    }
    reg("unpack") { _, args ->
      val tbl = args.firstOrNull() as? LuaValue.Table
        ?: throw LuaRuntimeException("bad argument #1 to 'unpack' (table expected)")
      val i = args.getOrNull(1)?.asInt() ?: 1
      val j = args.getOrNull(2)?.asInt() ?: tbl.arrayLength()
      val out = mutableListOf<LuaValue>()
      for (k in i..j) {
        out.add(tbl.get(LuaValue.Num(k.toDouble())))
      }
      out
    }
    reg("next") { _, args ->
      val tbl = args.firstOrNull() as? LuaValue.Table
        ?: throw LuaRuntimeException("bad argument #1 to 'next' (table expected)")
      val key = args.getOrNull(1) ?: LuaValue.Nil
      val keys = tbl.entries.keys.toList()
      if (key is LuaValue.Nil) {
        val firstKey = keys.firstOrNull() ?: return@reg listOf(LuaValue.Nil)
        listOf(firstKey.toLuaValue(), tbl.entries[firstKey] ?: LuaValue.Nil)
      } else {
        val idx = keys.indexOf(key.toKey())
        if (idx == -1 || idx + 1 >= keys.size) listOf(LuaValue.Nil)
        else {
          val nk = keys[idx + 1]
          listOf(nk.toLuaValue(), tbl.entries[nk] ?: LuaValue.Nil)
        }
      }
    }
    reg("pairs") { _, args ->
      val tbl = args.firstOrNull() ?: LuaValue.Nil
      val nextFn = env.get(LuaValue.Str("next"))
      listOf(nextFn, tbl, LuaValue.Nil)
    }
    reg("ipairs") { _, args ->
      val tbl = args.firstOrNull() as? LuaValue.Table
        ?: throw LuaRuntimeException("bad argument #1 to 'ipairs' (table expected)")
      val iter =
        LuaValue.NativeFunc("ipairs_iter") { _, iterArgs ->
          val t = iterArgs[0] as LuaValue.Table
          val i = iterArgs[1].asInt() + 1
          val v = t.get(LuaValue.Num(i.toDouble()))
          if (v is LuaValue.Nil) listOf(LuaValue.Nil) else listOf(LuaValue.Num(i.toDouble()), v)
        }
      listOf(iter, tbl, LuaValue.Num(0.0))
    }
    reg("setmetatable") { _, args ->
      val tbl = args.firstOrNull() as? LuaValue.Table
        ?: throw LuaRuntimeException("bad argument #1 to 'setmetatable' (table expected)")
      val mt = args.getOrNull(1)
      tbl.metatable = if (mt is LuaValue.Table) mt else null
      listOf(tbl)
    }
    reg("getmetatable") { _, args ->
      val v = args.firstOrNull()
      val mt =
        when (v) {
          is LuaValue.Table -> v.metatable
          is LuaValue.Userdata -> v.metatable
          else -> null
        }
      listOf(mt ?: LuaValue.Nil)
    }
    reg("rawget") { _, args ->
      val tbl = args.firstOrNull() as? LuaValue.Table
        ?: throw LuaRuntimeException("bad argument #1 to 'rawget' (table expected)")
      listOf(tbl.get(args.getOrNull(1) ?: LuaValue.Nil))
    }
    reg("rawset") { _, args ->
      val tbl = args.firstOrNull() as? LuaValue.Table
        ?: throw LuaRuntimeException("bad argument #1 to 'rawset' (table expected)")
      tbl.rawSet(args.getOrNull(1) ?: LuaValue.Nil, args.getOrNull(2) ?: LuaValue.Nil)
      listOf(tbl)
    }
    reg("rawequal") { _, args ->
      val a = args.getOrNull(0) ?: LuaValue.Nil
      val b = args.getOrNull(1) ?: LuaValue.Nil
      listOf(LuaValue.Bool(a.toKey() == b.toKey()))
    }
    reg("pcall") { callVm, args ->
      val fn = args.firstOrNull() ?: return@reg listOf(LuaValue.Bool(false), LuaValue.Str("attempt to call nil"))
      val callArgs = if (args.size > 1) args.subList(1, args.size) else emptyList()
      try {
        val res = callVm.callFunction(fn, callArgs)
        listOf(LuaValue.Bool(true)) + res
      } catch (e: LuaRuntimeException) {
        listOf(LuaValue.Bool(false), LuaValue.Str(e.message))
      }
    }
    reg("xpcall") { callVm, args ->
      val fn = args.getOrNull(0) ?: LuaValue.Nil
      val errHandler = args.getOrNull(1) ?: LuaValue.Nil
      val callArgs = if (args.size > 2) args.subList(2, args.size) else emptyList()
      try {
        val res = callVm.callFunction(fn, callArgs)
        listOf(LuaValue.Bool(true)) + res
      } catch (e: LuaRuntimeException) {
        val handled =
          if (errHandler !is LuaValue.Nil) {
            callVm.callFunction(errHandler, listOf(LuaValue.Str(e.message))).firstOrNull()
              ?: LuaValue.Str(e.message)
          } else {
            LuaValue.Str(e.message)
          }
        listOf(LuaValue.Bool(false), handled)
      }
    }

    // math library
    val mathTbl = LuaValue.Table()
    fun math1(name: String, f: (Double) -> Double) {
      mathTbl.rawSet(
        LuaValue.Str(name),
        LuaValue.NativeFunc("math.$name") { _, a ->
          listOf(LuaValue.Num(f(a.firstOrNull()?.asDoubleOrNull() ?: 0.0)))
        },
      )
    }
    mathTbl.rawSet(LuaValue.Str("pi"), LuaValue.Num(PI))
    mathTbl.rawSet(LuaValue.Str("huge"), LuaValue.Num(Double.POSITIVE_INFINITY))
    math1("abs") { abs(it) }
    math1("floor") { floor(it) }
    math1("ceil") { ceil(it) }
    math1("round") { round(it) }
    math1("sqrt") { sqrt(it) }
    math1("sin") { sin(it) }
    math1("cos") { cos(it) }
    math1("tan") { tan(it) }
    math1("asin") { asin(it) }
    math1("acos") { acos(it) }
    math1("atan") { atan(it) }
    math1("exp") { exp(it) }
    math1("log") { ln(it) }
    math1("log10") { log10(it) }
    math1("rad") { Math.toRadians(it) }
    math1("deg") { Math.toDegrees(it) }
    mathTbl.rawSet(
      LuaValue.Str("atan2"),
      LuaValue.NativeFunc("math.atan2") { _, a ->
        listOf(LuaValue.Num(atan2(a[0].asDoubleOrNull() ?: 0.0, a[1].asDoubleOrNull() ?: 0.0)))
      },
    )
    mathTbl.rawSet(
      LuaValue.Str("min"),
      LuaValue.NativeFunc("math.min") { _, a ->
        listOf(LuaValue.Num(a.mapNotNull { it.asDoubleOrNull() }.minOrNull() ?: 0.0))
      },
    )
    mathTbl.rawSet(
      LuaValue.Str("max"),
      LuaValue.NativeFunc("math.max") { _, a ->
        listOf(LuaValue.Num(a.mapNotNull { it.asDoubleOrNull() }.maxOrNull() ?: 0.0))
      },
    )
    mathTbl.rawSet(
      LuaValue.Str("clamp"),
      LuaValue.NativeFunc("math.clamp") { _, a ->
        val x = a.getOrNull(0)?.asDoubleOrNull() ?: 0.0
        val lo = a.getOrNull(1)?.asDoubleOrNull() ?: 0.0
        val hi = a.getOrNull(2)?.asDoubleOrNull() ?: 1.0
        listOf(LuaValue.Num(x.coerceIn(lo, hi)))
      },
    )
    mathTbl.rawSet(
      LuaValue.Str("random"),
      LuaValue.NativeFunc("math.random") { _, a ->
        val res =
          when (a.size) {
            0 -> Random.nextDouble()
            1 -> Random.nextInt(1, a[0].asInt().coerceAtLeast(1) + 1).toDouble()
            else -> {
              val lo = a[0].asInt()
              val hi = a[1].asInt()
              Random.nextInt(min(lo, hi), max(lo, hi) + 1).toDouble()
            }
          }
        listOf(LuaValue.Num(res))
      },
    )
    env.rawSet(LuaValue.Str("math"), mathTbl)

    // string library
    val strTbl = LuaValue.Table()
    strTbl.rawSet(
      LuaValue.Str("len"),
      LuaValue.NativeFunc("string.len") { _, a ->
        listOf(LuaValue.Num((a.firstOrNull()?.toString()?.length ?: 0).toDouble()))
      },
    )
    strTbl.rawSet(
      LuaValue.Str("upper"),
      LuaValue.NativeFunc("string.upper") { _, a ->
        listOf(LuaValue.Str(a.firstOrNull()?.toString()?.uppercase() ?: ""))
      },
    )
    strTbl.rawSet(
      LuaValue.Str("lower"),
      LuaValue.NativeFunc("string.lower") { _, a ->
        listOf(LuaValue.Str(a.firstOrNull()?.toString()?.lowercase() ?: ""))
      },
    )
    strTbl.rawSet(
      LuaValue.Str("rep"),
      LuaValue.NativeFunc("string.rep") { _, a ->
        val s = a.getOrNull(0)?.toString() ?: ""
        val n = a.getOrNull(1)?.asInt()?.coerceAtLeast(0) ?: 0
        listOf(LuaValue.Str(s.repeat(n)))
      },
    )
    strTbl.rawSet(
      LuaValue.Str("reverse"),
      LuaValue.NativeFunc("string.reverse") { _, a ->
        listOf(LuaValue.Str((a.firstOrNull()?.toString() ?: "").reversed()))
      },
    )
    strTbl.rawSet(
      LuaValue.Str("sub"),
      LuaValue.NativeFunc("string.sub") { _, a ->
        val s = a.getOrNull(0)?.toString() ?: ""
        val i = a.getOrNull(1)?.asInt() ?: 1
        val j = a.getOrNull(2)?.asInt() ?: -1
        val start = if (i < 0) (s.length + i).coerceAtLeast(0) else (i - 1).coerceAtLeast(0)
        val end = if (j < 0) (s.length + j + 1).coerceAtMost(s.length) else j.coerceAtMost(s.length)
        listOf(LuaValue.Str(if (start >= end || start >= s.length) "" else s.substring(start, end)))
      },
    )
    strTbl.rawSet(
      LuaValue.Str("find"),
      LuaValue.NativeFunc("string.find") { _, a ->
        val s = a.getOrNull(0)?.toString() ?: ""
        val pat = a.getOrNull(1)?.toString() ?: ""
        val init = ((a.getOrNull(2)?.asInt() ?: 1) - 1).coerceAtLeast(0)
        val plain = a.getOrNull(3)?.isTruthy() == true
        if (init > s.length) return@NativeFunc listOf(LuaValue.Nil)
        if (plain) {
          val idx = s.indexOf(pat, init)
          if (idx < 0) listOf(LuaValue.Nil)
          else listOf(LuaValue.Num((idx + 1).toDouble()), LuaValue.Num((idx + pat.length).toDouble()))
        } else {
          val regex = luaPatternToRegex(pat)
          val match = regex.find(s, init)
          if (match == null) {
            listOf(LuaValue.Nil)
          } else {
            val out = mutableListOf<LuaValue>(
              LuaValue.Num((match.range.first + 1).toDouble()),
              LuaValue.Num((match.range.last + 1).toDouble()),
            )
            for (g in 1 until match.groupValues.size) {
              out.add(LuaValue.Str(match.groupValues[g]))
            }
            out
          }
        }
      },
    )
    strTbl.rawSet(
      LuaValue.Str("match"),
      LuaValue.NativeFunc("string.match") { _, a ->
        val s = a.getOrNull(0)?.toString() ?: ""
        val pat = a.getOrNull(1)?.toString() ?: ""
        val init = ((a.getOrNull(2)?.asInt() ?: 1) - 1).coerceAtLeast(0)
        if (init > s.length) return@NativeFunc listOf(LuaValue.Nil)
        val match = luaPatternToRegex(pat).find(s, init) ?: return@NativeFunc listOf(LuaValue.Nil)
        if (match.groupValues.size > 1) {
          match.groupValues.drop(1).map { LuaValue.Str(it) }
        } else {
          listOf(LuaValue.Str(match.value))
        }
      },
    )
    strTbl.rawSet(
      LuaValue.Str("gmatch"),
      LuaValue.NativeFunc("string.gmatch") { _, a ->
        val s = a.getOrNull(0)?.toString() ?: ""
        val pat = a.getOrNull(1)?.toString() ?: ""
        val matches = luaPatternToRegex(pat).findAll(s).toList()
        var idx = 0
        val iter =
          LuaValue.NativeFunc("gmatch_iter") { _, _ ->
            if (idx >= matches.size) return@NativeFunc listOf(LuaValue.Nil)
            val m = matches[idx++]
            if (m.groupValues.size > 1) {
              m.groupValues.drop(1).map { LuaValue.Str(it) }
            } else {
              listOf(LuaValue.Str(m.value))
            }
          }
        listOf(iter)
      },
    )
    strTbl.rawSet(
      LuaValue.Str("gsub"),
      LuaValue.NativeFunc("string.gsub") { callVm, a ->
        val s = a.getOrNull(0)?.toString() ?: ""
        val pat = a.getOrNull(1)?.toString() ?: ""
        val replArg = a.getOrNull(2) ?: LuaValue.Str("")
        val maxRepl = a.getOrNull(3)?.asInt() ?: Int.MAX_VALUE
        val regex = luaPatternToRegex(pat)
        var count = 0
        val out = StringBuilder()
        var lastEnd = 0
        for (m in regex.findAll(s)) {
          if (count >= maxRepl) break
          out.append(s, lastEnd, m.range.first)
          val replacementText =
            when (replArg) {
              is LuaValue.Closure, is LuaValue.NativeFunc -> {
                val fnArgs =
                  if (m.groupValues.size > 1) m.groupValues.drop(1).map { LuaValue.Str(it) }
                  else listOf(LuaValue.Str(m.value))
                val res = callVm.callFunction(replArg, fnArgs).firstOrNull()
                if (res == null || res is LuaValue.Nil || res == LuaValue.Bool(false)) m.value else res.toString()
              }
              is LuaValue.Table -> {
                val key = if (m.groupValues.size > 1) m.groupValues[1] else m.value
                val res = replArg.get(LuaValue.Str(key))
                if (res is LuaValue.Nil || res == LuaValue.Bool(false)) m.value else res.toString()
              }
              else -> {
                var r = replArg.toString()
                for (gi in 0 until m.groupValues.size) {
                  r = r.replace("%$gi", m.groupValues[gi])
                }
                r
              }
            }
          out.append(replacementText)
          lastEnd = m.range.last + 1
          count++
        }
        out.append(s, lastEnd, s.length)
        listOf(LuaValue.Str(out.toString()), LuaValue.Num(count.toDouble()))
      },
    )
    strTbl.rawSet(
      LuaValue.Str("split"),
      LuaValue.NativeFunc("string.split") { _, a ->
        val s = a.getOrNull(0)?.toString() ?: ""
        val sep = a.getOrNull(1)?.toString() ?: ","
        val tbl = LuaValue.Table()
        s.split(sep).forEachIndexed { idx, part ->
          tbl.rawSet(LuaValue.Num((idx + 1).toDouble()), LuaValue.Str(part))
        }
        listOf(tbl)
      },
    )
    strTbl.rawSet(
      LuaValue.Str("format"),
      LuaValue.NativeFunc("string.format") { _, a ->
        val fmt = a.firstOrNull()?.toString() ?: ""
        val formatted = formatLuaString(fmt, a.drop(1))
        listOf(LuaValue.Str(formatted))
      },
    )
    strTbl.rawSet(
      LuaValue.Str("byte"),
      LuaValue.NativeFunc("string.byte") { _, a ->
        val s = a.getOrNull(0)?.toString() ?: ""
        val i = (a.getOrNull(1)?.asInt() ?: 1) - 1
        val j = (a.getOrNull(2)?.asInt() ?: (i + 1)) - 1
        val out = mutableListOf<LuaValue>()
        for (idx in i..j) {
          if (idx in s.indices) out.add(LuaValue.Num(s[idx].code.toDouble()))
        }
        if (out.isEmpty()) listOf(LuaValue.Nil) else out
      },
    )
    strTbl.rawSet(
      LuaValue.Str("char"),
      LuaValue.NativeFunc("string.char") { _, a ->
        val chars = a.map { it.asInt().toChar() }.joinToString("")
        listOf(LuaValue.Str(chars))
      },
    )
    env.rawSet(LuaValue.Str("string"), strTbl)

    // table library
    val tableTbl = LuaValue.Table()
    tableTbl.rawSet(
      LuaValue.Str("insert"),
      LuaValue.NativeFunc("table.insert") { _, a ->
        val t = a.firstOrNull() as? LuaValue.Table
          ?: throw LuaRuntimeException("table expected in table.insert")
        val len = t.arrayLength()
        if (a.size == 2) {
          t.rawSet(LuaValue.Num((len + 1).toDouble()), a[1])
        } else if (a.size >= 3) {
          val pos = a[1].asInt().coerceIn(1, len + 1)
          for (i in len downTo pos) {
            t.rawSet(LuaValue.Num((i + 1).toDouble()), t.get(LuaValue.Num(i.toDouble())))
          }
          t.rawSet(LuaValue.Num(pos.toDouble()), a[2])
        }
        emptyList()
      },
    )
    tableTbl.rawSet(
      LuaValue.Str("remove"),
      LuaValue.NativeFunc("table.remove") { _, a ->
        val t = a.firstOrNull() as? LuaValue.Table
          ?: throw LuaRuntimeException("table expected in table.remove")
        val len = t.arrayLength()
        if (len == 0) return@NativeFunc listOf(LuaValue.Nil)
        val pos = a.getOrNull(1)?.asInt()?.coerceIn(1, len) ?: len
        val removed = t.get(LuaValue.Num(pos.toDouble()))
        for (i in pos until len) {
          t.rawSet(LuaValue.Num(i.toDouble()), t.get(LuaValue.Num((i + 1).toDouble())))
        }
        t.rawSet(LuaValue.Num(len.toDouble()), LuaValue.Nil)
        listOf(removed)
      },
    )
    tableTbl.rawSet(
      LuaValue.Str("concat"),
      LuaValue.NativeFunc("table.concat") { _, a ->
        val t = a.firstOrNull() as? LuaValue.Table
          ?: throw LuaRuntimeException("table expected in table.concat")
        val sep = a.getOrNull(1)?.toString() ?: ""
        val start = a.getOrNull(2)?.asInt() ?: 1
        val end = a.getOrNull(3)?.asInt() ?: t.arrayLength()
        val parts = (start..end).map { t.get(LuaValue.Num(it.toDouble())).toString() }
        listOf(LuaValue.Str(parts.joinToString(sep)))
      },
    )
    tableTbl.rawSet(
      LuaValue.Str("find"),
      LuaValue.NativeFunc("table.find") { _, a ->
        val t = a.firstOrNull() as? LuaValue.Table ?: return@NativeFunc listOf(LuaValue.Nil)
        val target = a.getOrNull(1) ?: LuaValue.Nil
        val init = (a.getOrNull(2)?.asInt() ?: 1).coerceAtLeast(1)
        val len = t.arrayLength()
        for (i in init..len) {
          if (t.get(LuaValue.Num(i.toDouble())).toKey() == target.toKey()) {
            return@NativeFunc listOf(LuaValue.Num(i.toDouble()))
          }
        }
        listOf(LuaValue.Nil)
      },
    )
    tableTbl.rawSet(
      LuaValue.Str("clear"),
      LuaValue.NativeFunc("table.clear") { _, a ->
        val t = a.firstOrNull() as? LuaValue.Table
        t?.entries?.clear()
        emptyList()
      },
    )
    tableTbl.rawSet(
      LuaValue.Str("clone"),
      LuaValue.NativeFunc("table.clone") { _, a ->
        val t = a.firstOrNull() as? LuaValue.Table ?: return@NativeFunc listOf(LuaValue.Table())
        val copy = LuaValue.Table()
        copy.entries.putAll(t.entries)
        copy.metatable = t.metatable
        listOf(copy)
      },
    )
    tableTbl.rawSet(
      LuaValue.Str("create"),
      LuaValue.NativeFunc("table.create") { _, a ->
        val count = a.getOrNull(0)?.asInt()?.coerceIn(0, 10_000) ?: 0
        val v = a.getOrNull(1) ?: LuaValue.Nil
        val t = LuaValue.Table()
        if (v !is LuaValue.Nil) {
          for (i in 1..count) t.rawSet(LuaValue.Num(i.toDouble()), v)
        }
        listOf(t)
      },
    )
    tableTbl.rawSet(LuaValue.Str("unpack"), env.get(LuaValue.Str("unpack")))
    tableTbl.rawSet(
      LuaValue.Str("pack"),
      LuaValue.NativeFunc("table.pack") { _, a ->
        val t = LuaValue.Table()
        a.forEachIndexed { idx, v -> t.rawSet(LuaValue.Num((idx + 1).toDouble()), v) }
        t.rawSet(LuaValue.Str("n"), LuaValue.Num(a.size.toDouble()))
        listOf(t)
      },
    )
    tableTbl.rawSet(
      LuaValue.Str("maxn"),
      LuaValue.NativeFunc("table.maxn") { _, a ->
        val t = a.firstOrNull() as? LuaValue.Table
        val maxIdx =
          t?.entries?.keys?.filterIsInstance<LuaKey.NumKey>()?.maxOfOrNull { it.v } ?: 0.0
        listOf(LuaValue.Num(maxIdx))
      },
    )
    tableTbl.rawSet(
      LuaValue.Str("getn"),
      LuaValue.NativeFunc("table.getn") { _, a ->
        val t = a.firstOrNull() as? LuaValue.Table
        listOf(LuaValue.Num((t?.arrayLength() ?: 0).toDouble()))
      },
    )
    tableTbl.rawSet(
      LuaValue.Str("sort"),
      LuaValue.NativeFunc("table.sort") { callVm, a ->
        val t = a.firstOrNull() as? LuaValue.Table
          ?: throw LuaRuntimeException("table expected in table.sort")
        val cmp = a.getOrNull(1)
        val len = t.arrayLength()
        val items = (1..len).map { t.get(LuaValue.Num(it.toDouble())) }.toMutableList()
        for (i in 1 until items.size) {
          val key = items[i]
          var j = i - 1
          while (j >= 0) {
            val shouldSwap =
              if (cmp != null && cmp !is LuaValue.Nil) {
                callVm.callFunction(cmp, listOf(key, items[j])).firstOrNull()?.isTruthy() == true
              } else {
                val ka = key.asDoubleOrNull()
                val jb = items[j].asDoubleOrNull()
                if (ka != null && jb != null) ka < jb else key.toString() < items[j].toString()
              }
            if (!shouldSwap) break
            items[j + 1] = items[j]
            j--
          }
          items[j + 1] = key
        }
        items.forEachIndexed { idx, v -> t.rawSet(LuaValue.Num((idx + 1).toDouble()), v) }
        emptyList()
      },
    )
    env.rawSet(LuaValue.Str("table"), tableTbl)

    // coroutine library
    val coTbl = LuaValue.Table()
    coTbl.rawSet(
      LuaValue.Str("create"),
      LuaValue.NativeFunc("coroutine.create") { callVm, a ->
        val fn = a.firstOrNull() ?: throw LuaRuntimeException("coroutine.create expects a function")
        val co = LuaCoroutine()
        scope.launch {
          try {
            val firstArgs = co.inChannel.receive()
            val results = callVm.callFunction(fn, firstArgs)
            co.status = "dead"
            co.outChannel.send(listOf(LuaValue.Bool(true)) + results)
          } catch (e: LuaRuntimeException) {
            co.status = "dead"
            co.outChannel.send(listOf(LuaValue.Bool(false), LuaValue.Str(e.message)))
          } catch (e: Exception) {
            co.status = "dead"
            co.outChannel.send(listOf(LuaValue.Bool(false), LuaValue.Str(e.message ?: "coroutine error")))
          }
        }
        listOf(LuaValue.Userdata("thread", co))
      },
    )
    val resumeImpl: suspend (LuaVm, List<LuaValue>) -> List<LuaValue> = { _, a ->
      val co = (a.firstOrNull() as? LuaValue.Userdata)?.data as? LuaCoroutine
        ?: throw LuaRuntimeException("bad argument #1 to 'resume' (thread expected)")
      if (co.status == "dead") {
        listOf(LuaValue.Bool(false), LuaValue.Str("cannot resume dead coroutine"))
      } else if (co.status == "running") {
        listOf(LuaValue.Bool(false), LuaValue.Str("cannot resume non-suspended coroutine"))
      } else {
        val prev = runningCoroutine
        runningCoroutine = co
        co.status = "running"
        val resumeArgs = if (a.size > 1) a.subList(1, a.size) else emptyList()
        co.inChannel.send(resumeArgs)
        val out = co.outChannel.receive()
        runningCoroutine = prev
        out
      }
    }
    coTbl.rawSet(LuaValue.Str("resume"), LuaValue.NativeFunc("coroutine.resume", true, resumeImpl))
    coTbl.rawSet(
      LuaValue.Str("yield"),
      LuaValue.NativeFunc("coroutine.yield") { _, a ->
        val co = runningCoroutine
          ?: throw LuaRuntimeException("attempt to yield from outside a coroutine")
        co.status = "suspended"
        co.outChannel.send(listOf(LuaValue.Bool(true)) + a)
        val nextArgs = co.inChannel.receive()
        co.status = "running"
        nextArgs
      },
    )
    coTbl.rawSet(
      LuaValue.Str("wrap"),
      LuaValue.NativeFunc("coroutine.wrap") { callVm, a ->
        val createFn = coTbl.get(LuaValue.Str("create"))
        val coUserdata = callVm.callFunction(createFn, a).first()
        val wrapper =
          LuaValue.NativeFunc("coroutine.wrapper") { innerVm, callArgs ->
            val res = resumeImpl(innerVm, listOf(coUserdata) + callArgs)
            val ok = res.firstOrNull()?.isTruthy() == true
            if (!ok) {
              throw LuaRuntimeException(res.getOrNull(1)?.toString() ?: "coroutine error")
            }
            if (res.size > 1) res.subList(1, res.size) else emptyList()
          }
        listOf(wrapper)
      },
    )
    coTbl.rawSet(
      LuaValue.Str("status"),
      LuaValue.NativeFunc("coroutine.status") { _, a ->
        val co = (a.firstOrNull() as? LuaValue.Userdata)?.data as? LuaCoroutine
        listOf(LuaValue.Str(co?.status ?: "dead"))
      },
    )
    coTbl.rawSet(
      LuaValue.Str("running"),
      LuaValue.NativeFunc("coroutine.running") { _, _ ->
        val co = runningCoroutine
        if (co == null) listOf(LuaValue.Nil, LuaValue.Bool(true))
        else listOf(LuaValue.Userdata("thread", co), LuaValue.Bool(false))
      },
    )
    coTbl.rawSet(
      LuaValue.Str("isyieldable"),
      LuaValue.NativeFunc("coroutine.isyieldable") { _, _ ->
        listOf(LuaValue.Bool(runningCoroutine != null))
      },
    )
    env.rawSet(LuaValue.Str("coroutine"), coTbl)

    // debug library
    val debugTbl = LuaValue.Table()
    debugTbl.rawSet(
      LuaValue.Str("traceback"),
      LuaValue.NativeFunc("debug.traceback") { _, a ->
        val msg = a.firstOrNull()?.toString() ?: ""
        val trace = if (msg.isEmpty()) "stack traceback:\n\t[BloxLua 5.1 VM]" else "$msg\nstack traceback:\n\t[BloxLua 5.1 VM]"
        listOf(LuaValue.Str(trace))
      },
    )
    debugTbl.rawSet(
      LuaValue.Str("getinfo"),
      LuaValue.NativeFunc("debug.getinfo") { _, a ->
        val fn = a.firstOrNull()
        val tbl = LuaValue.Table().apply {
          rawSet(LuaValue.Str("source"), LuaValue.Str("=BloxLua"))
          rawSet(LuaValue.Str("short_src"), LuaValue.Str("BloxLua"))
          rawSet(LuaValue.Str("what"), LuaValue.Str(if (fn is LuaValue.NativeFunc) "C" else "Lua"))
          rawSet(LuaValue.Str("name"), LuaValue.Str((fn as? LuaValue.NativeFunc)?.name ?: "fn"))
          rawSet(LuaValue.Str("nups"), LuaValue.Num(((fn as? LuaValue.Closure)?.upvalues?.size ?: 0).toDouble()))
          rawSet(LuaValue.Str("currentline"), LuaValue.Num(1.0))
        }
        listOf(tbl)
      },
    )
    debugTbl.rawSet(
      LuaValue.Str("info"),
      LuaValue.NativeFunc("debug.info") { _, a ->
        listOf(LuaValue.Str("BloxLua"), LuaValue.Num(1.0), LuaValue.Str("fn"))
      },
    )
    debugTbl.rawSet(
      LuaValue.Str("getupvalue"),
      LuaValue.NativeFunc("debug.getupvalue") { _, a ->
        val c = a.getOrNull(0) as? LuaValue.Closure ?: return@NativeFunc listOf(LuaValue.Nil)
        val idx = (a.getOrNull(1)?.asInt() ?: 1) - 1
        if (idx !in c.upvalues.indices) listOf(LuaValue.Nil)
        else listOf(LuaValue.Str("upval_${idx + 1}"), c.upvalues[idx].value)
      },
    )
    debugTbl.rawSet(
      LuaValue.Str("setupvalue"),
      LuaValue.NativeFunc("debug.setupvalue") { _, a ->
        val c = a.getOrNull(0) as? LuaValue.Closure ?: return@NativeFunc listOf(LuaValue.Nil)
        val idx = (a.getOrNull(1)?.asInt() ?: 1) - 1
        if (idx !in c.upvalues.indices) listOf(LuaValue.Nil)
        else {
          c.upvalues[idx].value = a.getOrNull(2) ?: LuaValue.Nil
          listOf(LuaValue.Str("upval_${idx + 1}"))
        }
      },
    )
    debugTbl.rawSet(LuaValue.Str("getmetatable"), env.get(LuaValue.Str("getmetatable")))
    debugTbl.rawSet(LuaValue.Str("setmetatable"), env.get(LuaValue.Str("setmetatable")))
    env.rawSet(LuaValue.Str("debug"), debugTbl)

    // require(moduleScript or name)
    reg("require") { callVm, args ->
      val target = args.firstOrNull() ?: throw LuaRuntimeException("require expects a ModuleScript or name")
      val modInst = (target as? LuaValue.Userdata)?.data as? RbxInstance
      if (modInst != null) {
        if (modInst.className != "ModuleScript") {
          throw LuaRuntimeException("Attempted to call require with invalid instance (${modInst.className})")
        }
        moduleCache[modInst]?.let { return@reg listOf(it) }
        val src = (modInst.properties["Source"] as? LuaValue.Str)?.value ?: ""
        val proto = compiler.compile(src, modInst.name)
        val modEnv = LuaValue.Table()
        modEnv.metatable = LuaValue.Table().apply { rawSet(LuaValue.Str("__index"), genv) }
        modEnv.rawSet(LuaValue.Str("script"), wrapInstance(modInst))
        val upvals = Array(proto.upvalues.size) { UpvalueCell(LuaValue.Nil) }
        val exported = callVm.callFunction(LuaValue.Closure(proto, upvals, modEnv), emptyList())
          .firstOrNull() ?: LuaValue.Table()
        moduleCache[modInst] = exported
        listOf(exported)
      } else {
        listOf(LuaValue.Table())
      }
    }

    // bit32 library (Roblox Lua 5.1 bitwise library)
    val bit32Tbl = LuaValue.Table()
    bit32Tbl.rawSet(
      LuaValue.Str("band"),
      LuaValue.NativeFunc("bit32.band") { _, a ->
        var acc = a.firstOrNull()?.asInt() ?: -1
        for (item in a.drop(1)) acc = acc and item.asInt()
        listOf(LuaValue.Num(acc.toUInt().toDouble()))
      },
    )
    bit32Tbl.rawSet(
      LuaValue.Str("bor"),
      LuaValue.NativeFunc("bit32.bor") { _, a ->
        var acc = 0
        for (item in a) acc = acc or item.asInt()
        listOf(LuaValue.Num(acc.toUInt().toDouble()))
      },
    )
    bit32Tbl.rawSet(
      LuaValue.Str("bxor"),
      LuaValue.NativeFunc("bit32.bxor") { _, a ->
        var acc = 0
        for (item in a) acc = acc xor item.asInt()
        listOf(LuaValue.Num(acc.toUInt().toDouble()))
      },
    )
    bit32Tbl.rawSet(
      LuaValue.Str("bnot"),
      LuaValue.NativeFunc("bit32.bnot") { _, a ->
        val v = (a.firstOrNull()?.asInt() ?: 0).inv()
        listOf(LuaValue.Num(v.toUInt().toDouble()))
      },
    )
    bit32Tbl.rawSet(
      LuaValue.Str("lshift"),
      LuaValue.NativeFunc("bit32.lshift") { _, a ->
        val x = a.getOrNull(0)?.asInt() ?: 0
        val disp = a.getOrNull(1)?.asInt() ?: 0
        listOf(LuaValue.Num((x shl disp).toUInt().toDouble()))
      },
    )
    bit32Tbl.rawSet(
      LuaValue.Str("rshift"),
      LuaValue.NativeFunc("bit32.rshift") { _, a ->
        val x = a.getOrNull(0)?.asInt() ?: 0
        val disp = a.getOrNull(1)?.asInt() ?: 0
        listOf(LuaValue.Num((x ushr disp).toUInt().toDouble()))
      },
    )
    env.rawSet(LuaValue.Str("bit32"), bit32Tbl)

    // os library
    val osTbl = LuaValue.Table()
    osTbl.rawSet(
      LuaValue.Str("clock"),
      LuaValue.NativeFunc("os.clock") { _, _ ->
        listOf(LuaValue.Num((System.currentTimeMillis() - startTimeMs) / 1000.0))
      },
    )
    osTbl.rawSet(
      LuaValue.Str("time"),
      LuaValue.NativeFunc("os.time") { _, _ ->
        listOf(LuaValue.Num((System.currentTimeMillis() / 1000L).toDouble()))
      },
    )
    env.rawSet(LuaValue.Str("os"), osTbl)

    // task + wait + tick + spawn + delay
    val waitImpl: suspend (LuaVm, List<LuaValue>) -> List<LuaValue> = { _, args ->
      val requestedSec = args.firstOrNull()?.asDoubleOrNull()
      val intervalMs = getExecutionIntervalMs().coerceAtLeast(16L)
      val waitMs =
        if (requestedSec == null || requestedSec <= 0.0) intervalMs
        else max(intervalMs, (requestedSec * 1000.0).toLong().coerceAtMost(5000L))
      val t0 = System.currentTimeMillis()
      delay(waitMs)
      val elapsed = (System.currentTimeMillis() - t0) / 1000.0
      listOf(LuaValue.Num(elapsed))
    }
    reg("wait", waitImpl)
    reg("tick") { _, _ -> listOf(LuaValue.Num(System.currentTimeMillis() / 1000.0)) }

    val taskTbl = LuaValue.Table()
    taskTbl.rawSet(LuaValue.Str("wait"), LuaValue.NativeFunc("task.wait", true, waitImpl))
    val spawnImpl: suspend (LuaVm, List<LuaValue>) -> List<LuaValue> = { callVm, args ->
      val fn = args.firstOrNull() ?: LuaValue.Nil
      val fnArgs = if (args.size > 1) args.subList(1, args.size) else emptyList()
      activeTaskCount.incrementAndGet()
      scope.launch {
        try {
          callVm.callFunction(fn, fnArgs)
        } catch (e: Exception) {
          onLog(LogLevel.ERROR, e.message ?: "Spawned task error")
        } finally {
          activeTaskCount.decrementAndGet()
        }
      }
      emptyList()
    }
    taskTbl.rawSet(LuaValue.Str("spawn"), LuaValue.NativeFunc("task.spawn", true, spawnImpl))
    taskTbl.rawSet(LuaValue.Str("defer"), LuaValue.NativeFunc("task.defer", true, spawnImpl))
    taskTbl.rawSet(LuaValue.Str("cancel"), LuaValue.NativeFunc("task.cancel") { _, _ -> emptyList() })
    reg("spawn", spawnImpl)
    val delayImpl: suspend (LuaVm, List<LuaValue>) -> List<LuaValue> = { callVm, args ->
      val sec = args.getOrNull(0)?.asDoubleOrNull() ?: 0.0
      val fn = args.getOrNull(1) ?: LuaValue.Nil
      val fnArgs = if (args.size > 2) args.subList(2, args.size) else emptyList()
      activeTaskCount.incrementAndGet()
      scope.launch {
        try {
          delay((sec * 1000.0).toLong().coerceAtLeast(16L))
          callVm.callFunction(fn, fnArgs)
        } catch (e: Exception) {
          onLog(LogLevel.ERROR, e.message ?: "Delayed task error")
        } finally {
          activeTaskCount.decrementAndGet()
        }
      }
      emptyList()
    }
    taskTbl.rawSet(LuaValue.Str("delay"), LuaValue.NativeFunc("task.delay", true, delayImpl))
    reg("delay", delayImpl)
    env.rawSet(LuaValue.Str("task"), taskTbl)
  }

  private fun populateRobloxTypes(env: LuaValue.Table) {
    // UDim
    val udimTbl = LuaValue.Table()
    udimTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("UDim.new") { _, a ->
        listOf(
          wrapUDim(
            RbxUDim(a.getOrNull(0)?.asDoubleOrNull() ?: 0.0, a.getOrNull(1)?.asDoubleOrNull() ?: 0.0)
          )
        )
      },
    )
    env.rawSet(LuaValue.Str("UDim"), udimTbl)

    // UDim2
    val udim2Tbl = LuaValue.Table()
    udim2Tbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("UDim2.new") { _, a ->
        listOf(
          wrapUDim2(
            RbxUDim2(
              a.getOrNull(0)?.asDoubleOrNull() ?: 0.0,
              a.getOrNull(1)?.asDoubleOrNull() ?: 0.0,
              a.getOrNull(2)?.asDoubleOrNull() ?: 0.0,
              a.getOrNull(3)?.asDoubleOrNull() ?: 0.0,
            )
          )
        )
      },
    )
    udim2Tbl.rawSet(
      LuaValue.Str("fromScale"),
      LuaValue.NativeFunc("UDim2.fromScale") { _, a ->
        listOf(
          wrapUDim2(
            RbxUDim2(
              xScale = a.getOrNull(0)?.asDoubleOrNull() ?: 0.0,
              yScale = a.getOrNull(1)?.asDoubleOrNull() ?: 0.0,
            )
          )
        )
      },
    )
    udim2Tbl.rawSet(
      LuaValue.Str("fromOffset"),
      LuaValue.NativeFunc("UDim2.fromOffset") { _, a ->
        listOf(
          wrapUDim2(
            RbxUDim2(
              xOffset = a.getOrNull(0)?.asDoubleOrNull() ?: 0.0,
              yOffset = a.getOrNull(1)?.asDoubleOrNull() ?: 0.0,
            )
          )
        )
      },
    )
    env.rawSet(LuaValue.Str("UDim2"), udim2Tbl)

    // Vector2
    val vec2Tbl = LuaValue.Table()
    vec2Tbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("Vector2.new") { _, a ->
        listOf(
          wrapVector2(
            RbxVector2(a.getOrNull(0)?.asDoubleOrNull() ?: 0.0, a.getOrNull(1)?.asDoubleOrNull() ?: 0.0)
          )
        )
      },
    )
    vec2Tbl.rawSet(LuaValue.Str("zero"), wrapVector2(RbxVector2(0.0, 0.0)))
    vec2Tbl.rawSet(LuaValue.Str("one"), wrapVector2(RbxVector2(1.0, 1.0)))
    env.rawSet(LuaValue.Str("Vector2"), vec2Tbl)

    // Vector3
    val vec3Tbl = LuaValue.Table()
    vec3Tbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("Vector3.new") { _, a ->
        listOf(
          wrapVector3(
            RbxVector3(
              a.getOrNull(0)?.asDoubleOrNull() ?: 0.0,
              a.getOrNull(1)?.asDoubleOrNull() ?: 0.0,
              a.getOrNull(2)?.asDoubleOrNull() ?: 0.0,
            )
          )
        )
      },
    )
    vec3Tbl.rawSet(LuaValue.Str("zero"), wrapVector3(RbxVector3(0.0, 0.0, 0.0)))
    vec3Tbl.rawSet(LuaValue.Str("one"), wrapVector3(RbxVector3(1.0, 1.0, 1.0)))
    env.rawSet(LuaValue.Str("Vector3"), vec3Tbl)

    // CFrame
    val cframeTbl = LuaValue.Table()
    cframeTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("CFrame.new") { _, a ->
        val x = a.getOrNull(0)?.asDoubleOrNull() ?: 0.0
        val y = a.getOrNull(1)?.asDoubleOrNull() ?: 0.0
        val z = a.getOrNull(2)?.asDoubleOrNull() ?: 0.0
        listOf(wrapCFrame(RbxVector3(x, y, z)))
      },
    )
    cframeTbl.rawSet(
      LuaValue.Str("Angles"),
      LuaValue.NativeFunc("CFrame.Angles") { _, _ -> listOf(wrapCFrame(RbxVector3())) },
    )
    cframeTbl.rawSet(LuaValue.Str("identity"), wrapCFrame(RbxVector3()))
    env.rawSet(LuaValue.Str("CFrame"), cframeTbl)

    // NumberRange & Rect
    val nrTbl = LuaValue.Table()
    nrTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("NumberRange.new") { _, a ->
        val lo = a.getOrNull(0)?.asDoubleOrNull() ?: 0.0
        val hi = a.getOrNull(1)?.asDoubleOrNull() ?: lo
        val nr = RbxNumberRange(lo, hi)
        listOf(
          LuaValue.Userdata(
            "NumberRange",
            nr,
            onGet = { k ->
              when (k) {
                "Min" -> LuaValue.Num(nr.min)
                "Max" -> LuaValue.Num(nr.max)
                else -> LuaValue.Nil
              }
            },
          )
        )
      },
    )
    env.rawSet(LuaValue.Str("NumberRange"), nrTbl)

    val rectTbl = LuaValue.Table()
    rectTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("Rect.new") { _, a ->
        val rect = RbxRect(
          a.getOrNull(0)?.asDoubleOrNull() ?: 0.0,
          a.getOrNull(1)?.asDoubleOrNull() ?: 0.0,
          a.getOrNull(2)?.asDoubleOrNull() ?: 0.0,
          a.getOrNull(3)?.asDoubleOrNull() ?: 0.0,
        )
        listOf(
          LuaValue.Userdata(
            "Rect",
            rect,
            onGet = { k ->
              when (k) {
                "Min" -> wrapVector2(RbxVector2(rect.minX, rect.minY))
                "Max" -> wrapVector2(RbxVector2(rect.maxX, rect.maxY))
                "Width" -> LuaValue.Num(rect.width)
                "Height" -> LuaValue.Num(rect.height)
                else -> LuaValue.Nil
              }
            },
          )
        )
      },
    )
    env.rawSet(LuaValue.Str("Rect"), rectTbl)

    // Random.new & DateTime.now
    val randomTbl = LuaValue.Table()
    randomTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("Random.new") { _, a ->
        val seed = a.firstOrNull()?.asDoubleOrNull()?.toLong() ?: System.nanoTime()
        val rng = Random(seed)
        listOf(
          LuaValue.Userdata(
            "Random",
            rng,
            onGet = { k ->
              when (k) {
                "NextInteger" ->
                  LuaValue.NativeFunc("Random:NextInteger") { _, args ->
                    val lo = args.getOrNull(1)?.asInt() ?: 0
                    val hi = args.getOrNull(2)?.asInt() ?: 1
                    listOf(LuaValue.Num(rng.nextInt(min(lo, hi), max(lo, hi) + 1).toDouble()))
                  }
                "NextNumber" ->
                  LuaValue.NativeFunc("Random:NextNumber") { _, args ->
                    val lo = args.getOrNull(1)?.asDoubleOrNull() ?: 0.0
                    val hi = args.getOrNull(2)?.asDoubleOrNull() ?: 1.0
                    listOf(LuaValue.Num(if (hi > lo) rng.nextDouble(lo, hi) else lo))
                  }
                else -> LuaValue.Nil
              }
            },
          )
        )
      },
    )
    env.rawSet(LuaValue.Str("Random"), randomTbl)

    val dtTbl = LuaValue.Table()
    dtTbl.rawSet(
      LuaValue.Str("now"),
      LuaValue.NativeFunc("DateTime.now") { _, _ ->
        val nowMs = System.currentTimeMillis()
        listOf(
          LuaValue.Userdata(
            "DateTime",
            nowMs,
            onGet = { k ->
              when (k) {
                "UnixTimestamp" -> LuaValue.Num((nowMs / 1000L).toDouble())
                "UnixTimestampMillis" -> LuaValue.Num(nowMs.toDouble())
                "ToIsoDate" -> LuaValue.NativeFunc("DateTime:ToIsoDate") { _, _ -> listOf(LuaValue.Str(nowMs.toString())) }
                else -> LuaValue.Nil
              }
            },
          )
        )
      },
    )
    env.rawSet(LuaValue.Str("DateTime"), dtTbl)

    // Color3
    val color3Tbl = LuaValue.Table()
    color3Tbl.rawSet(
      LuaValue.Str("fromRGB"),
      LuaValue.NativeFunc("Color3.fromRGB") { _, a ->
        listOf(
          wrapColor3(
            RbxColor3(
              (a.getOrNull(0)?.asDoubleOrNull() ?: 255.0) / 255.0,
              (a.getOrNull(1)?.asDoubleOrNull() ?: 255.0) / 255.0,
              (a.getOrNull(2)?.asDoubleOrNull() ?: 255.0) / 255.0,
            )
          )
        )
      },
    )
    color3Tbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("Color3.new") { _, a ->
        listOf(
          wrapColor3(
            RbxColor3(
              a.getOrNull(0)?.asDoubleOrNull() ?: 0.0,
              a.getOrNull(1)?.asDoubleOrNull() ?: 0.0,
              a.getOrNull(2)?.asDoubleOrNull() ?: 0.0,
            )
          )
        )
      },
    )
    color3Tbl.rawSet(
      LuaValue.Str("fromHex"),
      LuaValue.NativeFunc("Color3.fromHex") { _, a ->
        val hex = (a.firstOrNull()?.toString() ?: "FFFFFF").removePrefix("#").padEnd(6, '0')
        val r = hex.substring(0, 2).toIntOrNull(16) ?: 255
        val g = hex.substring(2, 4).toIntOrNull(16) ?: 255
        val b = hex.substring(4, 6).toIntOrNull(16) ?: 255
        listOf(wrapColor3(RbxColor3(r / 255.0, g / 255.0, b / 255.0)))
      },
    )
    color3Tbl.rawSet(
      LuaValue.Str("fromHSV"),
      LuaValue.NativeFunc("Color3.fromHSV") { _, a ->
        val h = ((a.getOrNull(0)?.asDoubleOrNull() ?: 0.0) % 1.0).toFloat() * 360f
        val s = (a.getOrNull(1)?.asDoubleOrNull() ?: 1.0).toFloat().coerceIn(0f, 1f)
        val v = (a.getOrNull(2)?.asDoubleOrNull() ?: 1.0).toFloat().coerceIn(0f, 1f)
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        listOf(
          wrapColor3(
            RbxColor3(
              android.graphics.Color.red(rgb) / 255.0,
              android.graphics.Color.green(rgb) / 255.0,
              android.graphics.Color.blue(rgb) / 255.0,
            )
          )
        )
      },
    )
    env.rawSet(LuaValue.Str("Color3"), color3Tbl)

    // ColorSequence & ColorSequenceKeypoint
    val cskTbl = LuaValue.Table()
    cskTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("ColorSequenceKeypoint.new") { _, a ->
        val t = a.getOrNull(0)?.asDoubleOrNull() ?: 0.0
        val c = (a.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxColor3 ?: RbxColor3()
        listOf(LuaValue.Userdata("ColorSequenceKeypoint", RbxColorSequenceKeypoint(t, c)))
      },
    )
    env.rawSet(LuaValue.Str("ColorSequenceKeypoint"), cskTbl)

    val csTbl = LuaValue.Table()
    csTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("ColorSequence.new") { _, a ->
        val first = a.getOrNull(0)
        val seq =
          when {
            first is LuaValue.Table -> {
              val pts = mutableListOf<RbxColorSequenceKeypoint>()
              for (i in 1..first.arrayLength()) {
                val kp = (first.get(LuaValue.Num(i.toDouble())) as? LuaValue.Userdata)?.data as? RbxColorSequenceKeypoint
                if (kp != null) pts.add(kp)
              }
              RbxColorSequence(pts.ifEmpty { listOf(RbxColorSequenceKeypoint(0.0, RbxColor3())) })
            }
            (first as? LuaValue.Userdata)?.data is RbxColor3 -> {
              val c0 = first.data as RbxColor3
              val c1 = (a.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxColor3 ?: c0
              RbxColorSequence(listOf(RbxColorSequenceKeypoint(0.0, c0), RbxColorSequenceKeypoint(1.0, c1)))
            }
            else -> RbxColorSequence(listOf(RbxColorSequenceKeypoint(0.0, RbxColor3())))
          }
        listOf(LuaValue.Userdata("ColorSequence", seq))
      },
    )
    env.rawSet(LuaValue.Str("ColorSequence"), csTbl)

    val nsTbl = LuaValue.Table()
    nsTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("NumberSequence.new") { _, a ->
        listOf(LuaValue.Num(a.firstOrNull()?.asDoubleOrNull() ?: 0.0))
      },
    )
    env.rawSet(LuaValue.Str("NumberSequence"), nsTbl)

    val nskTbl = LuaValue.Table()
    nskTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("NumberSequenceKeypoint.new") { _, a ->
        listOf(LuaValue.Num(a.getOrNull(1)?.asDoubleOrNull() ?: a.getOrNull(0)?.asDoubleOrNull() ?: 0.0))
      },
    )
    env.rawSet(LuaValue.Str("NumberSequenceKeypoint"), nskTbl)

    // TweenInfo
    val tweenInfoTbl = LuaValue.Table()
    tweenInfoTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("TweenInfo.new") { _, a ->
        val time = a.getOrNull(0)?.asDoubleOrNull() ?: 1.0
        val styleItem = ((a.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxEnumItem)?.name ?: "Quad"
        val dirItem = ((a.getOrNull(2) as? LuaValue.Userdata)?.data as? RbxEnumItem)?.name ?: "Out"
        listOf(LuaValue.Userdata("TweenInfo", RbxTweenInfo(time, styleItem, dirItem)))
      },
    )
    env.rawSet(LuaValue.Str("TweenInfo"), tweenInfoTbl)

    // Enum
    val enumRoot = LuaValue.Table()
    fun addEnumGroup(groupName: String, vararg items: String) {
      val g = LuaValue.Table()
      items.forEachIndexed { idx, itemName ->
        g.rawSet(
          LuaValue.Str(itemName),
          LuaValue.Userdata("EnumItem", RbxEnumItem(groupName, itemName, idx)),
        )
      }
      enumRoot.rawSet(LuaValue.Str(groupName), g)
    }
    addEnumGroup("Font", "SourceSans", "SourceSansBold", "Gotham", "GothamBold", "GothamSemibold", "Code", "RobotoMono", "BuilderSans", "Arcade", "FredokaOne")
    addEnumGroup("TextXAlignment", "Left", "Right", "Center")
    addEnumGroup("TextYAlignment", "Top", "Center", "Bottom")
    addEnumGroup("TextTruncate", "None", "AtEnd", "SplitWord")
    addEnumGroup("FillDirection", "Horizontal", "Vertical")
    addEnumGroup("HorizontalAlignment", "Center", "Left", "Right")
    addEnumGroup("VerticalAlignment", "Center", "Top", "Bottom")
    addEnumGroup("SortOrder", "LayoutOrder", "Name", "Custom")
    addEnumGroup("AutomaticSize", "None", "X", "Y", "XY")
    addEnumGroup("ScrollingDirection", "X", "Y", "XY")
    addEnumGroup("ApplyStrokeMode", "Contextual", "Border")
    addEnumGroup("ScaleType", "Stretch", "Slice", "Tile", "Fit", "Crop")
    addEnumGroup("ZIndexBehavior", "Sibling", "Global")
    addEnumGroup("EasingStyle", "Linear", "Sine", "Quad", "Quart", "Quint", "Bounce", "Back", "Elastic", "Exponential", "Circular")
    addEnumGroup("EasingDirection", "In", "Out", "InOut")
    addEnumGroup("UserInputType", "MouseButton1", "MouseButton2", "MouseMovement", "Keyboard", "Touch")
    addEnumGroup("UserInputState", "Begin", "Change", "End", "Cancel", "None")
    addEnumGroup("KeyCode", "A", "B", "C", "D", "E", "F", "G", "H", "Q", "R", "S", "V", "W", "X", "Z", "Zero", "One", "Two", "Three", "Space", "Return", "Escape", "Tab", "Backspace", "LeftShift", "RightShift", "LeftControl", "RightControl", "Insert", "Delete")
    env.rawSet(LuaValue.Str("Enum"), enumRoot)

    // Instance.new
    val instanceTbl = LuaValue.Table()
    instanceTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("Instance.new") { _, a ->
        val cls = a.getOrNull(0)?.toString()
          ?: throw LuaRuntimeException("Instance.new requires className string")
        val parentArg = (a.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxInstance
        val inst = RbxInstance(cls, cls)
        allInstances.add(inst)
        if (parentArg != null) {
          inst.setParent(parentArg)
          parentArg.childAdded.fire(vmOrFallback(), listOf(wrapInstance(inst)))
          maybeScheduleLocalScript(inst)
          onUiDirty()
        }
        listOf(wrapInstance(inst))
      },
    )
    env.rawSet(LuaValue.Str("Instance"), instanceTbl)
  }

  private fun vmOrFallback(): LuaVm = attachedVm ?: LuaVm()

  private fun populateRobloxGlobals(vm: LuaVm, env: LuaValue.Table) {
    env.rawSet(LuaValue.Str("game"), wrapInstance(gameInstance))
    env.rawSet(LuaValue.Str("workspace"), wrapInstance(workspaceInstance))
    env.rawSet(LuaValue.Str("script"), wrapInstance(localScriptInstance))
  }

  private fun populateExecutorApis(vm: LuaVm, env: LuaValue.Table) {
    fun reg(name: String, fn: suspend (LuaVm, List<LuaValue>) -> List<LuaValue>) {
      env.rawSet(LuaValue.Str(name), LuaValue.NativeFunc(name, true, fn))
    }

    reg("getgenv") { _, _ -> listOf(genv) }
    reg("getrenv") { _, _ -> listOf(renv) }
    reg("getfenv") { _, _ -> listOf(genv) }
    reg("setfenv") { _, args ->
      val fn = args.getOrNull(0)
      val tbl = args.getOrNull(1) as? LuaValue.Table
      if (fn is LuaValue.Closure && tbl != null) fn.env = tbl
      listOf(fn ?: LuaValue.Nil)
    }
    reg("gethui") { _, _ -> listOf(wrapInstance(hiddenGui)) }
    reg("get_hidden_gui") { _, _ -> listOf(wrapInstance(hiddenGui)) }
    reg("identifyexecutor") { _, _ ->
      listOf(LuaValue.Str("BloxLua Executor"), LuaValue.Str("5.1-RBX"))
    }
    reg("getexecutorname") { _, _ ->
      listOf(LuaValue.Str("BloxLua Executor"))
    }
    reg("checkcaller") { _, _ -> listOf(LuaValue.Bool(true)) }
    reg("iscclosure") { _, args -> listOf(LuaValue.Bool(args.firstOrNull() is LuaValue.NativeFunc)) }
    reg("islclosure") { _, args -> listOf(LuaValue.Bool(args.firstOrNull() is LuaValue.Closure)) }
    reg("newcclosure") { _, args ->
      val fn = args.firstOrNull()
        ?: throw LuaRuntimeException("newcclosure requires a function")
      listOf(LuaValue.NativeFunc("cclosure", true) { callVm, callArgs -> callVm.callFunction(fn, callArgs) })
    }
    reg("hookfunction") { _, args ->
      val target = args.getOrNull(0)
        ?: throw LuaRuntimeException("hookfunction arg #1 must be a function")
      val replacement = args.getOrNull(1)
        ?: throw LuaRuntimeException("hookfunction arg #2 must be a function")
      val originalClone =
        when (target) {
          is LuaValue.Closure -> LuaValue.Closure(target.proto, target.upvalues, target.env)
          is LuaValue.NativeFunc -> LuaValue.NativeFunc(target.name, target.isCClosure, target.fn)
          else -> throw LuaRuntimeException("hookfunction expects functions")
        }
      hooks[target] = replacement
      onLog(LogLevel.EXEC, "Hooked function: $target")
      listOf(originalClone)
    }
    env.rawSet(LuaValue.Str("replaceclosure"), env.get(LuaValue.Str("hookfunction")))

    reg("getrawmetatable") { _, args ->
      val v = args.firstOrNull()
      val mt =
        when (v) {
          is LuaValue.Table -> v.metatable
          is LuaValue.Userdata -> v.metatable
          else -> null
        }
      listOf(mt ?: LuaValue.Nil)
    }
    reg("setrawmetatable") { _, args ->
      val v = args.getOrNull(0)
      val mt = args.getOrNull(1) as? LuaValue.Table
      when (v) {
        is LuaValue.Table -> v.metatable = mt
        is LuaValue.Userdata -> v.metatable = mt
        else -> Unit
      }
      listOf(v ?: LuaValue.Nil)
    }
    reg("hookmetamethod") { _, args ->
      val target = args.getOrNull(0)
      val method = args.getOrNull(1)?.toString() ?: ""
      val hookFn = args.getOrNull(2) ?: LuaValue.Nil
      val mt = (target as? LuaValue.Table)?.metatable ?: (target as? LuaValue.Userdata)?.metatable
        ?: throw LuaRuntimeException("Target has no metatable")
      val prev = mt.get(LuaValue.Str(method))
      mt.rawSet(LuaValue.Str(method), hookFn)
      onLog(LogLevel.EXEC, "Hooked metamethod $method")
      listOf(prev)
    }
    reg("setreadonly") { _, args ->
      val t = args.getOrNull(0) as? LuaValue.Table
      val ro = args.getOrNull(1)?.isTruthy() == true
      t?.isReadonly = ro
      emptyList()
    }
    reg("isreadonly") { _, args ->
      val t = args.getOrNull(0) as? LuaValue.Table
      listOf(LuaValue.Bool(t?.isReadonly == true))
    }
    reg("cloneref") { _, args ->
      val u = args.firstOrNull() as? LuaValue.Userdata
      val inst = u?.data as? RbxInstance
      if (inst != null) listOf(wrapInstance(inst)) else listOf(args.firstOrNull() ?: LuaValue.Nil)
    }
    reg("compareinstances") { _, args ->
      val a = (args.getOrNull(0) as? LuaValue.Userdata)?.data as? RbxInstance
      val b = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxInstance
      listOf(LuaValue.Bool(a != null && b != null && a.id == b.id))
    }
    reg("getinstances") { _, _ ->
      val tbl = LuaValue.Table()
      allInstances.forEachIndexed { idx, inst ->
        tbl.rawSet(LuaValue.Num((idx + 1).toDouble()), wrapInstance(inst))
      }
      listOf(tbl)
    }
    reg("getnilinstances") { _, _ ->
      val tbl = LuaValue.Table()
      allInstances.filter { it.parent == null && it !== gameInstance && it !== hiddenGui }
        .forEachIndexed { idx, inst ->
          tbl.rawSet(LuaValue.Num((idx + 1).toDouble()), wrapInstance(inst))
        }
      listOf(tbl)
    }
    reg("getgc") { _, _ -> listOf(LuaValue.Table()) }
    reg("getconnections") { _, args ->
      val sig = (args.firstOrNull() as? LuaValue.Userdata)?.data as? RbxSignal
      val tbl = LuaValue.Table()
      sig?.connections?.filter { it.connected }?.forEachIndexed { idx, conn ->
        val entry = LuaValue.Table().apply {
          rawSet(LuaValue.Str("Function"), conn.callback)
          rawSet(LuaValue.Str("Enabled"), LuaValue.Bool(conn.connected))
          rawSet(
            LuaValue.Str("Fire"),
            LuaValue.NativeFunc("Connection:Fire") { callVm, fArgs ->
              callVm.callFunction(conn.callback, fArgs)
              emptyList()
            },
          )
          rawSet(
            LuaValue.Str("Disable"),
            LuaValue.NativeFunc("Connection:Disable") { _, _ ->
              conn.disconnect()
              emptyList()
            },
          )
        }
        tbl.rawSet(LuaValue.Num((idx + 1).toDouble()), entry)
      }
      listOf(tbl)
    }
    reg("firesignal") { callVm, args ->
      val sig = (args.firstOrNull() as? LuaValue.Userdata)?.data as? RbxSignal
      val fireArgs = if (args.size > 1) args.subList(1, args.size) else emptyList()
      sig?.fire(callVm, fireArgs)
      emptyList()
    }
    reg("fireclickdetector") { _, args ->
      val inst = (args.firstOrNull() as? LuaValue.Userdata)?.data as? RbxInstance
      onLog(LogLevel.EXEC, "Fired ClickDetector on ${inst?.name ?: "nil"}")
      emptyList()
    }
    reg("protect_gui") { _, args ->
      val inst = (args.firstOrNull() as? LuaValue.Userdata)?.data as? RbxInstance
      if (inst != null) {
        protectedGuis.add(inst.id)
        onLog(LogLevel.EXEC, "Protected GUI '${inst.name}'")
      }
      emptyList()
    }
    reg("unprotect_gui") { _, args ->
      val inst = (args.firstOrNull() as? LuaValue.Userdata)?.data as? RbxInstance
      if (inst != null) protectedGuis.remove(inst.id)
      emptyList()
    }
    reg("setclipboard") { _, args ->
      val text = args.firstOrNull()?.toString() ?: ""
      onClipboard(text)
      onLog(LogLevel.EXEC, "Copied ${text.length} chars to clipboard")
      emptyList()
    }
    reg("loadstring") { _, args ->
      val code = args.firstOrNull()?.toString()
        ?: throw LuaRuntimeException("loadstring expects string source")
      val chunkName = args.getOrNull(1)?.toString() ?: "loadstring"
      try {
        val proto = compiler.compile(code, chunkName)
        val upvals = Array(proto.upvalues.size) { UpvalueCell(LuaValue.Nil) }
        listOf(LuaValue.Closure(proto, upvals, genv))
      } catch (e: LuaCompileException) {
        listOf(LuaValue.Nil, LuaValue.Str(e.message))
      }
    }

    // Virtual Filesystem inside Self-Environment
    reg("writefile") { _, args ->
      val path = args.getOrNull(0)?.toString() ?: throw LuaRuntimeException("writefile path missing")
      val content = args.getOrNull(1)?.toString() ?: ""
      virtualFs[path] = content
      onLog(LogLevel.EXEC, "writefile('$path', ${content.length} bytes)")
      emptyList()
    }
    reg("appendfile") { _, args ->
      val path = args.getOrNull(0)?.toString() ?: throw LuaRuntimeException("appendfile path missing")
      val content = args.getOrNull(1)?.toString() ?: ""
      virtualFs[path] = (virtualFs[path] ?: "") + content
      emptyList()
    }
    reg("readfile") { _, args ->
      val path = args.getOrNull(0)?.toString() ?: throw LuaRuntimeException("readfile path missing")
      val content = virtualFs[path] ?: throw LuaRuntimeException("File not found in sandbox FS: $path")
      listOf(LuaValue.Str(content))
    }
    reg("isfile") { _, args ->
      val path = args.getOrNull(0)?.toString() ?: ""
      listOf(LuaValue.Bool(virtualFs.containsKey(path)))
    }
    reg("isfolder") { _, args ->
      val path = args.getOrNull(0)?.toString() ?: ""
      listOf(LuaValue.Bool(virtualFolders.contains(path)))
    }
    reg("makefolder") { _, args ->
      val path = args.getOrNull(0)?.toString() ?: ""
      if (path.isNotBlank()) virtualFolders.add(path)
      emptyList()
    }
    reg("delfile") { _, args ->
      val path = args.getOrNull(0)?.toString() ?: ""
      virtualFs.remove(path)
      emptyList()
    }
    reg("listfiles") { _, args ->
      val prefix = args.getOrNull(0)?.toString()?.trimEnd('/') ?: ""
      val tbl = LuaValue.Table()
      val matched =
        if (prefix.isEmpty()) virtualFs.keys.sorted()
        else virtualFs.keys.filter { it.startsWith("$prefix/") || it == prefix }.sorted()
      matched.forEachIndexed { idx, k ->
        tbl.rawSet(LuaValue.Num((idx + 1).toDouble()), LuaValue.Str(k))
      }
      listOf(tbl)
    }

    // Drawing library
    val drawingTbl = LuaValue.Table()
    drawingTbl.rawSet(
      LuaValue.Str("new"),
      LuaValue.NativeFunc("Drawing.new") { _, a ->
        val dtype = a.firstOrNull()?.toString() ?: "Square"
        val obj = RbxDrawingObject(dtype)
        drawings.add(obj)
        onUiDirty()
        listOf(wrapDrawing(obj))
      },
    )
    drawingTbl.rawSet(
      LuaValue.Str("clear"),
      LuaValue.NativeFunc("Drawing.clear") { _, _ ->
        drawings.forEach { it.removed = true }
        drawings.clear()
        onUiDirty()
        emptyList()
      },
    )
    val fontsTbl = LuaValue.Table().apply {
      rawSet(LuaValue.Str("UI"), LuaValue.Num(0.0))
      rawSet(LuaValue.Str("System"), LuaValue.Num(1.0))
      rawSet(LuaValue.Str("Plex"), LuaValue.Num(2.0))
      rawSet(LuaValue.Str("Monospace"), LuaValue.Num(3.0))
    }
    drawingTbl.rawSet(LuaValue.Str("Fonts"), fontsTbl)
    env.rawSet(LuaValue.Str("Drawing"), drawingTbl)
    env.rawSet(LuaValue.Str("cleardrawcache"), drawingTbl.get(LuaValue.Str("clear")))

    // crypt library
    val cryptTbl = LuaValue.Table()
    cryptTbl.rawSet(
      LuaValue.Str("base64encode"),
      LuaValue.NativeFunc("crypt.base64encode") { _, a ->
        val raw = (a.firstOrNull()?.toString() ?: "").toByteArray()
        listOf(LuaValue.Str(Base64.encodeToString(raw, Base64.NO_WRAP)))
      },
    )
    cryptTbl.rawSet(
      LuaValue.Str("base64decode"),
      LuaValue.NativeFunc("crypt.base64decode") { _, a ->
        val raw = a.firstOrNull()?.toString() ?: ""
        val decoded = String(Base64.decode(raw, Base64.DEFAULT))
        listOf(LuaValue.Str(decoded))
      },
    )
    cryptTbl.rawSet(
      LuaValue.Str("hash"),
      LuaValue.NativeFunc("crypt.hash") { _, a ->
        val data = (a.getOrNull(0)?.toString() ?: "").toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        listOf(LuaValue.Str(digest.joinToString("") { "%02x".format(it) }))
      },
    )
    env.rawSet(LuaValue.Str("crypt"), cryptTbl)

    // HTTP & syn table for UI library compatibility
    reg("HttpGet") { _, args ->
      val url = args.firstOrNull()?.toString() ?: ""
      listOf(LuaValue.Str(performHttpGet(url)))
    }
    reg("request") { _, args ->
      val opts = args.firstOrNull() as? LuaValue.Table
      val url = opts?.get(LuaValue.Str("Url"))?.toString() ?: "sandbox://local"
      val method = opts?.get(LuaValue.Str("Method"))?.toString() ?: "GET"
      if (isHttpGetEnabled() && method.equals("GET", ignoreCase = true) && (url.startsWith("http://") || url.startsWith("https://"))) {
        val body = performHttpGet(url)
        val resp = LuaValue.Table()
        resp.rawSet(LuaValue.Str("Success"), LuaValue.Bool(body.isNotEmpty()))
        resp.rawSet(LuaValue.Str("StatusCode"), LuaValue.Num(200.0))
        resp.rawSet(LuaValue.Str("Body"), LuaValue.Str(body))
        listOf(resp)
      } else {
        onLog(LogLevel.EXEC, "Sandboxed HTTP $method -> $url")
        val resp = LuaValue.Table()
        resp.rawSet(LuaValue.Str("Success"), LuaValue.Bool(true))
        resp.rawSet(LuaValue.Str("StatusCode"), LuaValue.Num(200.0))
        resp.rawSet(LuaValue.Str("Body"), LuaValue.Str("{\"sandbox\":true,\"url\":\"$url\"}"))
        listOf(resp)
      }
    }
    env.rawSet(LuaValue.Str("http_request"), env.get(LuaValue.Str("request")))
    val httpTbl = LuaValue.Table().apply { rawSet(LuaValue.Str("request"), env.get(LuaValue.Str("request"))) }
    env.rawSet(LuaValue.Str("http"), httpTbl)

    val synTbl = LuaValue.Table().apply {
      rawSet(LuaValue.Str("protect_gui"), env.get(LuaValue.Str("protect_gui")))
      rawSet(LuaValue.Str("unprotect_gui"), env.get(LuaValue.Str("unprotect_gui")))
      rawSet(LuaValue.Str("request"), env.get(LuaValue.Str("request")))
      rawSet(LuaValue.Str("write_clipboard"), env.get(LuaValue.Str("setclipboard")))
      rawSet(LuaValue.Str("crypt"), cryptTbl)
      rawSet(LuaValue.Str("is_beta"), LuaValue.Bool(false))
    }
    env.rawSet(LuaValue.Str("syn"), synTbl)
  }

  private fun wrapUDim(u: RbxUDim): LuaValue.Userdata =
    LuaValue.Userdata(
      "UDim",
      u,
      onGet = { key ->
        when (key) {
          "Scale" -> LuaValue.Num(u.scale)
          "Offset" -> LuaValue.Num(u.offset)
          else -> LuaValue.Nil
        }
      },
    )

  private fun wrapUDim2(u: RbxUDim2): LuaValue.Userdata {
    val mt = LuaValue.Table().apply {
      rawSet(
        LuaValue.Str("__add"),
        LuaValue.NativeFunc("UDim2.__add") { _, a ->
          val x = (a.getOrNull(0) as? LuaValue.Userdata)?.data as? RbxUDim2 ?: RbxUDim2()
          val y = (a.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxUDim2 ?: RbxUDim2()
          listOf(wrapUDim2(x + y))
        },
      )
      rawSet(
        LuaValue.Str("__sub"),
        LuaValue.NativeFunc("UDim2.__sub") { _, a ->
          val x = (a.getOrNull(0) as? LuaValue.Userdata)?.data as? RbxUDim2 ?: RbxUDim2()
          val y = (a.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxUDim2 ?: RbxUDim2()
          listOf(wrapUDim2(x - y))
        },
      )
    }
    return LuaValue.Userdata(
      "UDim2",
      u,
      metatable = mt,
      onGet = { key ->
        when (key) {
          "X" -> wrapUDim(RbxUDim(u.xScale, u.xOffset))
          "Y" -> wrapUDim(RbxUDim(u.yScale, u.yOffset))
          "Lerp" ->
            LuaValue.NativeFunc("UDim2:Lerp") { _, args ->
              val goal = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxUDim2 ?: u
              val alpha = args.getOrNull(2)?.asDoubleOrNull() ?: 0.5
              listOf(wrapUDim2(u.lerp(goal, alpha)))
            }
          else -> LuaValue.Nil
        }
      },
    )
  }

  private fun wrapVector2(v: RbxVector2): LuaValue.Userdata {
    val mt = LuaValue.Table().apply {
      rawSet(
        LuaValue.Str("__add"),
        LuaValue.NativeFunc("Vector2.__add") { _, a ->
          val x = (a.getOrNull(0) as? LuaValue.Userdata)?.data as? RbxVector2 ?: RbxVector2()
          val y = (a.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxVector2 ?: RbxVector2()
          listOf(wrapVector2(x + y))
        },
      )
      rawSet(
        LuaValue.Str("__sub"),
        LuaValue.NativeFunc("Vector2.__sub") { _, a ->
          val x = (a.getOrNull(0) as? LuaValue.Userdata)?.data as? RbxVector2 ?: RbxVector2()
          val y = (a.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxVector2 ?: RbxVector2()
          listOf(wrapVector2(x - y))
        },
      )
    }
    return LuaValue.Userdata(
      "Vector2",
      v,
      metatable = mt,
      onGet = { key ->
        when (key) {
          "X", "x" -> LuaValue.Num(v.x)
          "Y", "y" -> LuaValue.Num(v.y)
          "Magnitude" -> LuaValue.Num(sqrt(v.x * v.x + v.y * v.y))
          "Lerp" ->
            LuaValue.NativeFunc("Vector2:Lerp") { _, args ->
              val goal = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxVector2 ?: v
              val alpha = args.getOrNull(2)?.asDoubleOrNull() ?: 0.5
              listOf(wrapVector2(v.lerp(goal, alpha)))
            }
          else -> LuaValue.Nil
        }
      },
    )
  }

  private fun wrapVector3(v: RbxVector3): LuaValue.Userdata =
    LuaValue.Userdata(
      "Vector3",
      v,
      onGet = { key ->
        when (key) {
          "X", "x" -> LuaValue.Num(v.x)
          "Y", "y" -> LuaValue.Num(v.y)
          "Z", "z" -> LuaValue.Num(v.z)
          "Magnitude" -> LuaValue.Num(sqrt(v.x * v.x + v.y * v.y + v.z * v.z))
          else -> LuaValue.Nil
        }
      },
    )

  private fun wrapCFrame(pos: RbxVector3): LuaValue.Userdata =
    LuaValue.Userdata(
      "CFrame",
      pos,
      onGet = { key ->
        when (key) {
          "Position", "p" -> wrapVector3(pos)
          "X" -> LuaValue.Num(pos.x)
          "Y" -> LuaValue.Num(pos.y)
          "Z" -> LuaValue.Num(pos.z)
          else -> LuaValue.Nil
        }
      },
    )

  private fun wrapColor3(c: RbxColor3): LuaValue.Userdata =
    LuaValue.Userdata(
      "Color3",
      c,
      onGet = { key ->
        when (key) {
          "R" -> LuaValue.Num(c.r)
          "G" -> LuaValue.Num(c.g)
          "B" -> LuaValue.Num(c.b)
          "Lerp" ->
            LuaValue.NativeFunc("Color3:Lerp") { _, args ->
              val goal = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxColor3 ?: c
              val alpha = args.getOrNull(2)?.asDoubleOrNull() ?: 0.5
              listOf(wrapColor3(c.lerp(goal, alpha)))
            }
          "ToHSV" ->
            LuaValue.NativeFunc("Color3:ToHSV") { _, _ ->
              val (h, s, v) = c.toHSV()
              listOf(LuaValue.Num(h), LuaValue.Num(s), LuaValue.Num(v))
            }
          "ToHex" ->
            LuaValue.NativeFunc("Color3:ToHex") { _, _ -> listOf(LuaValue.Str(c.toHex())) }
          else -> LuaValue.Nil
        }
      },
    )

  private fun wrapSignal(sig: RbxSignal): LuaValue.Userdata =
    LuaValue.Userdata(
      "RBXScriptSignal",
      sig,
      onGet = { key ->
        when (key) {
          "Connect" ->
            LuaValue.NativeFunc("${sig.name}:Connect") { _, args ->
              val fn = args.getOrNull(1) ?: throw LuaRuntimeException("Connect expects function")
              val conn = sig.connect(fn, once = false)
              listOf(wrapConnection(conn))
            }
          "Once" ->
            LuaValue.NativeFunc("${sig.name}:Once") { _, args ->
              val fn = args.getOrNull(1) ?: throw LuaRuntimeException("Once expects function")
              val conn = sig.connect(fn, once = true)
              listOf(wrapConnection(conn))
            }
          "Wait" ->
            LuaValue.NativeFunc("${sig.name}:Wait") { _, _ ->
              val deferred = CompletableDeferred<List<LuaValue>>()
              sig.waiters.add(deferred)
              deferred.await()
            }
          else -> LuaValue.Nil
        }
      },
    )

  private fun wrapConnection(conn: RbxConnection): LuaValue.Userdata =
    LuaValue.Userdata(
      "RBXScriptConnection",
      conn,
      onGet = { key ->
        when (key) {
          "Connected" -> LuaValue.Bool(conn.connected)
          "Disconnect" ->
            LuaValue.NativeFunc("Connection:Disconnect") { _, _ ->
              conn.disconnect()
              emptyList()
            }
          else -> LuaValue.Nil
        }
      },
    )

  private fun wrapDrawing(d: RbxDrawingObject): LuaValue.Userdata =
    LuaValue.Userdata(
      "Drawing",
      d,
      onGet = { key ->
        when (key) {
          "Visible" -> LuaValue.Bool(d.visible)
          "Position" -> wrapVector2(d.position)
          "Size" -> if (d.drawingType.equals("Text", true)) LuaValue.Num(d.textSize) else wrapVector2(d.size)
          "From" -> wrapVector2(d.from)
          "To" -> wrapVector2(d.to)
          "PointA" -> wrapVector2(d.pointA)
          "PointB" -> wrapVector2(d.pointB)
          "PointC" -> wrapVector2(d.pointC)
          "PointD" -> wrapVector2(d.pointD)
          "Color" -> wrapColor3(d.color)
          "OutlineColor" -> wrapColor3(d.outlineColor)
          "Text" -> LuaValue.Str(d.text)
          "Center" -> LuaValue.Bool(d.center)
          "Outline" -> LuaValue.Bool(d.outline)
          "Radius" -> LuaValue.Num(d.radius)
          "Thickness" -> LuaValue.Num(d.thickness)
          "Rounding" -> LuaValue.Num(d.rounding)
          "Filled" -> LuaValue.Bool(d.filled)
          "Transparency" -> LuaValue.Num(d.transparency)
          "ZIndex" -> LuaValue.Num(d.zIndex)
          "Remove", "Destroy" ->
            LuaValue.NativeFunc("Drawing:Remove") { _, _ ->
              d.removed = true
              drawings.remove(d)
              onUiDirty()
              emptyList()
            }
          else -> LuaValue.Nil
        }
      },
      onSet = { key, value ->
        when (key) {
          "Visible" -> d.visible = value.isTruthy()
          "Position" -> ((value as? LuaValue.Userdata)?.data as? RbxVector2)?.let { d.position = it }
          "Size" -> {
            val vec = (value as? LuaValue.Userdata)?.data as? RbxVector2
            if (vec != null) d.size = vec
            else value.asDoubleOrNull()?.let { d.textSize = it }
          }
          "From" -> ((value as? LuaValue.Userdata)?.data as? RbxVector2)?.let { d.from = it }
          "To" -> ((value as? LuaValue.Userdata)?.data as? RbxVector2)?.let { d.to = it }
          "PointA" -> ((value as? LuaValue.Userdata)?.data as? RbxVector2)?.let { d.pointA = it }
          "PointB" -> ((value as? LuaValue.Userdata)?.data as? RbxVector2)?.let { d.pointB = it }
          "PointC" -> ((value as? LuaValue.Userdata)?.data as? RbxVector2)?.let { d.pointC = it }
          "PointD" -> ((value as? LuaValue.Userdata)?.data as? RbxVector2)?.let { d.pointD = it }
          "Color" -> ((value as? LuaValue.Userdata)?.data as? RbxColor3)?.let { d.color = it }
          "OutlineColor" -> ((value as? LuaValue.Userdata)?.data as? RbxColor3)?.let { d.outlineColor = it }
          "Text" -> d.text = value.toString()
          "Center" -> d.center = value.isTruthy()
          "Outline" -> d.outline = value.isTruthy()
          "Radius" -> d.radius = value.asDoubleOrNull() ?: d.radius
          "Thickness" -> d.thickness = value.asDoubleOrNull() ?: d.thickness
          "Rounding" -> d.rounding = value.asDoubleOrNull() ?: d.rounding
          "Filled" -> d.filled = value.isTruthy()
          "Transparency" -> d.transparency = value.asDoubleOrNull() ?: d.transparency
          "ZIndex" -> d.zIndex = value.asDoubleOrNull() ?: d.zIndex
        }
        onUiDirty()
      },
    )

  private fun wrapInstance(inst: RbxInstance): LuaValue.Userdata {
    return LuaValue.Userdata(
      "Instance",
      inst,
      onGet = { key ->
        when (key) {
          "Name" -> LuaValue.Str(inst.name)
          "ClassName" -> LuaValue.Str(inst.className)
          "Parent" -> inst.parent?.let { wrapInstance(it) } ?: LuaValue.Nil
          "AbsolutePosition" -> wrapVector2(inst.liveAbsolutePosition)
          "AbsoluteSize" -> wrapVector2(inst.liveAbsoluteSize)
          "AbsoluteWindowSize" -> wrapVector2(inst.liveAbsoluteSize)
          "AbsoluteContentSize" -> wrapVector2(inst.liveAbsoluteContentSize)
          "ViewportSize" ->
            if (inst.className == "Camera") {
              val vp = getViewportSize()
              wrapVector2(RbxVector2(vp.first.toDouble(), vp.second.toDouble()))
            } else LuaValue.Nil
          "CurrentCamera" ->
            if (inst.className == "Workspace") wrapInstance(currentCamera) else LuaValue.Nil
          "TextBounds" -> {
            val txt = (inst.properties["Text"] as? LuaValue.Str)?.value ?: ""
            val sz = inst.properties["TextSize"]?.asDoubleOrNull() ?: 14.0
            wrapVector2(RbxVector2(txt.length * sz * 0.56, sz * 1.2))
          }
          "MouseButton1Click" -> wrapSignal(inst.mouseButton1Click)
          "MouseButton1Down" -> wrapSignal(inst.mouseButton1Down)
          "MouseButton1Up" -> wrapSignal(inst.mouseButton1Up)
          "MouseButton2Click" -> wrapSignal(inst.mouseButton2Click)
          "Activated" -> wrapSignal(inst.activated)
          "MouseEnter" -> wrapSignal(inst.mouseEnter)
          "MouseLeave" -> wrapSignal(inst.mouseLeave)
          "MouseMoved" -> wrapSignal(inst.mouseMoved)
          "Focused" -> wrapSignal(inst.focused)
          "FocusLost" -> wrapSignal(inst.focusLost)
          "InputBegan" ->
            if (inst.className == "UserInputService") wrapSignal(inputBegan) else wrapSignal(inst.inputBegan)
          "InputEnded" ->
            if (inst.className == "UserInputService") wrapSignal(inputEnded) else wrapSignal(inst.inputEnded)
          "InputChanged" -> if (inst.className == "UserInputService") wrapSignal(inputChanged) else LuaValue.Nil
          "Changed" -> wrapSignal(inst.changed)
          "ChildAdded" -> wrapSignal(inst.childAdded)
          "ChildRemoved" -> wrapSignal(inst.childRemoved)
          "AncestryChanged" -> wrapSignal(inst.ancestryChanged)
          "Destroying" -> wrapSignal(inst.destroying)
          "RenderStepped" -> if (inst.className == "RunService") wrapSignal(renderStepped) else LuaValue.Nil
          "Heartbeat" -> if (inst.className == "RunService") wrapSignal(heartbeat) else LuaValue.Nil
          "Stepped" -> if (inst.className == "RunService") wrapSignal(stepped) else LuaValue.Nil
          "GetPropertyChangedSignal" ->
            LuaValue.NativeFunc("Instance:GetPropertyChangedSignal") { _, args ->
              val prop = args.getOrNull(1)?.toString() ?: ""
              listOf(wrapSignal(inst.getPropertyChangedSignal(prop)))
            }
          "GetService" ->
            LuaValue.NativeFunc("game:GetService") { _, args ->
              val svcName = args.getOrNull(1)?.toString() ?: ""
              val svc =
                when (svcName) {
                  "Players" -> playersService
                  "CoreGui" -> coreGui
                  "RunService" -> runService
                  "TweenService" -> tweenService
                  "UserInputService" -> userInputService
                  "HttpService" -> httpService
                  "Workspace" -> workspaceInstance
                  "ReplicatedStorage" -> replicatedStorage
                  "StarterGui" -> starterGui
                  "TextService" -> textService
                  "GuiService" -> guiService
                  "Debris" -> debrisService
                  "ContentProvider" -> contentProvider
                  "CollectionService" -> collectionService
                  "Lighting" -> lightingService
                  "SoundService" -> soundService
                  else -> {
                    val existing = gameInstance.findFirstChild(svcName)
                    existing ?: RbxInstance(svcName, svcName).also {
                      it.setParent(gameInstance)
                      allInstances.add(it)
                    }
                  }
                }
              listOf(wrapInstance(svc))
            }
          "HttpGet", "HttpGetAsync" ->
            LuaValue.NativeFunc("game:HttpGet") { _, args ->
              val url = args.getOrNull(1)?.toString() ?: ""
              listOf(LuaValue.Str(performHttpGet(url)))
            }
          "GetMouse" ->
            if (inst.className == "Player") {
              LuaValue.NativeFunc("Player:GetMouse") { _, _ ->
                val vp = getViewportSize()
                listOf(
                  LuaValue.Userdata(
                    "Mouse",
                    localPlayer,
                    onGet = { mk ->
                      when (mk) {
                        "X" -> LuaValue.Num((vp.first * 0.5).toDouble())
                        "Y" -> LuaValue.Num((vp.second * 0.5).toDouble())
                        "ViewSizeX" -> LuaValue.Num(vp.first.toDouble())
                        "ViewSizeY" -> LuaValue.Num(vp.second.toDouble())
                        "Button1Down" -> wrapSignal(RbxSignal("MouseButton1Down"))
                        "Button1Up" -> wrapSignal(RbxSignal("MouseButton1Up"))
                        "Move" -> wrapSignal(RbxSignal("MouseMove"))
                        else -> LuaValue.Nil
                      }
                    },
                  )
                )
              }
            } else LuaValue.Nil
          "Create" ->
            if (inst.className == "TweenService") {
              LuaValue.NativeFunc("TweenService:Create") { _, args ->
                val targetInst = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxInstance
                  ?: throw LuaRuntimeException("TweenService:Create arg #1 must be Instance")
                val info = (args.getOrNull(2) as? LuaValue.Userdata)?.data as? RbxTweenInfo
                  ?: RbxTweenInfo(0.5)
                val goals = args.getOrNull(3) as? LuaValue.Table
                  ?: throw LuaRuntimeException("TweenService:Create arg #3 must be property table")
                listOf(createTweenUserdata(targetInst, info, goals))
              }
            } else LuaValue.Nil
          "GetTextSize" ->
            if (inst.className == "TextService") {
              LuaValue.NativeFunc("TextService:GetTextSize") { _, args ->
                val text = args.getOrNull(1)?.toString() ?: ""
                val textSize = args.getOrNull(2)?.asDoubleOrNull() ?: 14.0
                val bounds = (args.getOrNull(4) as? LuaValue.Userdata)?.data as? RbxVector2 ?: RbxVector2(1000.0, 1000.0)
                val lines = text.split('\n').size.coerceAtLeast(1)
                val maxLineLen = text.split('\n').maxOfOrNull { it.length } ?: text.length
                val estW = min(bounds.x, maxLineLen * textSize * 0.56 + 4.0)
                val estH = min(bounds.y, lines * textSize * 1.25)
                listOf(wrapVector2(RbxVector2(estW, estH)))
              }
            } else LuaValue.Nil
          "GetGuiInset" ->
            if (inst.className == "GuiService") {
              LuaValue.NativeFunc("GuiService:GetGuiInset") { _, _ ->
                listOf(wrapVector2(RbxVector2(0.0, 0.0)), wrapVector2(RbxVector2(0.0, 0.0)))
              }
            } else LuaValue.Nil
          "SetCore" ->
            if (inst.className == "StarterGui") {
              LuaValue.NativeFunc("StarterGui:SetCore") { _, args ->
                val coreName = args.getOrNull(1)?.toString() ?: ""
                val opts = args.getOrNull(2) as? LuaValue.Table
                if (coreName.equals("SendNotification", ignoreCase = true) && opts != null) {
                  val title = opts.get(LuaValue.Str("Title")).let { if (it is LuaValue.Nil) "Notification" else it.toString() }
                  val text = opts.get(LuaValue.Str("Text")).let { if (it is LuaValue.Nil) "" else it.toString() }
                  val dur = opts.get(LuaValue.Str("Duration")).asDoubleOrNull() ?: 4.0
                  notifications.add(GuiNotification(notifIdSeq.getAndIncrement(), title, text, dur))
                  onLog(LogLevel.SYSTEM, "Notification [$title]: $text")
                  onUiDirty()
                }
                emptyList()
              }
            } else LuaValue.Nil
          "SetCoreGuiEnabled" ->
            if (inst.className == "StarterGui") {
              LuaValue.NativeFunc("StarterGui:SetCoreGuiEnabled") { _, _ -> emptyList() }
            } else LuaValue.Nil
          "AddItem" ->
            if (inst.className == "Debris") {
              LuaValue.NativeFunc("Debris:AddItem") { _, args ->
                val item = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxInstance
                val life = args.getOrNull(2)?.asDoubleOrNull() ?: 1.0
                if (item != null) {
                  activeTaskCount.incrementAndGet()
                  scope.launch {
                    try {
                      delay((life * 1000.0).toLong().coerceAtLeast(16L))
                      item.setParent(null)
                      allInstances.remove(item)
                      onUiDirty()
                    } finally {
                      activeTaskCount.decrementAndGet()
                    }
                  }
                }
                emptyList()
              }
            } else LuaValue.Nil
          "PreloadAsync" ->
            if (inst.className == "ContentProvider") {
              LuaValue.NativeFunc("ContentProvider:PreloadAsync") { callVm, args ->
                val cb = args.getOrNull(2)
                if (cb is LuaValue.Closure || cb is LuaValue.NativeFunc) {
                  callVm.callFunction(cb, emptyList())
                }
                emptyList()
              }
            } else LuaValue.Nil
          "AddTag" ->
            if (inst.className == "CollectionService") {
              LuaValue.NativeFunc("CollectionService:AddTag") { _, args ->
                val target = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxInstance
                val tag = args.getOrNull(2)?.toString() ?: ""
                if (target != null && tag.isNotEmpty()) {
                  collectionTags.getOrPut(tag) { mutableSetOf() }.add(target)
                }
                emptyList()
              }
            } else LuaValue.Nil
          "RemoveTag" ->
            if (inst.className == "CollectionService") {
              LuaValue.NativeFunc("CollectionService:RemoveTag") { _, args ->
                val target = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxInstance
                val tag = args.getOrNull(2)?.toString() ?: ""
                if (target != null) collectionTags[tag]?.remove(target)
                emptyList()
              }
            } else LuaValue.Nil
          "HasTag" ->
            if (inst.className == "CollectionService") {
              LuaValue.NativeFunc("CollectionService:HasTag") { _, args ->
                val target = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxInstance
                val tag = args.getOrNull(2)?.toString() ?: ""
                listOf(LuaValue.Bool(target != null && collectionTags[tag]?.contains(target) == true))
              }
            } else LuaValue.Nil
          "GetTagged" ->
            if (inst.className == "CollectionService") {
              LuaValue.NativeFunc("CollectionService:GetTagged") { _, args ->
                val tag = args.getOrNull(1)?.toString() ?: ""
                val tbl = LuaValue.Table()
                collectionTags[tag]?.forEachIndexed { idx, item ->
                  tbl.rawSet(LuaValue.Num((idx + 1).toDouble()), wrapInstance(item))
                }
                listOf(tbl)
              }
            } else LuaValue.Nil
          "GetTags" ->
            if (inst.className == "CollectionService") {
              LuaValue.NativeFunc("CollectionService:GetTags") { _, args ->
                val target = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxInstance
                val tbl = LuaValue.Table()
                if (target != null) {
                  var idx = 1
                  for ((tag, set) in collectionTags) {
                    if (target in set) tbl.rawSet(LuaValue.Num((idx++).toDouble()), LuaValue.Str(tag))
                  }
                }
                listOf(tbl)
              }
            } else LuaValue.Nil
          "JSONEncode" ->
            if (inst.className == "HttpService") {
              LuaValue.NativeFunc("HttpService:JSONEncode") { _, args ->
                val value = args.getOrNull(1) ?: LuaValue.Nil
                listOf(LuaValue.Str(luaValueToJsonString(value)))
              }
            } else LuaValue.Nil
          "JSONDecode" ->
            if (inst.className == "HttpService") {
              LuaValue.NativeFunc("HttpService:JSONDecode") { _, args ->
                val raw = args.getOrNull(1)?.toString() ?: "null"
                val parsed =
                  try {
                    parseJsonToLua(raw)
                  } catch (e: Exception) {
                    throw LuaRuntimeException("JSONDecode failed: ${e.message}")
                  }
                listOf(parsed)
              }
            } else LuaValue.Nil
          "GenerateGUID" ->
            if (inst.className == "HttpService") {
              LuaValue.NativeFunc("HttpService:GenerateGUID") { _, args ->
                val wrap = args.getOrNull(1)?.isTruthy() ?: true
                val g = UUID.randomUUID().toString().uppercase()
                listOf(LuaValue.Str(if (wrap) "{$g}" else g))
              }
            } else LuaValue.Nil
          "TweenPosition" ->
            LuaValue.NativeFunc("GuiObject:TweenPosition") { callVm, args ->
              val endPos = args.getOrNull(1) ?: LuaValue.Nil
              val duration = args.getOrNull(4)?.asDoubleOrNull() ?: 0.35
              val cb = args.getOrNull(6)
              val goals = LuaValue.Table().apply { rawSet(LuaValue.Str("Position"), endPos) }
              launchGuiTween(callVm, inst, RbxTweenInfo(duration), goals, cb)
              listOf(LuaValue.Bool(true))
            }
          "TweenSize" ->
            LuaValue.NativeFunc("GuiObject:TweenSize") { callVm, args ->
              val endSize = args.getOrNull(1) ?: LuaValue.Nil
              val duration = args.getOrNull(4)?.asDoubleOrNull() ?: 0.35
              val cb = args.getOrNull(6)
              val goals = LuaValue.Table().apply { rawSet(LuaValue.Str("Size"), endSize) }
              launchGuiTween(callVm, inst, RbxTweenInfo(duration), goals, cb)
              listOf(LuaValue.Bool(true))
            }
          "TweenSizeAndPosition" ->
            LuaValue.NativeFunc("GuiObject:TweenSizeAndPosition") { callVm, args ->
              val endSize = args.getOrNull(1) ?: LuaValue.Nil
              val endPos = args.getOrNull(2) ?: LuaValue.Nil
              val duration = args.getOrNull(5)?.asDoubleOrNull() ?: 0.35
              val cb = args.getOrNull(7)
              val goals = LuaValue.Table().apply {
                rawSet(LuaValue.Str("Size"), endSize)
                rawSet(LuaValue.Str("Position"), endPos)
              }
              launchGuiTween(callVm, inst, RbxTweenInfo(duration), goals, cb)
              listOf(LuaValue.Bool(true))
            }
          "CaptureFocus" ->
            LuaValue.NativeFunc("TextBox:CaptureFocus") { callVm, _ ->
              inst.focused.fire(callVm, emptyList())
              emptyList()
            }
          "ReleaseFocus" ->
            LuaValue.NativeFunc("TextBox:ReleaseFocus") { callVm, args ->
              val submitted = args.getOrNull(1)?.isTruthy() ?: false
              inst.focusLost.fire(callVm, listOf(LuaValue.Bool(submitted), LuaValue.Nil))
              emptyList()
            }
          "Clone" ->
            LuaValue.NativeFunc("Instance:Clone") { _, _ ->
              val cloned = inst.cloneDeep { node -> allInstances.add(node) }
              listOf(wrapInstance(cloned))
            }
          "FindFirstChild" ->
            LuaValue.NativeFunc("Instance:FindFirstChild") { _, args ->
              val targetName = args.getOrNull(1)?.toString() ?: ""
              val recursive = args.getOrNull(2)?.isTruthy() == true
              listOf(inst.findFirstChild(targetName, recursive)?.let { wrapInstance(it) } ?: LuaValue.Nil)
            }
          "FindFirstChildOfClass" ->
            LuaValue.NativeFunc("Instance:FindFirstChildOfClass") { _, args ->
              val cls = args.getOrNull(1)?.toString() ?: ""
              listOf(inst.findFirstChildOfClass(cls)?.let { wrapInstance(it) } ?: LuaValue.Nil)
            }
          "FindFirstChildWhichIsA" ->
            LuaValue.NativeFunc("Instance:FindFirstChildWhichIsA") { _, args ->
              val cls = args.getOrNull(1)?.toString() ?: ""
              val recursive = args.getOrNull(2)?.isTruthy() == true
              listOf(inst.findFirstChildWhichIsA(cls, recursive)?.let { wrapInstance(it) } ?: LuaValue.Nil)
            }
          "FindFirstAncestor" ->
            LuaValue.NativeFunc("Instance:FindFirstAncestor") { _, args ->
              val target = args.getOrNull(1)?.toString() ?: ""
              var cur = inst.parent
              while (cur != null && cur.name != target) cur = cur.parent
              listOf(cur?.let { wrapInstance(it) } ?: LuaValue.Nil)
            }
          "FindFirstAncestorOfClass" ->
            LuaValue.NativeFunc("Instance:FindFirstAncestorOfClass") { _, args ->
              val cls = args.getOrNull(1)?.toString() ?: ""
              var cur = inst.parent
              while (cur != null && cur.className != cls) cur = cur.parent
              listOf(cur?.let { wrapInstance(it) } ?: LuaValue.Nil)
            }
          "FindFirstAncestorWhichIsA" ->
            LuaValue.NativeFunc("Instance:FindFirstAncestorWhichIsA") { _, args ->
              val cls = args.getOrNull(1)?.toString() ?: ""
              var cur = inst.parent
              while (cur != null && !cur.isA(cls)) cur = cur.parent
              listOf(cur?.let { wrapInstance(it) } ?: LuaValue.Nil)
            }
          "IsDescendantOf" ->
            LuaValue.NativeFunc("Instance:IsDescendantOf") { _, args ->
              val anc = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxInstance
              listOf(LuaValue.Bool(anc != null && inst.isDescendantOf(anc)))
            }
          "IsAncestorOf" ->
            LuaValue.NativeFunc("Instance:IsAncestorOf") { _, args ->
              val desc = (args.getOrNull(1) as? LuaValue.Userdata)?.data as? RbxInstance
              listOf(LuaValue.Bool(desc != null && desc.isDescendantOf(inst)))
            }
          "GetFullName" ->
            LuaValue.NativeFunc("Instance:GetFullName") { _, _ -> listOf(LuaValue.Str(inst.getFullName())) }
          "WaitForChild" ->
            LuaValue.NativeFunc("Instance:WaitForChild") { _, args ->
              val targetName = args.getOrNull(1)?.toString() ?: ""
              listOf(inst.findFirstChild(targetName, false)?.let { wrapInstance(it) } ?: LuaValue.Nil)
            }
          "GetChildren" ->
            LuaValue.NativeFunc("Instance:GetChildren") { _, _ ->
              val t = LuaValue.Table()
              inst.children.forEachIndexed { idx, c ->
                t.rawSet(LuaValue.Num((idx + 1).toDouble()), wrapInstance(c))
              }
              listOf(t)
            }
          "GetDescendants" ->
            LuaValue.NativeFunc("Instance:GetDescendants") { _, _ ->
              val t = LuaValue.Table()
              inst.getDescendants().forEachIndexed { idx, c ->
                t.rawSet(LuaValue.Num((idx + 1).toDouble()), wrapInstance(c))
              }
              listOf(t)
            }
          "Destroy" ->
            LuaValue.NativeFunc("Instance:Destroy") { callVm, _ ->
              inst.destroying.fire(callVm, emptyList())
              val oldParent = inst.parent
              inst.setParent(null)
              oldParent?.childRemoved?.fire(callVm, listOf(wrapInstance(inst)))
              allInstances.remove(inst)
              executedLocalScripts.remove(inst.id)
              onUiDirty()
              emptyList()
            }
          "ClearAllChildren" ->
            LuaValue.NativeFunc("Instance:ClearAllChildren") { callVm, _ ->
              inst.children.toList().forEach {
                it.setParent(null)
                inst.childRemoved.fire(callVm, listOf(wrapInstance(it)))
              }
              onUiDirty()
              emptyList()
            }
          "IsA" ->
            LuaValue.NativeFunc("Instance:IsA") { _, args ->
              val cls = args.getOrNull(1)?.toString() ?: ""
              listOf(LuaValue.Bool(inst.isA(cls)))
            }
          "SetAttribute" ->
            LuaValue.NativeFunc("Instance:SetAttribute") { _, args ->
              val k = args.getOrNull(1)?.toString() ?: ""
              val v = args.getOrNull(2) ?: LuaValue.Nil
              inst.attributes[k] = v
              emptyList()
            }
          "GetAttribute" ->
            LuaValue.NativeFunc("Instance:GetAttribute") { _, args ->
              val k = args.getOrNull(1)?.toString() ?: ""
              listOf(inst.attributes[k] ?: LuaValue.Nil)
            }
          else -> {
            inst.properties[key] ?: inst.findFirstChild(key)?.let { wrapInstance(it) } ?: LuaValue.Nil
          }
        }
      },
      onSet = { key, value ->
        when (key) {
          "Name" -> {
            inst.name = value.toString()
            inst.properties["Name"] = LuaValue.Str(inst.name)
          }
          "Parent" -> {
            val oldParent = inst.parent
            val newParent = (value as? LuaValue.Userdata)?.data as? RbxInstance
            inst.setParent(newParent)
            if (oldParent != null && oldParent !== newParent) {
              if (oldParent.childRemoved.hasListeners()) {
                scope.launch { oldParent.childRemoved.fire(vmOrFallback(), listOf(wrapInstance(inst))) }
              }
            }
            if (newParent != null && newParent !== oldParent) {
              if (newParent.childAdded.hasListeners()) {
                scope.launch { newParent.childAdded.fire(vmOrFallback(), listOf(wrapInstance(inst))) }
              }
            }
            if (inst.ancestryChanged.hasListeners()) {
              scope.launch {
                inst.ancestryChanged.fire(
                  vmOrFallback(),
                  listOf(wrapInstance(inst), newParent?.let { wrapInstance(it) } ?: LuaValue.Nil),
                )
              }
            }
            if (newParent == null) executedLocalScripts.remove(inst.id)
            else maybeScheduleLocalScript(inst)
          }
          else -> {
            inst.properties[key] = value
            if (inst.className == "LocalScript" && (key == "Source" || key == "Disabled" || key == "Enabled")) {
              val disabled = (inst.properties["Disabled"] as? LuaValue.Bool)?.value == true
              val enabled = (inst.properties["Enabled"] as? LuaValue.Bool)?.value ?: true
              if (disabled || !enabled) {
                executedLocalScripts.remove(inst.id)
              } else {
                maybeScheduleLocalScript(inst)
              }
            }
          }
        }
        val propSig = inst.propertyChangedSignals[key]
        if (inst.changed.hasListeners() || propSig?.hasListeners() == true) {
          scope.launch {
            val vm = vmOrFallback()
            if (inst.changed.hasListeners()) inst.changed.fire(vm, listOf(LuaValue.Str(key)))
            propSig?.fire(vm, emptyList())
          }
        }
        onUiDirty()
      },
    )
  }

  private fun createTweenUserdata(
    target: RbxInstance,
    info: RbxTweenInfo,
    goals: LuaValue.Table,
  ): LuaValue.Userdata {
    val completed = RbxSignal("Completed")
    var cancelled = false
    return LuaValue.Userdata(
      "Tween",
      target,
      onGet = { key ->
        when (key) {
          "Completed" -> wrapSignal(completed)
          "Cancel" ->
            LuaValue.NativeFunc("Tween:Cancel") { _, _ ->
              cancelled = true
              emptyList()
            }
          "Play" ->
            LuaValue.NativeFunc("Tween:Play") { callVm, _ ->
              cancelled = false
              launchGuiTween(callVm, target, info, goals, null, completed) { cancelled }
              emptyList()
            }
          else -> LuaValue.Nil
        }
      },
    )
  }

  private fun launchGuiTween(
    callVm: LuaVm,
    target: RbxInstance,
    info: RbxTweenInfo,
    goals: LuaValue.Table,
    callback: LuaValue?,
    completedSignal: RbxSignal? = null,
    isCancelled: () -> Boolean = { false },
  ) {
    val startSnapshot = LinkedHashMap<String, LuaValue>()
    val goalEntries = goals.entries.entries.mapNotNull { (k, v) ->
      val propName = (k as? LuaKey.StrKey)?.v ?: return@mapNotNull null
      startSnapshot[propName] = target.properties[propName] ?: LuaValue.Nil
      propName to v
    }
    activeTaskCount.incrementAndGet()
    scope.launch {
      try {
        val durationMs = (info.time * 1000.0).toLong().coerceAtLeast(32L)
        val t0 = System.currentTimeMillis()
        while (isActive && !isCancelled()) {
          val elapsed = System.currentTimeMillis() - t0
          val linear = (elapsed.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
          val alpha = 1.0 - (1.0 - linear) * (1.0 - linear) // QuadOut
          for ((prop, goalVal) in goalEntries) {
            val startVal = startSnapshot[prop]
            val interpolated = interpolateValue(startVal, goalVal, alpha)
            if (interpolated != null) {
              target.properties[prop] = interpolated
            }
          }
          onUiDirty()
          if (linear >= 1.0) break
          delay(getExecutionIntervalMs().coerceIn(16L, 250L))
        }
        if (!isCancelled()) {
          completedSignal?.fire(callVm, emptyList())
          if (callback is LuaValue.Closure || callback is LuaValue.NativeFunc) {
            callVm.callFunction(callback, emptyList())
          }
        }
      } finally {
        activeTaskCount.decrementAndGet()
      }
    }
  }

  private fun interpolateValue(start: LuaValue?, goal: LuaValue, alpha: Double): LuaValue? {
    if (start is LuaValue.Num && goal is LuaValue.Num) {
      return LuaValue.Num(start.value + (goal.value - start.value) * alpha)
    }
    val sd = (start as? LuaValue.Userdata)?.data
    val gd = (goal as? LuaValue.Userdata)?.data
    return when {
      sd is RbxUDim2 && gd is RbxUDim2 -> wrapUDim2(sd.lerp(gd, alpha))
      sd is RbxColor3 && gd is RbxColor3 -> wrapColor3(sd.lerp(gd, alpha))
      sd is RbxVector2 && gd is RbxVector2 -> wrapVector2(sd.lerp(gd, alpha))
      else -> if (alpha >= 1.0) goal else start
    }
  }

  private suspend fun performHttpGet(url: String): String {
    if (!isHttpGetEnabled()) {
      onLog(LogLevel.WARN, "HttpGet disabled in Settings -> $url")
      return ""
    }
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      onLog(LogLevel.EXEC, "Sandboxed HttpGet -> $url")
      return ""
    }
    onLog(LogLevel.EXEC, "HttpGet -> $url")
    return withContext(Dispatchers.IO) {
      try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = 6000
          readTimeout = 6000
          setRequestProperty("User-Agent", "BloxLua/1.0 Roblox/Studio")
        }
        try {
          val code = conn.responseCode
          val stream = if (code in 200..299) conn.inputStream else conn.errorStream
          val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
          if (code !in 200..299) {
            onLog(LogLevel.WARN, "HttpGet HTTP $code from $url")
          }
          body
        } finally {
          conn.disconnect()
        }
      } catch (e: Exception) {
        onLog(LogLevel.WARN, "HttpGet offline/failed ($url): ${e.message}")
        ""
      }
    }
  }

  private fun escapeJsonString(s: String): String {
    val sb = StringBuilder(s.length + 2)
    sb.append('"')
    for (ch in s) {
      when (ch) {
        '"' -> sb.append("\\\"")
        '\\' -> sb.append("\\\\")
        '\b' -> sb.append("\\b")
        '\u000C' -> sb.append("\\f")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        '\t' -> sb.append("\\t")
        else ->
          if (ch.code < 0x20) sb.append(String.format("\\u%04x", ch.code))
          else sb.append(ch)
      }
    }
    sb.append('"')
    return sb.toString()
  }

  private fun luaValueToJsonString(v: LuaValue, depth: Int = 0): String {
    if (depth > 32) return "null"
    return when (v) {
      is LuaValue.Nil -> "null"
      is LuaValue.Bool -> if (v.value) "true" else "false"
      is LuaValue.Num -> {
        val d = v.value
        if (!d.isFinite()) "null"
        else if (d == d.toLong().toDouble()) d.toLong().toString()
        else d.toString()
      }
      is LuaValue.Str -> escapeJsonString(v.value)
      is LuaValue.Table -> {
        val arrLen = v.arrayLength()
        if (arrLen > 0 && arrLen == v.entries.size) {
          buildString {
            append('[')
            for (i in 1..arrLen) {
              if (i > 1) append(',')
              append(luaValueToJsonString(v.get(LuaValue.Num(i.toDouble())), depth + 1))
            }
            append(']')
          }
        } else {
          buildString {
            append('{')
            var first = true
            for ((k, valItem) in v.entries) {
              if (!first) append(',')
              first = false
              append(escapeJsonString(k.toLuaValue().toString()))
              append(':')
              append(luaValueToJsonString(valItem, depth + 1))
            }
            append('}')
          }
        }
      }
      else -> escapeJsonString(v.toString())
    }
  }

  private fun parseJsonToLua(raw: String): LuaValue {
    var idx = 0
    val len = raw.length
    fun skipWs() {
      while (idx < len && raw[idx].isWhitespace()) idx++
    }
    fun parseString(): String {
      if (idx >= len || raw[idx] != '"') error("Expected '\"' at $idx")
      idx++
      val sb = StringBuilder()
      while (idx < len) {
        val c = raw[idx++]
        when (c) {
          '"' -> return sb.toString()
          '\\' -> {
            if (idx >= len) error("Unterminated escape")
            when (val e = raw[idx++]) {
              '"', '\\', '/' -> sb.append(e)
              'b' -> sb.append('\b')
              'f' -> sb.append('\u000C')
              'n' -> sb.append('\n')
              'r' -> sb.append('\r')
              't' -> sb.append('\t')
              'u' -> {
                if (idx + 4 > len) error("Invalid \\u escape")
                sb.append(raw.substring(idx, idx + 4).toInt(16).toChar())
                idx += 4
              }
              else -> sb.append(e)
            }
          }
          else -> sb.append(c)
        }
      }
      error("Unterminated string")
    }
    fun parseValue(): LuaValue {
      skipWs()
      if (idx >= len) error("Unexpected end of JSON")
      return when (raw[idx]) {
        '{' -> {
          idx++
          val tbl = LuaValue.Table()
          skipWs()
          if (idx < len && raw[idx] == '}') {
            idx++
            return tbl
          }
          while (true) {
            skipWs()
            val key = parseString()
            skipWs()
            if (idx >= len || raw[idx] != ':') error("Expected ':' at $idx")
            idx++
            tbl.rawSet(LuaValue.Str(key), parseValue())
            skipWs()
            if (idx < len && raw[idx] == ',') {
              idx++
              continue
            }
            if (idx < len && raw[idx] == '}') {
              idx++
              break
            }
            error("Expected ',' or '}' at $idx")
          }
          tbl
        }
        '[' -> {
          idx++
          val tbl = LuaValue.Table()
          skipWs()
          if (idx < len && raw[idx] == ']') {
            idx++
            return tbl
          }
          var arrIndex = 1
          while (true) {
            tbl.rawSet(LuaValue.Num(arrIndex++.toDouble()), parseValue())
            skipWs()
            if (idx < len && raw[idx] == ',') {
              idx++
              continue
            }
            if (idx < len && raw[idx] == ']') {
              idx++
              break
            }
            error("Expected ',' or ']' at $idx")
          }
          tbl
        }
        '"' -> LuaValue.Str(parseString())
        't' -> {
          if (!raw.startsWith("true", idx)) error("Invalid literal at $idx")
          idx += 4
          LuaValue.Bool(true)
        }
        'f' -> {
          if (!raw.startsWith("false", idx)) error("Invalid literal at $idx")
          idx += 5
          LuaValue.Bool(false)
        }
        'n' -> {
          if (!raw.startsWith("null", idx)) error("Invalid literal at $idx")
          idx += 4
          LuaValue.Nil
        }
        '-', in '0'..'9' -> {
          val start = idx
          if (raw[idx] == '-') idx++
          while (idx < len && (raw[idx].isDigit() || raw[idx] == '.' || raw[idx] == 'e' || raw[idx] == 'E' || raw[idx] == '+' || raw[idx] == '-')) {
            if ((raw[idx] == '+' || raw[idx] == '-') && idx > start && raw[idx - 1] != 'e' && raw[idx - 1] != 'E') break
            idx++
          }
          LuaValue.Num(raw.substring(start, idx).toDouble())
        }
        else -> error("Unexpected char '${raw[idx]}' at $idx")
      }
    }
    return parseValue()
  }

  private fun luaPatternToRegex(pat: String): Regex {
    val sb = StringBuilder()
    var i = 0
    while (i < pat.length) {
      val ch = pat[i]
      if (ch == '%' && i + 1 < pat.length) {
        val code = pat[i + 1]
        val mapped =
          when (code) {
            'a' -> "[A-Za-z]"
            'A' -> "[^A-Za-z]"
            'c' -> "[\\x00-\\x1F\\x7F]"
            'd' -> "\\d"
            'D' -> "\\D"
            'l' -> "[a-z]"
            'L' -> "[^a-z]"
            'p' -> "\\p{Punct}"
            'P' -> "\\P{Punct}"
            's' -> "\\s"
            'S' -> "\\S"
            'u' -> "[A-Z]"
            'U' -> "[^A-Z]"
            'w' -> "[A-Za-z0-9]"
            'W' -> "[^A-Za-z0-9]"
            'x' -> "[0-9A-Fa-f]"
            'X' -> "[^0-9A-Fa-f]"
            'z' -> "\\u0000"
            else -> Regex.escape(code.toString())
          }
        sb.append(mapped)
        i += 2
      } else {
        sb.append(ch)
        i++
      }
    }
    return try {
      Regex(sb.toString())
    } catch (_: Exception) {
      Regex(Regex.escape(pat))
    }
  }

  private fun formatLuaString(fmt: String, args: List<LuaValue>): String {
    val sb = StringBuilder()
    var i = 0
    var argIdx = 0
    while (i < fmt.length) {
      if (fmt[i] == '%' && i + 1 < fmt.length) {
        if (fmt[i + 1] == '%') {
          sb.append('%')
          i += 2
          continue
        }
        val match = Regex("^%([-+0 #]*\\d*(?:\\.\\d+)?)([dfsxXoq%])").find(fmt.substring(i))
        if (match != null) {
          val spec = match.groupValues[1]
          val conv = match.groupValues[2]
          val v = args.getOrNull(argIdx++) ?: LuaValue.Nil
          val formatted =
            when (conv) {
              "d" -> "%${spec}d".format(v.asInt())
              "f" -> "%${spec}f".format(v.asDoubleOrNull() ?: 0.0)
              "x" -> "%${spec}x".format(v.asInt())
              "X" -> "%${spec}X".format(v.asInt())
              "s", "q" -> v.toString()
              else -> v.toString()
            }
          sb.append(formatted)
          i += match.value.length
          continue
        }
      }
      sb.append(fmt[i])
      i++
    }
    return sb.toString()
  }
}
