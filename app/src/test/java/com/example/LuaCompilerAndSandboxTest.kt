package com.example

import com.example.lua.GuiDrawCommand
import com.example.lua.GuiDrawingProcessor
import com.example.lua.LogLevel
import com.example.lua.LuaCompileException
import com.example.lua.LuaCompiler
import com.example.lua.LuaValue
import com.example.lua.LuaVm
import com.example.lua.RobloxSandboxEnv
import com.example.lua.TerminalFrameRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LuaCompilerAndSandboxTest {
  private val compiler = LuaCompiler()

  @Test
  fun `rejects Lua 5_2 goto and Lua 5_3 floor division and bitwise operators`() {
    val invalidSnippets =
      listOf(
        "goto done\n::done::",
        "local x = 10 // 3",
        "local y = 5 & 3",
        "local z = 1 << 2",
        "local a = 1\na += 2",
      )
    for (src in invalidSnippets) {
      try {
        compiler.compile(src)
        fail("Expected LuaCompileException for: $src")
      } catch (e: LuaCompileException) {
        assertTrue(e.message.isNotBlank())
      }
    }
  }

  @Test
  fun `executes Lua 5_1 closures metatables and Roblox executor APIs`() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val logs = mutableListOf<Pair<LogLevel, String>>()
    val vm = LuaVm()
    val env =
      RobloxSandboxEnv(
        scope = scope,
        getExecutionIntervalMs = { 50L },
        onLog = { lvl, msg -> logs.add(lvl to msg) },
        onUiDirty = {},
        onClipboard = {},
      )
    env.attachToVm(vm)

    val source =
      """
      local exec, ver = identifyexecutor()
      getgenv().Flag = 42
      writefile("test.txt", "hello-rbx")
      local readBack = readfile("test.txt")

      local function makeAcc(n)
        return function(d)
          n = n + d
          return n
        end
      end
      local acc = makeAcc(10)
      local v1, v2 = acc(5), acc(3)

      local function mul(a, b) return a * b end
      local oldMul
      oldMul = hookfunction(mul, function(a, b)
        return oldMul(a, b) + 1
      end)

      local chunk = loadstring("return bit32.band(0x0F, 0x06)")
      print(exec, readBack, v1, v2, mul(3, 4), chunk(), getgenv().Flag)
      """
    val proto = compiler.compile(source)
    vm.execute(proto, env.genv)

    val printLine = logs.firstOrNull { it.first == LogLevel.PRINT }?.second.orEmpty()
    assertTrue("Expected BloxLua Executor in '$printLine'", printLine.contains("BloxLua Executor"))
    assertTrue("Expected hello-rbx in '$printLine'", printLine.contains("hello-rbx"))
    assertTrue("Expected 15\t18 in '$printLine'", printLine.contains("15\t18"))
    assertTrue("Expected hooked mul(3,4)=13 in '$printLine'", printLine.contains("13"))
    assertTrue("Expected bit32 band=6 in '$printLine'", printLine.contains("6"))
    assertTrue("Expected genv Flag=42 in '$printLine'", printLine.contains("42"))
    scope.cancel()
  }

  @Test
  fun `converts Roblox Frame UI and interactive TextButton into terminal display updates`() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val vm = LuaVm()
    val env =
      RobloxSandboxEnv(
        scope = scope,
        getExecutionIntervalMs = { 50L },
        onLog = { _, _ -> },
        onUiDirty = {},
        onClipboard = {},
      )
    env.attachToVm(vm)

    val source =
      """
      local gui = Instance.new("ScreenGui", gethui())
      local frame = Instance.new("Frame", gui)
      frame.Name = "Card"
      frame.Position = UDim2.new(0.1, 0, 0.1, 0)
      frame.Size = UDim2.new(0.8, 0, 0.8, 0)
      frame.BackgroundColor3 = Color3.fromRGB(20, 40, 60)
      Instance.new("UICorner", frame)

      local lbl = Instance.new("TextLabel", frame)
      lbl.Position = UDim2.new(0.1, 0, 0.1, 0)
      lbl.Size = UDim2.new(0.8, 0, 0.3, 0)
      lbl.Text = "STATUS_ALPHA"

      local btn = Instance.new("TextButton", frame)
      btn.Name = "ActBtn"
      btn.Position = UDim2.new(0.2, 0, 0.55, 0)
      btn.Size = UDim2.new(0.6, 0, 0.3, 0)
      btn.Text = "CLICK_ME"
      btn.MouseButton1Click:Connect(function()
        lbl.Text = "STATUS_BETA"
      end)
      """
    val proto = compiler.compile(source)
    vm.execute(proto, env.genv)

    val renderer = TerminalFrameRenderer()
    val snap1 = renderer.render(env.uiRoots(), env.drawings, 52, 18, 1L, 16L, true)
    val screen1 = snap1.grid.joinToString("\n") { row -> row.map { it.ch }.joinToString("") }
    assertTrue("Expected STATUS_ALPHA in terminal frame", screen1.contains("STATUS_ALPHA"))
    assertTrue("Expected rounded corner '╭' from UICorner", screen1.contains("╭"))
    assertEquals(1, snap1.interactiveButtons.size)

    // Fire interactive button click and re-render terminal frame
    env.triggerButtonClick(vm, snap1.interactiveButtons.first().instanceId)
    val snap2 = renderer.render(env.uiRoots(), env.drawings, 52, 18, 2L, 16L, true)
    val screen2 = snap2.grid.joinToString("\n") { row -> row.map { it.ch }.joinToString("") }
    assertTrue("Expected STATUS_BETA after button click", screen2.contains("STATUS_BETA"))
    assertTrue(snap2.diffSummary?.contains("STATUS_BETA") == true)
    scope.cancel()
  }

  @Test
  fun `gui drawing processor emits UICorner UIStroke UIGradient ScrollingFrame and handles tap and drag`() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val vm = LuaVm()
    val env =
      RobloxSandboxEnv(
        scope = scope,
        getExecutionIntervalMs = { 50L },
        onLog = { _, _ -> },
        onUiDirty = {},
        onClipboard = {},
      )
    env.attachToVm(vm)

    val source =
      """
      local gui = Instance.new("ScreenGui", gethui())
      local panel = Instance.new("Frame", gui)
      panel.Name = "Panel"
      panel.Position = UDim2.new(0.1, 0, 0.1, 0)
      panel.Size = UDim2.new(0.8, 0, 0.8, 0)
      panel.BackgroundColor3 = Color3.fromRGB(255, 255, 255)
      panel.Active = true
      panel.Draggable = true

      local corner = Instance.new("UICorner", panel)
      corner.CornerRadius = UDim.new(0, 12)

      local stroke = Instance.new("UIStroke", panel)
      stroke.Color = Color3.fromRGB(56, 189, 248)
      stroke.Thickness = 2

      local grad = Instance.new("UIGradient", panel)
      grad.Color = ColorSequence.new(Color3.fromRGB(30, 40, 60), Color3.fromRGB(10, 15, 25))

      local btn = Instance.new("TextButton", panel)
      btn.Name = "TapBtn"
      btn.Position = UDim2.new(0.5, 0, 0.5, 0)
      btn.Size = UDim2.new(0.4, 0, 0.3, 0)
      btn.Text = "BEFORE"
      btn.MouseButton1Click:Connect(function()
        btn.Text = "AFTER"
      end)

      local scroll = Instance.new("ScrollingFrame", panel)
      scroll.Name = "ScrollBox"
      scroll.Position = UDim2.new(0.05, 0, 0.1, 0)
      scroll.Size = UDim2.new(0.4, 0, 0.8, 0)
      scroll.CanvasSize = UDim2.new(0, 0, 2.0, 0)

      local sq = Drawing.new("Square")
      sq.Position = Vector2.new(20, 20)
      sq.Size = Vector2.new(50, 50)
      sq.Rounding = 6
      """
    val proto = compiler.compile(source)
    vm.execute(proto, env.genv)

    val processor = GuiDrawingProcessor()
    val scene = processor.process(env.uiRoots(), env.drawings, 400f, 240f, 1L, 16L)

    val panelRect = scene.commands.filterIsInstance<GuiDrawCommand.DrawRect>().first()
    assertEquals(12f, panelRect.cornerRadius, 0.01f)
    assertEquals(2f, panelRect.strokeThickness, 0.01f)
    assertTrue("Expected UIGradient stops", (panelRect.gradientStops?.size ?: 0) >= 2)
    assertTrue(scene.commands.any { it is GuiDrawCommand.DrawingSquare && it.cornerRadius == 6f })

    // Hit-test the TextButton center inside Panel and trigger tap
    val btnHit = scene.hitTargets.first { it.name == "TapBtn" }
    val hitTopmost =
      scene.hitTargets.asReversed().firstOrNull {
        it.isClickable && it.contains(btnHit.x + btnHit.width * 0.5f, btnHit.y + btnHit.height * 0.5f)
      }
    assertEquals("TapBtn", hitTopmost?.name)
    env.triggerButtonClick(vm, hitTopmost!!.instanceId)

    // Drag Panel and ScrollBox
    val panelHit = scene.hitTargets.first { it.name == "Panel" }
    val scrollHit = scene.hitTargets.first { it.name == "ScrollBox" }
    env.applyDragDelta(panelHit.instanceId, 0.1, 0.05, 40.0, 12.0)
    env.applyDragDelta(scrollHit.instanceId, 0.0, -0.2, 0.0, -30.0)

    val scene2 = processor.process(env.uiRoots(), env.drawings, 400f, 240f, 2L, 16L)
    val updatedText = scene2.commands.filterIsInstance<GuiDrawCommand.DrawText>().first { it.instanceId == btnHit.instanceId }
    assertEquals("AFTER", updatedText.text)
    val movedPanel = scene2.commands.filterIsInstance<GuiDrawCommand.DrawRect>().first { it.instanceId == panelHit.instanceId }
    assertTrue("Panel should have moved right after drag", movedPanel.x > panelRect.x + 20f)
    scope.cancel()
  }
}
