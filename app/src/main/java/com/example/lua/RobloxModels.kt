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

  fun toHSV(): Triple<Double, Double, Double> {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
      (r.coerceIn(0.0, 1.0) * 255).toInt(),
      (g.coerceIn(0.0, 1.0) * 255).toInt(),
      (b.coerceIn(0.0, 1.0) * 255).toInt(),
      hsv,
    )
    return Triple((hsv[0] / 360.0).coerceIn(0.0, 1.0), hsv[1].toDouble(), hsv[2].toDouble())
  }

  fun toHex(): String {
    val ri = (r.coerceIn(0.0, 1.0) * 255).toInt()
    val gi = (g.coerceIn(0.0, 1.0) * 255).toInt()
    val bi = (b.coerceIn(0.0, 1.0) * 255).toInt()
    return "%02x%02x%02x".format(ri, gi, bi)
  }

  override fun toString(): String = rgbLabel()
}

data class RbxNumberRange(val min: Double, val max: Double) {
  override fun toString(): String = "NumberRange.new($min, $max)"
}

data class RbxRect(
  val minX: Double = 0.0,
  val minY: Double = 0.0,
  val maxX: Double = 0.0,
  val maxY: Double = 0.0,
) {
  val width: Double get() = maxX - minX
  val height: Double get() = maxY - minY
  override fun toString(): String = "Rect.new($minX, $minY, $maxX, $maxY)"
}

data class GuiNotification(
  val id: Int,
  val title: String,
  val text: String,
  val durationSec: Double = 4.0,
  val createdAtMs: Long = System.currentTimeMillis(),
)

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

  fun hasListeners(): Boolean = connections.any { it.connected } || waiters.isNotEmpty()

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

  // Live computed layout telemetry updated by GuiDrawingProcessor
  @Volatile var liveAbsolutePosition: RbxVector2 = RbxVector2(0.0, 0.0)
  @Volatile var liveAbsoluteSize: RbxVector2 = RbxVector2(0.0, 0.0)
  @Volatile var liveAbsoluteContentSize: RbxVector2 = RbxVector2(0.0, 0.0)

  val mouseButton1Click = RbxSignal("MouseButton1Click")
  val mouseButton1Down = RbxSignal("MouseButton1Down")
  val mouseButton1Up = RbxSignal("MouseButton1Up")
  val mouseButton2Click = RbxSignal("MouseButton2Click")
  val activated = RbxSignal("Activated")
  val mouseEnter = RbxSignal("MouseEnter")
  val mouseLeave = RbxSignal("MouseLeave")
  val mouseMoved = RbxSignal("MouseMoved")
  val focused = RbxSignal("Focused")
  val focusLost = RbxSignal("FocusLost")
  val inputBegan = RbxSignal("InputBegan")
  val inputEnded = RbxSignal("InputEnded")
  val changed = RbxSignal("Changed")
  val childAdded = RbxSignal("ChildAdded")
  val childRemoved = RbxSignal("ChildRemoved")
  val ancestryChanged = RbxSignal("AncestryChanged")
  val destroying = RbxSignal("Destroying")
  val propertyChangedSignals = linkedMapOf<String, RbxSignal>()

  fun getPropertyChangedSignal(prop: String): RbxSignal =
    propertyChangedSignals.getOrPut(prop) { RbxSignal("PropertyChanged:$prop") }

  init {
    properties["Name"] = LuaValue.Str(name)
    properties["ClassName"] = LuaValue.Str(className)
    when (className) {
      "Frame", "ScrollingFrame", "CanvasGroup", "ViewportFrame",
      "TextLabel", "TextButton", "ImageLabel", "ImageButton", "TextBox" -> {
        properties["Visible"] = LuaValue.Bool(true)
        properties["Active"] = LuaValue.Bool(
          className == "TextButton" || className == "ImageButton" ||
            className == "ScrollingFrame" || className == "TextBox"
        )
        properties["Draggable"] = LuaValue.Bool(false)
        properties["Selectable"] = LuaValue.Bool(className == "TextButton" || className == "ImageButton" || className == "TextBox")
        properties["ClipsDescendants"] = LuaValue.Bool(className == "ScrollingFrame" || className == "CanvasGroup")
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
        properties["AutomaticSize"] = LuaValue.Userdata("EnumItem", RbxEnumItem("AutomaticSize", "None", 0))

        if (className == "CanvasGroup") {
          properties["GroupColor3"] = LuaValue.Userdata("Color3", RbxColor3(1.0, 1.0, 1.0))
          properties["GroupTransparency"] = LuaValue.Num(0.0)
        }
        if (className == "ScrollingFrame") {
          properties["CanvasSize"] = LuaValue.Userdata("UDim2", RbxUDim2(0.0, 0.0, 2.0, 0.0))
          properties["AutomaticCanvasSize"] = LuaValue.Userdata("EnumItem", RbxEnumItem("AutomaticSize", "None", 0))
          properties["CanvasPosition"] = LuaValue.Userdata("Vector2", RbxVector2(0.0, 0.0))
          properties["ScrollBarThickness"] = LuaValue.Num(6.0)
          properties["ScrollBarImageColor3"] = LuaValue.Userdata("Color3", RbxColor3(0.7, 0.7, 0.75))
          properties["ScrollingEnabled"] = LuaValue.Bool(true)
        }
        if (className == "TextLabel" || className == "TextButton" || className == "TextBox") {
          properties["Text"] = LuaValue.Str(
            when (className) {
              "TextButton" -> "Button"
              "TextBox" -> ""
              else -> "Label"
            }
          )
          properties["TextColor3"] = LuaValue.Userdata("Color3", RbxColor3(0.95, 0.96, 0.98))
          properties["TextSize"] = LuaValue.Num(14.0)
          properties["TextScaled"] = LuaValue.Bool(false)
          properties["TextWrapped"] = LuaValue.Bool(false)
          properties["RichText"] = LuaValue.Bool(false)
          properties["TextTransparency"] = LuaValue.Num(0.0)
          properties["TextXAlignment"] =
            LuaValue.Userdata("EnumItem", RbxEnumItem("TextXAlignment", "Center", 2))
          properties["TextYAlignment"] =
            LuaValue.Userdata("EnumItem", RbxEnumItem("TextYAlignment", "Center", 1))
          properties["Font"] =
            LuaValue.Userdata("EnumItem", RbxEnumItem("Font", "Gotham", 1))
        }
        if (className == "TextBox") {
          properties["PlaceholderText"] = LuaValue.Str("Type text...")
          properties["PlaceholderColor3"] = LuaValue.Userdata("Color3", RbxColor3(0.55, 0.58, 0.65))
          properties["ClearTextOnFocus"] = LuaValue.Bool(false)
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
      "ScreenGui", "BillboardGui", "SurfaceGui" -> {
        properties["Enabled"] = LuaValue.Bool(true)
        properties["DisplayOrder"] = LuaValue.Num(0.0)
        properties["ResetOnSpawn"] = LuaValue.Bool(false)
        properties["IgnoreGuiInset"] = LuaValue.Bool(false)
      }
      "UICorner" -> {
        properties["CornerRadius"] = LuaValue.Userdata("UDim", RbxUDim(0.0, 8.0))
      }
      "UIStroke" -> {
        properties["Color"] = LuaValue.Userdata("Color3", RbxColor3(0.85, 0.85, 0.9))
        properties["Thickness"] = LuaValue.Num(1.5)
        properties["Transparency"] = LuaValue.Num(0.0)
        properties["Enabled"] = LuaValue.Bool(true)
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
        properties["Offset"] = LuaValue.Userdata("Vector2", RbxVector2(0.0, 0.0))
        properties["Enabled"] = LuaValue.Bool(true)
      }
      "UIListLayout" -> {
        properties["FillDirection"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("FillDirection", "Vertical", 1))
        properties["HorizontalAlignment"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("HorizontalAlignment", "Left", 1))
        properties["VerticalAlignment"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("VerticalAlignment", "Top", 1))
        properties["SortOrder"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("SortOrder", "LayoutOrder", 2))
        properties["Padding"] = LuaValue.Userdata("UDim", RbxUDim(0.0, 4.0))
        properties["Wraps"] = LuaValue.Bool(false)
      }
      "UIGridLayout" -> {
        properties["CellSize"] = LuaValue.Userdata("UDim2", RbxUDim2(0.0, 90.0, 0.0, 44.0))
        properties["CellPadding"] = LuaValue.Userdata("UDim2", RbxUDim2(0.0, 6.0, 0.0, 6.0))
        properties["HorizontalAlignment"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("HorizontalAlignment", "Left", 1))
        properties["VerticalAlignment"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("VerticalAlignment", "Top", 1))
        properties["SortOrder"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("SortOrder", "LayoutOrder", 2))
      }
      "UIPageLayout", "UITableLayout" -> {
        properties["Padding"] = LuaValue.Userdata("UDim", RbxUDim(0.0, 4.0))
        properties["FillDirection"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("FillDirection", "Vertical", 1))
        properties["SortOrder"] =
          LuaValue.Userdata("EnumItem", RbxEnumItem("SortOrder", "LayoutOrder", 2))
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
      "UIScale" -> {
        properties["Scale"] = LuaValue.Num(1.0)
      }
      "UISizeConstraint" -> {
        properties["MinSize"] = LuaValue.Userdata("Vector2", RbxVector2(0.0, 0.0))
        properties["MaxSize"] = LuaValue.Userdata("Vector2", RbxVector2(10000.0, 10000.0))
      }
      "UITextSizeConstraint" -> {
        properties["MinTextSize"] = LuaValue.Num(1.0)
        properties["MaxTextSize"] = LuaValue.Num(100.0)
      }
      "LocalScript" -> {
        properties["Disabled"] = LuaValue.Bool(false)
        properties["Enabled"] = LuaValue.Bool(true)
        properties["Source"] = LuaValue.Str("")
      }
      "ModuleScript" -> {
        properties["Source"] = LuaValue.Str("")
      }
      "StringValue" -> properties["Value"] = LuaValue.Str("")
      "NumberValue", "IntValue" -> properties["Value"] = LuaValue.Num(0.0)
      "BoolValue" -> properties["Value"] = LuaValue.Bool(false)
      "ObjectValue" -> properties["Value"] = LuaValue.Nil
      "Color3Value" -> properties["Value"] = LuaValue.Userdata("Color3", RbxColor3(1.0, 1.0, 1.0))
    }
  }

  fun setParent(newParent: RbxInstance?) {
    parent?.children?.remove(this)
    parent = newParent
    if (newParent != null && this !in newParent.children) {
      newParent.children.add(this)
    }
  }

  fun cloneDeep(register: (RbxInstance) -> Unit): RbxInstance {
    val copy = RbxInstance(className, name)
    copy.properties.clear()
    copy.properties.putAll(properties)
    copy.attributes.putAll(attributes)
    register(copy)
    for (child in children.toList()) {
      val childCopy = child.cloneDeep(register)
      childCopy.setParent(copy)
    }
    return copy
  }

  fun isA(targetClass: String): Boolean {
    if (className == targetClass || targetClass == "Instance") return true
    return when (targetClass) {
      "GuiObject" -> className in setOf(
        "Frame", "ScrollingFrame", "CanvasGroup", "ViewportFrame",
        "TextLabel", "TextButton", "ImageLabel", "ImageButton", "TextBox",
      )
      "GuiBase2d" -> isA("GuiObject") || isA("LayerCollector")
      "GuiButton" -> className == "TextButton" || className == "ImageButton"
      "LayerCollector" -> className == "ScreenGui" || className == "BillboardGui" || className == "SurfaceGui"
      "UIBase", "UIComponent" -> className.startsWith("UI")
      "UIConstraint" -> className in setOf("UIAspectRatioConstraint", "UISizeConstraint", "UITextSizeConstraint")
      "UILayout" -> className in setOf("UIListLayout", "UIGridLayout", "UIPageLayout", "UITableLayout")
      "BaseScript", "LuaSourceContainer" -> className in setOf("LocalScript", "Script", "ModuleScript")
      "ValueBase" -> className.endsWith("Value")
      else -> false
    }
  }

  fun findFirstChild(childName: String, recursive: Boolean = false): RbxInstance? {
    for (c in children.toList()) {
      if (c.name == childName) return c
    }
    if (recursive) {
      for (c in children.toList()) {
        val found = c.findFirstChild(childName, true)
        if (found != null) return found
      }
    }
    return null
  }

  fun findFirstChildOfClass(cls: String): RbxInstance? =
    children.toList().firstOrNull { it.className == cls }

  fun findFirstChildWhichIsA(cls: String, recursive: Boolean = false): RbxInstance? {
    for (c in children.toList()) {
      if (c.isA(cls)) return c
    }
    if (recursive) {
      for (c in children.toList()) {
        val found = c.findFirstChildWhichIsA(cls, true)
        if (found != null) return found
      }
    }
    return null
  }

  fun isDescendantOf(ancestor: RbxInstance): Boolean {
    var cur = parent
    while (cur != null) {
      if (cur === ancestor) return true
      cur = cur.parent
    }
    return false
  }

  fun getFullName(): String {
    val parts = mutableListOf<String>()
    var cur: RbxInstance? = this
    while (cur != null) {
      parts.add(cur.name)
      cur = cur.parent
    }
    return parts.asReversed().joinToString(".")
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
