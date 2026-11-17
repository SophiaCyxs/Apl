package com.example.lua

import java.util.concurrent.atomic.AtomicInteger

data class RbxUDim(val scale: Double = 0.0, val offset: Double = 0.0) {
  override fun toString(): String = "UDim.new($scale, $offset)"
}

data class RbxUDim2(
  val xScale: Double = 0.0,
  val xOffset: Double = 0.0,
  val yScale: Double = 0.0,
  val yOffset: Double = 0.0,
) {
  fun lerp(goal: RbxUDim2, alpha: Double): RbxUDim2 =
    RbxUDim2(
      xScale + (goal.xScale - xScale) * alpha,
      xOffset + (goal.xOffset - xOffset) * alpha,
      yScale + (goal.yScale - yScale) * alpha,
      yOffset + (goal.yOffset - yOffset) * alpha,
    )

  operator fun plus(other: RbxUDim2): RbxUDim2 =
    RbxUDim2(xScale + other.xScale, xOffset + other.xOffset, yScale + other.yScale, yOffset + other.yOffset)

  operator fun minus(other: RbxUDim2): RbxUDim2 =
    RbxUDim2(xScale - other.xScale, xOffset - other.xOffset, yScale - other.yScale, yOffset - other.yOffset)

  override fun toString(): String =
    "UDim2.new(${"%.2f".format(xScale)}, ${xOffset.toInt()}, ${"%.2f".format(yScale)}, ${yOffset.toInt()})"
}

data class RbxVector2(val x: Double = 0.0, val y: Double = 0.0) {
  fun lerp(goal: RbxVector2, alpha: Double): RbxVector2 =
    RbxVector2(x + (goal.x - x) * alpha, y + (goal.y - y) * alpha)

  operator fun plus(other: RbxVector2): RbxVector2 = RbxVector2(x + other.x, y + other.y)
  operator fun minus(other: RbxVector2): RbxVector2 = RbxVector2(x - other.x, y - other.y)

  override fun toString(): String = "Vector2.new(${"%.1f".format(x)}, ${"%.1f".format(y)})"
}

data class RbxVector3(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {
  override fun toString(): String =
    "Vector3.new(${"%.1f".format(x)}, ${"%.1f".format(y)}, ${"%.1f".format(z)})"
}

data class RbxColor3(val r: Double = 1.0, val g: Double = 1.0, val b: Double = 1.0) {
  fun toArgbLong(alpha: Double = 1.0): Long {
    val ai = (alpha.coerceIn(0.0, 1.0) * 255).toInt()
    val ri = (r.coerceIn(0.0, 1.0) * 255).toInt()
    val gi = (g.coerceIn(0.0, 1.0) * 255).toInt()
    val bi = (b.coerceIn(0.0, 1.0) * 255).toInt()
    return (ai.toLong() shl 24) or (ri.toLong() shl 16) or (gi.toLong() shl 8) or bi.toLong()
  }

  fun rgbLabel(): String {
    val ri = (r.coerceIn(0.0, 1.0) * 255).toInt()
    val gi = (g.coerceIn(0.0, 1.0) * 255).toInt()
    val bi = (b.coerceIn(0.0, 1.0) * 255).toInt()
    return "RGB($ri,$gi,$bi)"
  }

  fun lerp(goal: RbxColor3, alpha: Double): RbxColor3 =
    RbxColor3(
      r + (goal.r - r) * alpha,
      g + (goal.g - g) * alpha,
      b + (goal.b - b) * alpha,
    )

  override fun toString(): String = rgbLabel()
}

data class RbxColorSequenceKeypoint(val time: Double, val value: RbxColor3)

data class RbxColorSequence(val keypoints: List<RbxColorSequenceKeypoint>) {
  fun startColor(): RbxColor3 = keypoints.firstOrNull()?.value ?: RbxColor3(1.0, 1.0, 1.0)
  fun endColor(): RbxColor3 = keypoints.lastOrNull()?.value ?: startColor()
}

data class RbxTweenInfo(
  val time: Double = 1.0,
  val easingStyle: String = "Quad",
  val easingDirection: String = "Out",
)

data class RbxEnumItem(val enumType: String, val name: String, val value: Int) {
  override fun toString(): String = "Enum.$enumType.$name"
}

class RbxSignal(val name: String) {
  val connections = mutableListOf<RbxConnection>()
  val waiters = mutableListOf<kotlinx.coroutines.CompletableDeferred<List<LuaValue>>>()

  fun connect(callback: LuaValue, once: Boolean = false): RbxConnection {
    val conn = RbxConnection(this, callback, once)
    connections.add(conn)
    return conn
  }

  suspend fun fire(vm: LuaVm, args: List<LuaValue>) {
    val currentWaiters = waiters.toList()
    waiters.clear()
    for (w in currentWaiters) {
      w.complete(args)
    }
    val snapshot = connections.toList()
    for (conn in snapshot) {
      if (!conn.connected) continue
      if (conn.once) conn.disconnect()
      vm.callFunction(conn.callback, args)
    }
  }
}

class RbxConnection(
  private val signal: RbxSignal,
  val callback: LuaValue,
  val once: Boolean = false,
) {
  var connected: Boolean = true
    private set

  fun disconnect() {
    connected = false
    signal.connections.remove(this)
  }
}

class RbxInstance(
  val className: String,
  var name: String = className,
) {
  companion object {
    private val nextId = AtomicInteger(1)
  }

  val id: Int = nextId.getAndIncrement()
  val properties = linkedMapOf<String, LuaValue>()
  val attributes = linkedMapOf<String, LuaValue>()
  val children = mutableListOf<RbxInstance>()
  var parent: RbxInstance? = null
    private set

  val mouseButton1Click = RbxSignal("MouseButton1Click")
  val mouseButton1Down = RbxSignal("MouseButton1Down")
  val mouseButton1Up = RbxSignal("MouseButton1Up")
  val activated = RbxSignal("Activated")
  val changed = RbxSignal("Changed")
  val childAdded = RbxSignal("ChildAdded")

  init {
    properties["Name"] = LuaValue.Str(name)
    properties["ClassName"] = LuaValue.Str(className)
    when (className) {
      "Frame", "ScrollingFrame", "TextLabel", "TextButton", "ImageLabel", "ImageButton", "TextBox" -> {
        properties["Visible"] = LuaValue.Bool(true)
        properties["Active"] = LuaValue.Bool(className == "TextButton" || className == "ImageButton" || className == "ScrollingFrame" || className == "TextBox")
        properties["Draggable"] = LuaValue.Bool(false)
        properties["ClipsDescendants"] = LuaValue.Bool(className == "ScrollingFrame")
        properties["Position"] = LuaValue.Userdata("UDim2", RbxUDim2(0.0, 0.0, 0.0, 0.0))
        properties["Size"] = LuaValue.Userdata("UDim2", RbxUDim2(0.4, 0.0, 0.25, 0.0))
        properties["AnchorPoint"] = LuaValue.Userdata("Vector2", RbxVector2(0.0, 0.0))
        properties["BackgroundColor3"] = LuaValue.Userdata("Color3", RbxColor3(0.12, 0.14, 0.18))
        properties["BackgroundTransparency"] = LuaValue.Num(0.0)
        properties["BorderSizePixel"] = LuaValue.Num(1.0)
        properties["BorderColor3"] = LuaValue.Userdata("Color3", RbxColor3(0.24, 0.28, 0.35))
        properties["ZIndex"] = LuaValue.Num(1.0)
        properties["Rotation"] = LuaValue.Num(0.0)
        properties["LayoutOrder"] = LuaValue.Num(0.0)

        if (className == "ScrollingFrame") {
          properties["CanvasSize"] = LuaValue.Userdata("UDim2", RbxUDim2(0.0, 0.0, 2.0, 0.0))
          properties["CanvasPosition"] = LuaValue.Userdata("Vector2", RbxVector2(0.0, 0.0))
          properties["ScrollBarThickness"] = LuaValue.Num(6.0)
          properties["ScrollBarImageColor3"] = LuaValue.Userdata("Color3", RbxColor3(0.7, 0.7, 0.75))
        }
        if (className == "TextLabel" || className == "TextButton" || className == "TextBox") {
          properties["Text"] = LuaValue.Str(if (className == "TextButton") "Button" else "Label")
          properties["TextColor3"] = LuaValue.Userdata("Color3", RbxColor3(0.95, 0.96, 0.98))
          properties["TextSize"] = LuaValue.Num(14.0)
          properties["TextScaled"] = LuaValue.Bool(false)
          properties["TextTransparency"] = LuaValue.Num(0.0)
          properties["TextXAlignment"] =
            LuaValue.Userdata("EnumItem", RbxEnumItem("TextXAlignment", "Center", 2))
          properties["TextYAlignment"] =
            LuaValue.Userdata("EnumItem", RbxEnumItem("TextYAlignment", "Center", 1))
          properties["Font"] =
            LuaValue.Userdata("EnumItem", RbxEnumItem("Font", "Gotham", 1))
        }
        if (className == "TextButton" || className == "ImageButton") {
          properties["AutoButtonColor"] = LuaValue.Bool(true)
        }
        if (className == "ImageLabel" || className == "ImageButton") {
          properties["Image"] = LuaValue.Str("rbxassetid://0")
          properties["ImageColor3"] = LuaValue.Userdata("Color3", RbxColor3(1.0, 1.0, 1.0))
          properties["ImageTransparency"] = LuaValue.Num(0.0)
        }
      }
      "ScreenGui" -> {
        properties["Enabled"] = LuaValue.Bool(true)
        properties["DisplayOrder"] = LuaValue.Num(0.0)
      }
      "UICorner" -> {
        properties["CornerRadius"] = LuaValue.Userdata("UDim", RbxUDim(0.0, 8.0))
      }
      "UIStroke" -> {
        properties["Color"] = LuaValue.Userdata("Color3", RbxColor3(0.85, 0.85, 0.9))
        properties["Thickness"] = LuaValue.Num(1.5)
        properties["Transparency"] = LuaValue.Num(0.0)
      }
      "UIGradient" -> {
        properties["Color"] =
          LuaValue.Userdata(
            "ColorSequence",
            RbxColorSequence(
              listOf(
                RbxColorSequenceKeypoint(0.0, RbxColor3(1.0, 1.0, 1.0)),
                RbxColorSequenceKeypoint(1.0, RbxColor3(0.65, 0.65, 0.7)),
              )
            ),
          )
        properties["Rotation"] = LuaValue.Num(0.0)
        properties["Enabled"] = LuaValue.Bool(true)
      }
      "UIListLayout" -> {
        properties["FillDirection"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("FillDirection", "Vertical", 1))
        properties["HorizontalAlignment"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("HorizontalAlignment", "Left", 1))
        properties["VerticalAlignment"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("VerticalAlignment", "Top", 1))
        properties["Padding"] = LuaValue.Userdata("UDim", RbxUDim(0.0, 4.0))
      }
      "UIGridLayout" -> {
        properties["CellSize"] = LuaValue.Userdata("UDim2", RbxUDim2(0.0, 90.0, 0.0, 44.0))
        properties["CellPadding"] = LuaValue.Userdata("UDim2", RbxUDim2(0.0, 6.0, 0.0, 6.0))
      }
      "UIPadding" -> {
        properties["PaddingTop"] = LuaValue.Userdata("UDim", RbxUDim(0.0, 0.0))
        properties["PaddingBottom"] = LuaValue.Userdata("UDim", RbxUDim(0.0, 0.0))
        properties["PaddingLeft"] = LuaValue.Userdata("UDim", RbxUDim(0.0, 0.0))
        properties["PaddingRight"] = LuaValue.Userdata("UDim", RbxUDim(0.0, 0.0))
      }
      "UIAspectRatioConstraint" -> {
        properties["AspectRatio"] = LuaValue.Num(1.0)
      }
    }
  }

  fun setParent(newParent: RbxInstance?) {
    parent?.children?.remove(this)
    parent = newParent
    if (newParent != null && this !in newParent.children) {
      newParent.children.add(this)
    }
  }

  fun findFirstChild(childName: String, recursive: Boolean = false): RbxInstance? {
    for (c in children) {
      if (c.name == childName) return c
    }
    if (recursive) {
      for (c in children) {
        val found = c.findFirstChild(childName, true)
        if (found != null) return found
      }
    }
    return null
  }

  fun getDescendants(): List<RbxInstance> {
    val result = mutableListOf<RbxInstance>()
    fun walk(node: RbxInstance) {
      for (c in node.children.toList()) {
        result.add(c)
        walk(c)
      }
    }
    walk(this)
    return result
  }

  override fun toString(): String = name
}

class RbxDrawingObject(val drawingType: String) {
  var visible: Boolean = true
  var position: RbxVector2 = RbxVector2(24.0, 24.0)
  var size: RbxVector2 = RbxVector2(80.0, 60.0)
  var from: RbxVector2 = RbxVector2(16.0, 16.0)
  var to: RbxVector2 = RbxVector2(120.0, 80.0)
  var pointA: RbxVector2 = RbxVector2(40.0, 20.0)
  var pointB: RbxVector2 = RbxVector2(90.0, 80.0)
  var pointC: RbxVector2 = RbxVector2(20.0, 90.0)
  var pointD: RbxVector2 = RbxVector2(10.0, 40.0)
  var color: RbxColor3 = RbxColor3(0.9, 0.9, 0.95)
  var outlineColor: RbxColor3 = RbxColor3(0.0, 0.0, 0.0)
  var text: String = "ESP"
  var textSize: Double = 14.0
  var center: Boolean = false
  var outline: Boolean = false
  var radius: Double = 24.0
  var thickness: Double = 1.5
  var rounding: Double = 0.0
  var filled: Boolean = false
  var transparency: Double = 1.0
  var zIndex: Double = 1.0
  var removed: Boolean = false

  override fun toString(): String = "Drawing<$drawingType>"
}
