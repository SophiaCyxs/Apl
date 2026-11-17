package com.example.lua

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface GuiDrawCommand {
  data class PushClip(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val cornerRadius: Float = 0f,
  ) : GuiDrawCommand

  data object PopClip : GuiDrawCommand

  data class PushRotate(
    val degrees: Float,
    val pivotX: Float,
    val pivotY: Float,
  ) : GuiDrawCommand

  data object PopRotate : GuiDrawCommand

  data class DrawRect(
    val instanceId: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val cornerRadius: Float,
    val backgroundArgb: Long,
    val backgroundAlpha: Float,
    val gradientStops: List<Pair<Float, Long>>?,
    val gradientRotationDeg: Float,
    val strokeArgb: Long,
    val strokeAlpha: Float,
    val strokeThickness: Float,
  ) : GuiDrawCommand

  data class DrawText(
    val instanceId: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String,
    val textArgb: Long,
    val textAlpha: Float,
    val textSize: Float,
    val textScaled: Boolean,
    val xAlign: String,
    val yAlign: String,
    val fontName: String,
    val isBold: Boolean,
  ) : GuiDrawCommand

  data class DrawImageBadge(
    val instanceId: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val imageId: String,
    val tintArgb: Long,
    val alpha: Float,
  ) : GuiDrawCommand

  data class DrawScrollBar(
    val trackX: Float,
    val trackY: Float,
    val trackW: Float,
    val trackH: Float,
    val thumbY: Float,
    val thumbH: Float,
    val thumbArgb: Long,
  ) : GuiDrawCommand

  // Executor Drawing.* primitives
  data class DrawingSquare(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val cornerRadius: Float,
    val argb: Long,
    val alpha: Float,
    val thickness: Float,
    val filled: Boolean,
  ) : GuiDrawCommand

  data class DrawingCircle(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val argb: Long,
    val alpha: Float,
    val thickness: Float,
    val filled: Boolean,
  ) : GuiDrawCommand

  data class DrawingLine(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val argb: Long,
    val alpha: Float,
    val thickness: Float,
  ) : GuiDrawCommand

  data class DrawingText(
    val x: Float,
    val y: Float,
    val text: String,
    val textSize: Float,
    val argb: Long,
    val outlineArgb: Long,
    val alpha: Float,
    val center: Boolean,
    val outline: Boolean,
  ) : GuiDrawCommand

  data class DrawingPolygon(
    val points: List<Pair<Float, Float>>,
    val argb: Long,
    val alpha: Float,
    val thickness: Float,
    val filled: Boolean,
  ) : GuiDrawCommand
}

data class GuiHitTarget(
  val instanceId: Int,
  val name: String,
  val className: String,
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float,
  val isClickable: Boolean,
  val isDraggable: Boolean,
  val isScrollable: Boolean,
  val label: String?,
) {
  fun contains(px: Float, py: Float): Boolean =
    px >= x && px <= (x + width) && py >= y && py <= (y + height)
}

data class GuiRenderScene(
  val frameNumber: Long,
  val deltaMs: Long,
  val width: Float,
  val height: Float,
  val commands: List<GuiDrawCommand>,
  val hitTargets: List<GuiHitTarget>,
  val activeGuiCount: Int,
  val diffSummary: String?,
)

class GuiDrawingProcessor {
  private var lastSignatures = mutableMapOf<Int, String>()

  fun reset() {
    lastSignatures.clear()
  }

  fun process(
    roots: List<RbxInstance>,
    drawings: List<RbxDrawingObject>,
    viewportW: Float,
    viewportH: Float,
    frameNumber: Long,
    deltaMs: Long,
    monochromeGui: Boolean = false,
  ): GuiRenderScene {
    val safeW = viewportW.coerceAtLeast(120f)
    val safeH = viewportH.coerceAtLeast(80f)
    val commands = mutableListOf<GuiDrawCommand>()
    val hitTargets = mutableListOf<GuiHitTarget>()
    val currentSignatures = mutableMapOf<Int, String>()
    val changedNames = mutableListOf<String>()
    var activeGuis = 0

    val screenGuis = mutableListOf<RbxInstance>()
    for (root in roots) {
      for (child in root.children.toList()) {
        if (child.className == "ScreenGui") {
          val enabled = (child.properties["Enabled"] as? LuaValue.Bool)?.value ?: true
          if (enabled) {
            activeGuis++
            screenGuis.add(child)
          }
        } else if (isRenderableUi(child.className)) {
          activeGuis++
          screenGuis.add(child)
        }
      }
    }

    val sortedGuis =
      screenGuis.sortedBy { (it.properties["DisplayOrder"] as? LuaValue.Num)?.value ?: 0.0 }

    val rootRect = RectF(0f, 0f, safeW, safeH)
    val clipStack = ArrayDeque<RectF>()

    fun intersectRect(a: RectF, b: RectF): RectF {
      val x0 = max(a.x, b.x)
      val y0 = max(a.y, b.y)
      val x1 = min(a.x + a.w, b.x + b.w)
      val y1 = min(a.y + a.h, b.y + b.h)
      return RectF(x0, y0, (x1 - x0).coerceAtLeast(0f), (y1 - y0).coerceAtLeast(0f))
    }

    fun currentClippedRect(r: RectF): RectF {
      var out = r
      for (c in clipStack) out = intersectRect(out, c)
      return out
    }

    fun processNode(node: RbxInstance, parentRect: RectF, overrideRect: RectF? = null) {
      if (node.className == "ScreenGui") {
        val children =
          node.children.toList()
            .filter { isRenderableUi(it.className) }
            .sortedBy { (it.properties["ZIndex"] as? LuaValue.Num)?.value ?: 1.0 }
        for (c in children) processNode(c, parentRect, null)
        return
      }
      if (!isRenderableUi(node.className)) return
      val visible = (node.properties["Visible"] as? LuaValue.Bool)?.value ?: true
      if (!visible) return

      var rect = overrideRect ?: computeNodeRect(node, parentRect)
      val aspect = node.children.firstOrNull { it.className == "UIAspectRatioConstraint" }
      if (aspect != null) {
        val ratio = ((aspect.properties["AspectRatio"] as? LuaValue.Num)?.value ?: 1.0).toFloat()
          .coerceIn(0.1f, 10f)
        val curW = rect.w
        val curH = rect.h
        if (curW > 0f && curH > 0f) {
          val fittedW = min(curW, curH * ratio)
          val fittedH = fittedW / ratio
          rect = RectF(
            rect.x + (curW - fittedW) * 0.5f,
            rect.y + (curH - fittedH) * 0.5f,
            fittedW,
            fittedH,
          )
        }
      }
      if (rect.w <= 0.5f || rect.h <= 0.5f) return

      val rotation = ((node.properties["Rotation"] as? LuaValue.Num)?.value ?: 0.0).toFloat()
      val hasRotation = abs(rotation) > 0.01f
      if (hasRotation) {
        commands.add(GuiDrawCommand.PushRotate(rotation, rect.x + rect.w * 0.5f, rect.y + rect.h * 0.5f))
      }

      // UICorner
      val cornerNode = node.children.firstOrNull { it.className == "UICorner" }
      val cornerRadius =
        if (cornerNode != null) {
          val udim = (cornerNode.properties["CornerRadius"] as? LuaValue.Userdata)?.data as? RbxUDim
            ?: RbxUDim(0.0, 8.0)
          val raw = (udim.scale * min(rect.w, rect.h) + udim.offset).toFloat()
          raw.coerceIn(0f, min(rect.w, rect.h) * 0.5f)
        } else {
          0f
        }

      // Background + UIGradient
      val bgColor = (node.properties["BackgroundColor3"] as? LuaValue.Userdata)?.data as? RbxColor3
        ?: RbxColor3(0.12, 0.14, 0.18)
      val bgTrans = ((node.properties["BackgroundTransparency"] as? LuaValue.Num)?.value ?: 0.0).toFloat()
        .coerceIn(0f, 1f)
      val bgAlpha = (1f - bgTrans).coerceIn(0f, 1f)
      val bgArgb = maybeGray(bgColor.toArgbLong(), monochromeGui)

      val gradNode = node.children.firstOrNull { it.className == "UIGradient" }
      val gradEnabled = (gradNode?.properties?.get("Enabled") as? LuaValue.Bool)?.value ?: true
      val gradientStops: List<Pair<Float, Long>>?
      val gradientRot: Float
      if (gradNode != null && gradEnabled) {
        val seq = (gradNode.properties["Color"] as? LuaValue.Userdata)?.data as? RbxColorSequence
        gradientStops =
          seq?.keypoints?.sortedBy { it.time }?.map { kp ->
            val blended = RbxColor3(
              bgColor.r * kp.value.r,
              bgColor.g * kp.value.g,
              bgColor.b * kp.value.b,
            )
            kp.time.toFloat().coerceIn(0f, 1f) to maybeGray(blended.toArgbLong(), monochromeGui)
          }?.takeIf { it.size >= 2 }
        gradientRot = ((gradNode.properties["Rotation"] as? LuaValue.Num)?.value ?: 0.0).toFloat()
      } else {
        gradientStops = null
        gradientRot = 0f
      }

      // UIStroke or BorderSizePixel
      val strokeNode = node.children.firstOrNull { it.className == "UIStroke" }
      val borderPx = ((node.properties["BorderSizePixel"] as? LuaValue.Num)?.value ?: 1.0).toFloat()
      val strokeColor: RbxColor3
      val strokeThickness: Float
      val strokeAlpha: Float
      if (strokeNode != null) {
        strokeColor =
          (strokeNode.properties["Color"] as? LuaValue.Userdata)?.data as? RbxColor3
            ?: RbxColor3(0.85, 0.85, 0.9)
        strokeThickness =
          ((strokeNode.properties["Thickness"] as? LuaValue.Num)?.value ?: 1.5).toFloat()
            .coerceIn(0f, 12f)
        val stTrans =
          ((strokeNode.properties["Transparency"] as? LuaValue.Num)?.value ?: 0.0).toFloat()
            .coerceIn(0f, 1f)
        strokeAlpha = (1f - stTrans).coerceIn(0f, 1f)
      } else if (borderPx > 0f) {
        strokeColor =
          (node.properties["BorderColor3"] as? LuaValue.Userdata)?.data as? RbxColor3
            ?: RbxColor3(0.24, 0.28, 0.35)
        strokeThickness = borderPx.coerceIn(0f, 6f)
        strokeAlpha = bgAlpha
      } else {
        strokeColor = RbxColor3(0.0, 0.0, 0.0)
        strokeThickness = 0f
        strokeAlpha = 0f
      }

      commands.add(
        GuiDrawCommand.DrawRect(
          instanceId = node.id,
          x = rect.x,
          y = rect.y,
          width = rect.w,
          height = rect.h,
          cornerRadius = cornerRadius,
          backgroundArgb = bgArgb,
          backgroundAlpha = bgAlpha,
          gradientStops = gradientStops,
          gradientRotationDeg = gradientRot,
          strokeArgb = maybeGray(strokeColor.toArgbLong(), monochromeGui),
          strokeAlpha = strokeAlpha,
          strokeThickness = strokeThickness,
        )
      )

      // Hit target registration (before children so children sit on top)
      val isButton = node.className == "TextButton" || node.className == "ImageButton"
      val isScroll = node.className == "ScrollingFrame"
      val isDraggable = (node.properties["Draggable"] as? LuaValue.Bool)?.value == true
      val isActive = (node.properties["Active"] as? LuaValue.Bool)?.value ?: isButton
      val hasClickSignal =
        node.mouseButton1Click.connections.isNotEmpty() ||
          node.mouseButton1Down.connections.isNotEmpty() ||
          node.mouseButton1Up.connections.isNotEmpty() ||
          node.activated.connections.isNotEmpty()
      val labelText = (node.properties["Text"] as? LuaValue.Str)?.value
      if (isButton || isScroll || isDraggable || isActive || hasClickSignal) {
        val clippedHit = currentClippedRect(rect)
        if (clippedHit.w > 1f && clippedHit.h > 1f) {
          hitTargets.add(
            GuiHitTarget(
              instanceId = node.id,
              name = node.name,
              className = node.className,
              x = clippedHit.x,
              y = clippedHit.y,
              width = clippedHit.w,
              height = clippedHit.h,
              isClickable = isButton || hasClickSignal,
              isDraggable = isDraggable,
              isScrollable = isScroll,
              label = labelText,
            )
          )
        }
      }

      // Compute padded content box for text & children
      val paddingNode = node.children.firstOrNull { it.className == "UIPadding" }
      val padTop = evalUDim(paddingNode?.properties?.get("PaddingTop"), rect.h)
      val padBottom = evalUDim(paddingNode?.properties?.get("PaddingBottom"), rect.h)
      val padLeft = evalUDim(paddingNode?.properties?.get("PaddingLeft"), rect.w)
      val padRight = evalUDim(paddingNode?.properties?.get("PaddingRight"), rect.w)
      val contentRect = RectF(
        rect.x + padLeft,
        rect.y + padTop,
        (rect.w - padLeft - padRight).coerceAtLeast(1f),
        (rect.h - padTop - padBottom).coerceAtLeast(1f),
      )

      // Text or Image content
      if (node.className == "TextLabel" || node.className == "TextButton" || node.className == "TextBox") {
        val text = labelText ?: ""
        if (text.isNotEmpty()) {
          val textColor =
            (node.properties["TextColor3"] as? LuaValue.Userdata)?.data as? RbxColor3
              ?: RbxColor3(0.95, 0.96, 0.98)
          val textTrans = ((node.properties["TextTransparency"] as? LuaValue.Num)?.value ?: 0.0).toFloat()
            .coerceIn(0f, 1f)
          val textSize = ((node.properties["TextSize"] as? LuaValue.Num)?.value ?: 14.0).toFloat()
            .coerceIn(7f, 48f)
          val textScaled = (node.properties["TextScaled"] as? LuaValue.Bool)?.value ?: false
          val xAlign =
            ((node.properties["TextXAlignment"] as? LuaValue.Userdata)?.data as? RbxEnumItem)?.name
              ?: "Center"
          val yAlign =
            ((node.properties["TextYAlignment"] as? LuaValue.Userdata)?.data as? RbxEnumItem)?.name
              ?: "Center"
          val fontName =
            ((node.properties["Font"] as? LuaValue.Userdata)?.data as? RbxEnumItem)?.name ?: "Gotham"
          commands.add(
            GuiDrawCommand.DrawText(
              instanceId = node.id,
              x = contentRect.x + 4f,
              y = contentRect.y + 2f,
              width = (contentRect.w - 8f).coerceAtLeast(4f),
              height = (contentRect.h - 4f).coerceAtLeast(4f),
              text = text,
              textArgb = maybeGray(textColor.toArgbLong(), monochromeGui),
              textAlpha = (1f - textTrans).coerceIn(0f, 1f),
              textSize = textSize,
              textScaled = textScaled,
              xAlign = xAlign,
              yAlign = yAlign,
              fontName = fontName,
              isBold = isButton || fontName.contains("Bold", ignoreCase = true),
            )
          )
        }
      } else if (node.className == "ImageLabel" || node.className == "ImageButton") {
        val imageId = (node.properties["Image"] as? LuaValue.Str)?.value ?: ""
        val imgColor =
          (node.properties["ImageColor3"] as? LuaValue.Userdata)?.data as? RbxColor3
            ?: RbxColor3(1.0, 1.0, 1.0)
        val imgTrans = ((node.properties["ImageTransparency"] as? LuaValue.Num)?.value ?: 0.0).toFloat()
          .coerceIn(0f, 1f)
        commands.add(
          GuiDrawCommand.DrawImageBadge(
            instanceId = node.id,
            x = contentRect.x,
            y = contentRect.y,
            width = contentRect.w,
            height = contentRect.h,
            imageId = imageId,
            tintArgb = maybeGray(imgColor.toArgbLong(), monochromeGui),
            alpha = (1f - imgTrans).coerceIn(0f, 1f),
          )
        )
      }

      // Signature for frame diff logs
      val sig =
        "${node.className}:${node.name}|${rect.x.roundToInt()},${rect.y.roundToInt()},${rect.w.roundToInt()},${rect.h.roundToInt()}|${bgColor.rgbLabel()}|${labelText.orEmpty()}|$cornerRadius|$strokeThickness"
      currentSignatures[node.id] = sig
      if (lastSignatures[node.id] != sig) {
        val desc =
          if (labelText != null) "${node.name}(\"${labelText.take(16)}\")"
          else "${node.name}(${rect.w.roundToInt()}x${rect.h.roundToInt()})"
        changedNames.add(desc)
      }

      // Children layout + clip
      val clips = (node.properties["ClipsDescendants"] as? LuaValue.Bool)?.value ?: isScroll
      if (clips) {
        clipStack.addLast(rect)
        commands.add(GuiDrawCommand.PushClip(rect.x, rect.y, rect.w, rect.h, cornerRadius))
      }

      val renderChildren =
        node.children.toList()
          .filter { isRenderableUi(it.className) }
          .sortedWith(
            compareBy<RbxInstance>(
              { (it.properties["ZIndex"] as? LuaValue.Num)?.value ?: 1.0 },
              { (it.properties["LayoutOrder"] as? LuaValue.Num)?.value ?: 0.0 },
            )
          )

      // ScrollingFrame canvas offset + virtual canvas rect
      val scrollOffset: Pair<Float, Float>
      val layoutArea: RectF
      if (isScroll) {
        val canvasSize =
          (node.properties["CanvasSize"] as? LuaValue.Userdata)?.data as? RbxUDim2
            ?: RbxUDim2(0.0, 0.0, 2.0, 0.0)
        val canvasW = max(contentRect.w, (canvasSize.xScale * contentRect.w + canvasSize.xOffset).toFloat())
        val canvasH = max(contentRect.h, (canvasSize.yScale * contentRect.h + canvasSize.yOffset).toFloat())
        val maxScrollX = (canvasW - contentRect.w).coerceAtLeast(0f)
        val maxScrollY = (canvasH - contentRect.h).coerceAtLeast(0f)
        val curPos =
          (node.properties["CanvasPosition"] as? LuaValue.Userdata)?.data as? RbxVector2
            ?: RbxVector2(0.0, 0.0)
        val sx = curPos.x.toFloat().coerceIn(0f, maxScrollX)
        val sy = curPos.y.toFloat().coerceIn(0f, maxScrollY)
        scrollOffset = sx to sy
        layoutArea = RectF(contentRect.x - sx, contentRect.y - sy, canvasW, canvasH)
      } else {
        scrollOffset = 0f to 0f
        layoutArea = contentRect
      }

      val listLayout = node.children.firstOrNull { it.className == "UIListLayout" }
      val gridLayout = node.children.firstOrNull { it.className == "UIGridLayout" }

      if (gridLayout != null) {
        val cellSize =
          (gridLayout.properties["CellSize"] as? LuaValue.Userdata)?.data as? RbxUDim2
            ?: RbxUDim2(0.0, 90.0, 0.0, 44.0)
        val cellPad =
          (gridLayout.properties["CellPadding"] as? LuaValue.Userdata)?.data as? RbxUDim2
            ?: RbxUDim2(0.0, 6.0, 0.0, 6.0)
        val cw = (cellSize.xScale * layoutArea.w + cellSize.xOffset).toFloat().coerceAtLeast(16f)
        val ch = (cellSize.yScale * layoutArea.h + cellSize.yOffset).toFloat().coerceAtLeast(16f)
        val gapX = (cellPad.xScale * layoutArea.w + cellPad.xOffset).toFloat().coerceAtLeast(0f)
        val gapY = (cellPad.yScale * layoutArea.h + cellPad.yOffset).toFloat().coerceAtLeast(0f)

        var cx = layoutArea.x
        var cy = layoutArea.y
        for (child in renderChildren) {
          val vis = (child.properties["Visible"] as? LuaValue.Bool)?.value ?: true
          if (!vis) continue
          if (cx + cw > layoutArea.x + layoutArea.w + 0.5f && cx > layoutArea.x) {
            cx = layoutArea.x
            cy += ch + gapY
          }
          processNode(child, layoutArea, RectF(cx, cy, cw, ch))
          cx += cw + gapX
        }
      } else if (listLayout != null) {
        val dir =
          ((listLayout.properties["FillDirection"] as? LuaValue.Userdata)?.data as? RbxEnumItem)?.name
            ?: "Vertical"
        val hAlign =
          ((listLayout.properties["HorizontalAlignment"] as? LuaValue.Userdata)?.data as? RbxEnumItem)
            ?.name ?: "Left"
        val vAlign =
          ((listLayout.properties["VerticalAlignment"] as? LuaValue.Userdata)?.data as? RbxEnumItem)
            ?.name ?: "Top"
        val padUdim =
          (listLayout.properties["Padding"] as? LuaValue.Userdata)?.data as? RbxUDim
            ?: RbxUDim(0.0, 4.0)
        val gap =
          (padUdim.scale * (if (dir == "Horizontal") layoutArea.w else layoutArea.h) + padUdim.offset)
            .toFloat()
            .coerceAtLeast(0f)

        val visibleChildren =
          renderChildren.filter { (it.properties["Visible"] as? LuaValue.Bool)?.value ?: true }
        val sizes = visibleChildren.map { computeNodeSize(it, layoutArea) }

        if (dir == "Horizontal") {
          val totalW = sizes.sumOf { it.first.toDouble() }.toFloat() + gap * max(0, sizes.size - 1)
          var cx =
            when (hAlign) {
              "Center" -> layoutArea.x + (layoutArea.w - totalW) * 0.5f
              "Right" -> layoutArea.x + (layoutArea.w - totalW)
              else -> layoutArea.x
            }
          visibleChildren.forEachIndexed { idx, child ->
            val (cw, ch) = sizes[idx]
            val cy =
              when (vAlign) {
                "Center" -> layoutArea.y + (layoutArea.h - ch) * 0.5f
                "Bottom" -> layoutArea.y + (layoutArea.h - ch)
                else -> layoutArea.y
              }
            processNode(child, layoutArea, RectF(cx, cy, cw, ch))
            cx += cw + gap
          }
        } else {
          val totalH = sizes.sumOf { it.second.toDouble() }.toFloat() + gap * max(0, sizes.size - 1)
          var cy =
            when (vAlign) {
              "Center" -> layoutArea.y + (layoutArea.h - totalH) * 0.5f
              "Bottom" -> layoutArea.y + (layoutArea.h - totalH)
              else -> layoutArea.y
            }
          visibleChildren.forEachIndexed { idx, child ->
            val (cw, ch) = sizes[idx]
            val cx =
              when (hAlign) {
                "Center" -> layoutArea.x + (layoutArea.w - cw) * 0.5f
                "Right" -> layoutArea.x + (layoutArea.w - cw)
                else -> layoutArea.x
              }
            processNode(child, layoutArea, RectF(cx, cy, cw, ch))
            cy += ch + gap
          }
        }
      } else {
        for (child in renderChildren) {
          processNode(child, layoutArea, null)
        }
      }

      // Scrollbar thumb inside ScrollingFrame
      if (isScroll && layoutArea.h > contentRect.h + 1f) {
        val barThick =
          ((node.properties["ScrollBarThickness"] as? LuaValue.Num)?.value ?: 6.0).toFloat()
            .coerceIn(2f, 12f)
        val barColor =
          (node.properties["ScrollBarImageColor3"] as? LuaValue.Userdata)?.data as? RbxColor3
            ?: RbxColor3(0.7, 0.7, 0.75)
        val ratioVisible = (contentRect.h / layoutArea.h).coerceIn(0.12f, 1f)
        val thumbH = max(14f, (rect.h - 4f) * ratioVisible)
        val maxScroll = (layoutArea.h - contentRect.h).coerceAtLeast(1f)
        val scrollRatio = (scrollOffset.second / maxScroll).coerceIn(0f, 1f)
        val thumbY = rect.y + 2f + scrollRatio * ((rect.h - 4f) - thumbH)
        commands.add(
          GuiDrawCommand.DrawScrollBar(
            trackX = rect.x + rect.w - barThick - 2f,
            trackY = rect.y + 2f,
            trackW = barThick,
            trackH = (rect.h - 4f).coerceAtLeast(4f),
            thumbY = thumbY,
            thumbH = thumbH,
            thumbArgb = maybeGray(barColor.toArgbLong(), monochromeGui),
          )
        )
      }

      if (clips) {
        clipStack.removeLast()
        commands.add(GuiDrawCommand.PopClip)
      }
      if (hasRotation) {
        commands.add(GuiDrawCommand.PopRotate)
      }
    }

    for (gui in sortedGuis) {
      processNode(gui, rootRect, null)
    }

    // Executor Drawing.* primitives sorted by ZIndex
    val sortedDrawings = drawings.toList().filter { !it.removed && it.visible }.sortedBy { it.zIndex }
    for (d in sortedDrawings) {
      val dSig =
        "${d.drawingType}:${d.position}:${d.size}:${d.from}:${d.to}:${d.text}:${d.color}:${d.filled}:${d.rounding}:${d.radius}"
      val syntheticId = -System.identityHashCode(d)
      currentSignatures[syntheticId] = dSig
      if (lastSignatures[syntheticId] != dSig) {
        changedNames.add("Drawing.${d.drawingType}")
      }

      val argb = maybeGray(d.color.toArgbLong(), monochromeGui)
      val alpha = d.transparency.toFloat().coerceIn(0f, 1f)
      val thickness = d.thickness.toFloat().coerceIn(0.5f, 12f)

      when (d.drawingType.lowercase()) {
        "square" ->
          commands.add(
            GuiDrawCommand.DrawingSquare(
              x = d.position.x.toFloat(),
              y = d.position.y.toFloat(),
              width = d.size.x.toFloat().coerceAtLeast(1f),
              height = d.size.y.toFloat().coerceAtLeast(1f),
              cornerRadius = d.rounding.toFloat().coerceAtLeast(0f),
              argb = argb,
              alpha = alpha,
              thickness = thickness,
              filled = d.filled,
            )
          )
        "circle" ->
          commands.add(
            GuiDrawCommand.DrawingCircle(
              centerX = d.position.x.toFloat(),
              centerY = d.position.y.toFloat(),
              radius = d.radius.toFloat().coerceAtLeast(1f),
              argb = argb,
              alpha = alpha,
              thickness = thickness,
              filled = d.filled,
            )
          )
        "line" ->
          commands.add(
            GuiDrawCommand.DrawingLine(
              fromX = d.from.x.toFloat(),
              fromY = d.from.y.toFloat(),
              toX = d.to.x.toFloat(),
              toY = d.to.y.toFloat(),
              argb = argb,
              alpha = alpha,
              thickness = thickness,
            )
          )
        "text" ->
          commands.add(
            GuiDrawCommand.DrawingText(
              x = d.position.x.toFloat(),
              y = d.position.y.toFloat(),
              text = d.text,
              textSize = d.textSize.toFloat().coerceIn(8f, 42f),
              argb = argb,
              outlineArgb = maybeGray(d.outlineColor.toArgbLong(), monochromeGui),
              alpha = alpha,
              center = d.center,
              outline = d.outline,
            )
          )
        "triangle" ->
          commands.add(
            GuiDrawCommand.DrawingPolygon(
              points = listOf(
                d.pointA.x.toFloat() to d.pointA.y.toFloat(),
                d.pointB.x.toFloat() to d.pointB.y.toFloat(),
                d.pointC.x.toFloat() to d.pointC.y.toFloat(),
              ),
              argb = argb,
              alpha = alpha,
              thickness = thickness,
              filled = d.filled,
            )
          )
        "quad" ->
          commands.add(
            GuiDrawCommand.DrawingPolygon(
              points = listOf(
                d.pointA.x.toFloat() to d.pointA.y.toFloat(),
                d.pointB.x.toFloat() to d.pointB.y.toFloat(),
                d.pointC.x.toFloat() to d.pointC.y.toFloat(),
                d.pointD.x.toFloat() to d.pointD.y.toFloat(),
              ),
              argb = argb,
              alpha = alpha,
              thickness = thickness,
              filled = d.filled,
            )
          )
      }
    }

    lastSignatures = currentSignatures
    val diffSummary =
      if (changedNames.isEmpty()) null
      else "[FRAME #${frameNumber.toString().padStart(4, '0')} Δ${deltaMs}ms] ${changedNames.take(3).joinToString(", ")}"

    return GuiRenderScene(
      frameNumber = frameNumber,
      deltaMs = deltaMs,
      width = safeW,
      height = safeH,
      commands = commands,
      hitTargets = hitTargets,
      activeGuiCount = activeGuis,
      diffSummary = diffSummary,
    )
  }

  private fun evalUDim(v: LuaValue?, refSize: Float): Float {
    val u = (v as? LuaValue.Userdata)?.data as? RbxUDim ?: return 0f
    return (u.scale * refSize + u.offset).toFloat()
  }

  private fun isRenderableUi(className: String): Boolean =
    className in setOf(
      "ScreenGui",
      "Frame",
      "ScrollingFrame",
      "TextLabel",
      "TextButton",
      "ImageLabel",
      "ImageButton",
      "TextBox",
    )

  private data class RectF(val x: Float, val y: Float, val w: Float, val h: Float)

  private fun computeNodeSize(node: RbxInstance, parent: RectF): Pair<Float, Float> {
    val size = (node.properties["Size"] as? LuaValue.Userdata)?.data as? RbxUDim2
      ?: RbxUDim2(0.4, 0.0, 0.25, 0.0)
    val w = (size.xScale * parent.w + size.xOffset).toFloat().coerceAtLeast(1f)
    val h = (size.yScale * parent.h + size.yOffset).toFloat().coerceAtLeast(1f)
    return w to h
  }

  private fun computeNodeRect(node: RbxInstance, parent: RectF): RectF {
    val pos = (node.properties["Position"] as? LuaValue.Userdata)?.data as? RbxUDim2 ?: RbxUDim2()
    val anchor = (node.properties["AnchorPoint"] as? LuaValue.Userdata)?.data as? RbxVector2 ?: RbxVector2()
    val (w, h) = computeNodeSize(node, parent)
    val rawX = parent.x + (pos.xScale * parent.w + pos.xOffset).toFloat() - (anchor.x.toFloat() * w)
    val rawY = parent.y + (pos.yScale * parent.h + pos.yOffset).toFloat() - (anchor.y.toFloat() * h)
    return RectF(rawX, rawY, w, h)
  }

  private fun maybeGray(argb: Long, monochrome: Boolean): Long {
    if (!monochrome) return argb
    val a = (argb ushr 24) and 0xFF
    val r = ((argb shr 16) and 0xFF).toInt()
    val g = ((argb shr 8) and 0xFF).toInt()
    val b = (argb and 0xFF).toInt()
    val lum = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255).toLong()
    return (a shl 24) or (lum shl 16) or (lum shl 8) or lum
  }
}
