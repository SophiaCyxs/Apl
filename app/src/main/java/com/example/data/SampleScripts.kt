package com.example.data

object SampleScripts {
  val presets: List<ScriptEntity> =
    listOf(
      ScriptEntity(
        id = 1,
        title = "Live Frame HUD",
        category = "Roblox UI",
        code =
          """-- Roblox Lua 5.1 + Graphical Drawing Processor Demo
-- Supports Frame, UICorner, UIStroke, UIGradient, Draggable & Tap
local execName, execVer = identifyexecutor()
print("Executor:", execName, execVer)

local gui = Instance.new("ScreenGui")
gui.Name = "TelemetryHUD"
gui.Parent = gethui()

local root = Instance.new("Frame", gui)
root.Name = "MainPanel"
root.Position = UDim2.new(0.06, 0, 0.08, 0)
root.Size = UDim2.new(0.88, 0, 0.84, 0)
root.BackgroundColor3 = Color3.fromRGB(255, 255, 255)
root.Active = true
root.Draggable = true

local corner = Instance.new("UICorner", root)
corner.CornerRadius = UDim.new(0, 12)

local stroke = Instance.new("UIStroke", root)
stroke.Color = Color3.fromRGB(56, 189, 248)
stroke.Thickness = 2

local grad = Instance.new("UIGradient", root)
grad.Color = ColorSequence.new(Color3.fromRGB(24, 34, 52), Color3.fromRGB(13, 18, 28))
grad.Rotation = 90

local title = Instance.new("TextLabel", root)
title.Name = "Header"
title.Position = UDim2.new(0.05, 0, 0.08, 0)
title.Size = UDim2.new(0.90, 0, 0.22, 0)
title.BackgroundColor3 = Color3.fromRGB(15, 42, 60)
title.TextColor3 = Color3.fromRGB(186, 230, 253)
title.TextSize = 13
title.Text = "BLOX ENGINE // DRAG OR TAP"
local titleCorner = Instance.new("UICorner", title)
titleCorner.CornerRadius = UDim.new(0, 8)
local titleStroke = Instance.new("UIStroke", title)
titleStroke.Color = Color3.fromRGB(14, 116, 144)
titleStroke.Thickness = 1

local barBg = Instance.new("Frame", root)
barBg.Name = "BarTrack"
barBg.Position = UDim2.new(0.05, 0, 0.38, 0)
barBg.Size = UDim2.new(0.90, 0, 0.16, 0)
barBg.BackgroundColor3 = Color3.fromRGB(30, 41, 59)
Instance.new("UICorner", barBg).CornerRadius = UDim.new(0, 99)

local barFill = Instance.new("Frame", barBg)
barFill.Name = "BarFill"
barFill.Position = UDim2.new(0.0, 0, 0.0, 0)
barFill.Size = UDim2.new(0.15, 0, 1.0, 0)
barFill.BackgroundColor3 = Color3.fromRGB(52, 211, 153)
barFill.BorderSizePixel = 0
Instance.new("UICorner", barFill).CornerRadius = UDim.new(0, 99)

local status = Instance.new("TextLabel", root)
status.Name = "StatusLabel"
status.Position = UDim2.new(0.05, 0, 0.62, 0)
status.Size = UDim2.new(0.55, 0, 0.26, 0)
status.BackgroundColor3 = Color3.fromRGB(18, 26, 38)
status.TextSize = 13
status.Text = "Charge: 15%"
Instance.new("UICorner", status).CornerRadius = UDim.new(0, 8)

local boostBtn = Instance.new("TextButton", root)
boostBtn.Name = "BoostBtn"
boostBtn.Position = UDim2.new(0.64, 0, 0.62, 0)
boostBtn.Size = UDim2.new(0.31, 0, 0.26, 0)
boostBtn.BackgroundColor3 = Color3.fromRGB(8, 145, 178)
boostBtn.TextSize = 13
boostBtn.Text = "PULSE"
Instance.new("UICorner", boostBtn).CornerRadius = UDim.new(0, 8)
local btnStroke = Instance.new("UIStroke", boostBtn)
btnStroke.Color = Color3.fromRGB(165, 243, 252)
btnStroke.Thickness = 1.5

local TweenService = game:GetService("TweenService")
local pulseCount = 0

boostBtn.MouseButton1Click:Connect(function()
  pulseCount = pulseCount + 1
  status.Text = "Pulse #" .. tostring(pulseCount)
  print("Canvas button tapped! Count =", pulseCount)
  barFill.Size = UDim2.new(0.18, 0, 1.0, 0)
  local t = TweenService:Create(barFill, TweenInfo.new(0.45), {
    Size = UDim2.new(0.95, 0, 1.0, 0),
    BackgroundColor3 = Color3.fromRGB(56, 189, 248)
  })
  t:Play()
end)

for step = 1, 8 do
  local pct = 0.15 + (step * 0.10)
  if pct > 0.95 then pct = 0.95 end
  barFill.Size = UDim2.new(pct, 0, 1.0, 0)
  status.Text = string.format("Sync: %d%%", math.floor(pct * 100))
  task.wait(0.10)
end

print("HUD ready - tap PULSE or drag MainPanel directly on GUI Canvas!")
""",
        updatedAt = 10L,
        isBuiltIn = true,
      ),
      ScriptEntity(
        id = 2,
        title = "Executor Menu + FS",
        category = "Executor",
        code =
          """-- ScrollingFrame + UIListLayout + UICorner + UIStroke + Virtual FS
local genv = getgenv()
genv.MenuConfig = { Aimbot = false, Esp = true, Noclip = false, Speed = true }

writefile("configs/menu.cfg", "esp=true;speed=true")
print("Saved config:", readfile("configs/menu.cfg"))

local gui = Instance.new("ScreenGui", game:GetService("CoreGui"))
gui.Name = "ExecutorMenu"

local panel = Instance.new("Frame", gui)
panel.Name = "MenuFrame"
panel.Position = UDim2.new(0.08, 0, 0.06, 0)
panel.Size = UDim2.new(0.84, 0, 0.88, 0)
panel.BackgroundColor3 = Color3.fromRGB(16, 20, 28)
panel.Active = true
panel.Draggable = true
Instance.new("UICorner", panel).CornerRadius = UDim.new(0, 10)
local pStroke = Instance.new("UIStroke", panel)
pStroke.Color = Color3.fromRGB(100, 116, 139)
pStroke.Thickness = 1.5

local header = Instance.new("TextLabel", panel)
header.Name = "MenuTitle"
header.Position = UDim2.new(0.04, 0, 0.05, 0)
header.Size = UDim2.new(0.92, 0, 0.18, 0)
header.BackgroundColor3 = Color3.fromRGB(30, 41, 59)
header.TextColor3 = Color3.fromRGB(226, 232, 240)
header.TextSize = 13
header.Text = "BLOX HUB // SCROLL LIST"
Instance.new("UICorner", header).CornerRadius = UDim.new(0, 6)

local scroll = Instance.new("ScrollingFrame", panel)
scroll.Name = "OptionsScroll"
scroll.Position = UDim2.new(0.04, 0, 0.27, 0)
scroll.Size = UDim2.new(0.92, 0, 0.67, 0)
scroll.BackgroundColor3 = Color3.fromRGB(11, 15, 22)
scroll.CanvasSize = UDim2.new(0, 0, 1.8, 0)
scroll.ScrollBarThickness = 5
Instance.new("UICorner", scroll).CornerRadius = UDim.new(0, 8)
local pad = Instance.new("UIPadding", scroll)
pad.PaddingTop = UDim.new(0, 6)
pad.PaddingLeft = UDim.new(0, 6)
pad.PaddingRight = UDim.new(0, 6)

local layout = Instance.new("UIListLayout", scroll)
layout.FillDirection = Enum.FillDirection.Vertical
layout.Padding = UDim.new(0, 6)

local function makeToggle(name, key, initial)
  local btn = Instance.new("TextButton", scroll)
  btn.Name = name
  btn.Size = UDim2.new(1.0, 0, 0.0, 34)
  btn.TextSize = 12
  Instance.new("UICorner", btn).CornerRadius = UDim.new(0, 6)
  local st = Instance.new("UIStroke", btn)
  st.Thickness = 1
  local function refresh()
    local on = genv.MenuConfig[key]
    btn.Text = string.upper(key) .. (on and ": ON" or ": OFF")
    btn.BackgroundColor3 = on and Color3.fromRGB(16, 110, 82) or Color3.fromRGB(38, 46, 62)
    st.Color = on and Color3.fromRGB(52, 211, 153) or Color3.fromRGB(71, 85, 105)
  end
  refresh()
  btn.MouseButton1Click:Connect(function()
    genv.MenuConfig[key] = not genv.MenuConfig[key]
    refresh()
    print(key, "->", tostring(genv.MenuConfig[key]))
  end)
end

makeToggle("EspToggle", "Esp", true)
makeToggle("AimToggle", "Aimbot", false)
makeToggle("NoclipToggle", "Noclip", false)
makeToggle("SpeedToggle", "Speed", true)

print("Files:", table.concat(listfiles("configs"), ", "))
""",
        updatedAt = 11L,
        isBuiltIn = true,
      ),
      ScriptEntity(
        id = 3,
        title = "Drawing ESP Loop",
        category = "Executor",
        code =
          """-- Real-Time Executor Drawing Processor (Square, Circle, Line, Text)
Drawing.clear()

local box = Drawing.new("Square")
box.Color = Color3.fromRGB(52, 211, 153)
box.Size = Vector2.new(76, 92)
box.Thickness = 2
box.Rounding = 6
box.Visible = true

local head = Drawing.new("Circle")
head.Color = Color3.fromRGB(248, 113, 113)
head.Radius = 12
head.Thickness = 2
head.Filled = false
head.Visible = true

local label = Drawing.new("Text")
label.Color = Color3.fromRGB(251, 191, 36)
label.Size = 12
label.Center = true
label.Outline = true
label.Text = "TARGET [100 HP]"

local tracer = Drawing.new("Line")
tracer.Color = Color3.fromRGB(56, 189, 248)
tracer.Thickness = 1.5
tracer.From = Vector2.new(180, 224)

local RunService = game:GetService("RunService")
local t = 0
local conn
conn = RunService.RenderStepped:Connect(function(dt)
  t = t + 0.16
  local x = 140 + math.sin(t) * 85
  local y = 56 + math.cos(t * 0.8) * 28
  box.Position = Vector2.new(x, y)
  head.Position = Vector2.new(x + 38, y + 20)
  label.Position = Vector2.new(x + 38, y - 16)
  tracer.To = Vector2.new(x + 38, y + 92)
  if t > 10 then
    conn:Disconnect()
    print("Drawing ESP cycle finished!")
  end
end)

print("Drawing ESP active on GUI Canvas")
""",
        updatedAt = 12L,
        isBuiltIn = true,
      ),
      ScriptEntity(
        id = 4,
        title = "Hooks & Bit32 Lab",
        category = "Lua 5.1",
        code =
          """-- Lua 5.1 Closures, Metatables, hookfunction & bit32
local function makeCounter(start)
  local count = start
  return function(delta)
    count = count + (delta or 1)
    return count
  end
end

local c = makeCounter(10)
print("Closure counter:", c(2), c(5))

local rawVec = setmetatable({ x = 3, y = 4 }, {
  __tostring = function(self)
    return string.format("Vec(%d, %d)", self.x, self.y)
  end
})
print("Metatable __tostring:", tostring(rawVec))

-- Hookfunction test
local function greet(name)
  return "Hello, " .. name
end

local oldGreet
oldGreet = hookfunction(greet, newcclosure(function(name)
  return "[HOOKED] " .. oldGreet(name)
end))
print(greet("RobloxLua51"))

-- bit32 (Lua 5.1 style)
local flags = bit32.bor(0x01, 0x04, 0x10)
print("bit32 flags:", flags, "has 0x04?", bit32.band(flags, 0x04) ~= 0)

-- loadstring dynamic compilation
local chunk, err = loadstring("return 6 * 7")
if chunk then
  print("loadstring result:", chunk())
else
  warn("Compile error:", err)
end
""",
        updatedAt = 4L,
        isBuiltIn = true,
      ),
      ScriptEntity(
        id = 5,
        title = "Dialect Guard Test",
        category = "Compiler",
        code =
          """-- Strict Roblox Lua 5.1 & Executor Lua Compiler Check
-- Uncomment any non-5.1 line below to verify the compiler rejects it:

-- goto skip_label          -- Rejected: Lua 5.2+ 'goto'
-- local d = 10 // 3        -- Rejected: Lua 5.3+ floor division '//'
-- local b = 5 & 3          -- Rejected: Lua 5.3+ bitwise '&'
-- local x = 1; x += 2      -- Rejected: compound '+='

local a = math.floor(10 / 3)
local b = bit32.band(5, 3)
local hash = crypt.hash("BloxLua51")

print("Valid Lua 5.1 floor:", a)
print("Valid bit32.band:", b)
print("SHA-256 prefix:", string.sub(hash, 1, 16))
""",
        updatedAt = 5L,
        isBuiltIn = true,
      ),
      ScriptEntity(
        id = 6,
        title = "LocalScript + UI Lib",
        category = "Roblox UI",
        code =
          """-- LocalScript + ModuleScript UI Library + CanvasGroup + TextBox + Notification
local HttpService = game:GetService("HttpService")
local StarterGui = game:GetService("StarterGui")

local uiLib = Instance.new("ModuleScript")
uiLib.Name = "NeonLib"
uiLib.Source = [[
local Lib = {}
function Lib:CreateWindow(titleText)
  local gui = Instance.new("ScreenGui", gethui())
  gui.Name = "NeonLibGui"

  local group = Instance.new("CanvasGroup", gui)
  group.Name = "WindowGroup"
  group.Position = UDim2.new(0.06, 0, 0.08, 0)
  group.Size = UDim2.new(0.88, 0, 0.84, 0)
  group.BackgroundColor3 = Color3.fromRGB(16, 20, 30)
  group.GroupTransparency = 0.04
  group.Active = true
  group.Draggable = true
  Instance.new("UICorner", group).CornerRadius = UDim.new(0, 12)
  local st = Instance.new("UIStroke", group)
  st.Color = Color3.fromRGB(129, 140, 248)
  st.Thickness = 1.5

  local scaler = Instance.new("UIScale", group)
  scaler.Scale = 1.0

  local title = Instance.new("TextLabel", group)
  title.Position = UDim2.new(0.05, 0, 0.08, 0)
  title.Size = UDim2.new(0.90, 0, 0.22, 0)
  title.BackgroundColor3 = Color3.fromRGB(28, 35, 54)
  title.TextColor3 = Color3.fromRGB(224, 231, 255)
  title.RichText = true
  title.Text = "<b>" .. titleText .. "</b>"
  Instance.new("UICorner", title).CornerRadius = UDim.new(0, 8)
  return { Gui = gui, Root = group, Scale = scaler }
end
return Lib
]]

local NeonLib = require(uiLib)
local win = NeonLib:CreateWindow("NEON UI LIB // LOCALSCRIPT")

local inputBox = Instance.new("TextBox", win.Root)
inputBox.Name = "CommandInput"
inputBox.Position = UDim2.new(0.05, 0, 0.36, 0)
inputBox.Size = UDim2.new(0.90, 0, 0.24, 0)
inputBox.BackgroundColor3 = Color3.fromRGB(22, 27, 40)
inputBox.PlaceholderText = "Tap to type a command..."
inputBox.Text = ""
Instance.new("UICorner", inputBox).CornerRadius = UDim.new(0, 8)
local boxStroke = Instance.new("UIStroke", inputBox)
boxStroke.Color = Color3.fromRGB(99, 102, 241)
boxStroke.Thickness = 1

local outLabel = Instance.new("TextLabel", win.Root)
outLabel.Name = "OutputBadge"
outLabel.Position = UDim2.new(0.05, 0, 0.66, 0)
outLabel.Size = UDim2.new(0.90, 0, 0.24, 0)
outLabel.BackgroundColor3 = Color3.fromRGB(15, 23, 42)
outLabel.Text = "Waiting for LocalScript..."
Instance.new("UICorner", outLabel).CornerRadius = UDim.new(0, 8)

inputBox:GetPropertyChangedSignal("Text"):Connect(function()
  outLabel.Text = "Live Input: " .. inputBox.Text
end)

inputBox.FocusLost:Connect(function(enterPressed)
  if enterPressed and #inputBox.Text > 0 then
    local payload = HttpService:JSONEncode({ cmd = inputBox.Text })
    print("Submitted JSON:", payload)
    StarterGui:SetCore("SendNotification", {
      Title = "UI Command",
      Text = inputBox.Text,
      Duration = 3
    })
  end
end)

-- Child LocalScript executes automatically when parented under GUI!
local ls = Instance.new("LocalScript")
ls.Name = "StatusRunner"
ls.Source = [[
print("Child LocalScript booted inside:", script.Parent.Name)
local badge = script.Parent:FindFirstChild("OutputBadge")
if badge then
  badge.Text = "LocalScript Active — Tap TextBox!"
end
]]
ls.Parent = win.Root
""",
        updatedAt = 13L,
        isBuiltIn = true,
      ),
    )
}
