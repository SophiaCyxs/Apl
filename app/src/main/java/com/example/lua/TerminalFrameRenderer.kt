package com.example.lua

import kotlin.math.roundToInt

data class TerminalCell(
  val ch: Char = ' ',
  val fgArgb: Long = 0xFFE6EDF3L,
  val bgArgb: Long = 0xFF06090DL,
  val bold: Boolean = false,
)

data class InteractiveButtonInfo(
  val instanceId: Int,
  val name: String,
  val text: String,
)

data class TerminalFrameSnapshot(
  val frameNumber: Long,
  val deltaMs: Long,
  val columns: Int,
  val rows: Int,
  val grid: List<List<TerminalCell>>,
  val activeGuiCount: Int,
  val interactiveButtons: List<InteractiveButtonInfo>,
  val diffSummary: String?,
)

class TerminalFrameRenderer {
  private var lastSignatures = mutableMapOf<Int, String>()

  fun reset() {
    lastSignatures.clear()
  }

  fun render(
    roots: List<RbxInstance>,
    drawings: List<RbxDrawingObject>,
    columns: Int,
    rows: Int,
    frameNumber: Long,
    deltaMs: Long,
    darkTheme: Boolean,
    monochrome: Boolean = false,
  ): TerminalFrameSnapshot {
    val baseFg =
      when {
        monochrome && darkTheme -> 0xFFF4F4F5L
        monochrome && !darkTheme -> 0xFF09090BL
        darkTheme -> 0xFFE6EDF3L
        else -> 0xFF0F172AL
      }
    val baseBg =
      when {
        monochrome && darkTheme -> 0xFF050507L
        monochrome && !darkTheme -> 0xFFFAFAFBL
        darkTheme -> 0xFF06090DL
        else -> 0xFFF1F5F9L
      }
    val borderFg =
      when {
        monochrome && darkTheme -> 0xFF27272AL
        monochrome && !darkTheme -> 0xFFD4D4D8L
        darkTheme -> 0xFF233042L
        else -> 0xFFCBD5E1L
      }

    val grid = Array(rows) { Array(columns) { TerminalCell(' ', baseFg, baseBg, false) } }

    // Outer viewport bezel
    for (x in 0 until columns) {
      grid[0][x] = TerminalCell(if (x == 0) '┌' else if (x == columns - 1) '┐' else '─', borderFg, baseBg)
      grid[rows - 1][x] =
        TerminalCell(if (x == 0) '└' else if (x == columns - 1) '┘' else '─', borderFg, baseBg)
    }
    for (y in 1 until rows - 1) {
      grid[y][0] = TerminalCell('│', borderFg, baseBg)
      grid[y][columns - 1] = TerminalCell('│', borderFg, baseBg)
    }

    val interactiveButtons = mutableListOf<InteractiveButtonInfo>()
    val currentSignatures = mutableMapOf<Int, String>()
    val changedNames = mutableListOf<String>()
    var activeGuis = 0

    // Collect ScreenGuis from roots (PlayerGui, CoreGui, HiddenGui)
    val screenGuis = mutableListOf<RbxInstance>()
    for (root in roots) {
      for (child in root.children) {
        if (child.className == "ScreenGui") {
          val enabled = (child.properties["Enabled"] as? LuaValue.Bool)?.value ?: true
          if (enabled) {
            activeGuis++
            screenGuis.add(child)
          }
        } else if (isRenderableUi(child.className)) {
          screenGuis.add(child)
        }
      }
    }

    val sortedGuis =
      screenGuis.sortedBy { (it.properties["DisplayOrder"] as? LuaValue.Num)?.value ?: 0.0 }

    val rootRect = RectI(1, 1, columns - 2, rows - 2)

    fun renderNode(node: RbxInstance, parentRect: RectI) {
      if (node.className == "ScreenGui") {
        node.children.toList().forEach { renderNode(it, parentRect) }
        return
      }
      if (!isRenderableUi(node.className)) return
      val visible = (node.properties["Visible"] as? LuaValue.Bool)?.value ?: true
      if (!visible) return

      val rect = computeNodeRect(node, parentRect)
      if (rect.w <= 0 || rect.h <= 0) return

      val bgColor = (node.properties["BackgroundColor3"] as? LuaValue.Userdata)?.data as? RbxColor3
        ?: RbxColor3(0.14, 0.19, 0.28)
      val bgTrans = (node.properties["BackgroundTransparency"] as? LuaValue.Num)?.value ?: 0.0
      val borderPx = (node.properties["BorderSizePixel"] as? LuaValue.Num)?.value ?: 1.0
      val strokeChild = node.children.firstOrNull { it.className == "UIStroke" }
      val hasCorner = node.children.any { it.className == "UICorner" }
      val strokeColor =
        (strokeChild?.properties?.get("Color") as? LuaValue.Userdata)?.data as? RbxColor3
          ?: (node.properties["BorderColor3"] as? LuaValue.Userdata)?.data as? RbxColor3
          ?: RbxColor3(0.13, 0.83, 0.93)

      val bgArgb =
        if (bgTrans >= 0.95) baseBg
        else blendArgb(baseBg, bgColor.toArgbLong(), (1.0 - bgTrans).coerceIn(0.0, 1.0))

      val borderArgb =
        if (strokeChild != null || borderPx > 0.0) strokeColor.toArgbLong() else bgArgb

      val x0 = rect.x.coerceIn(1, columns - 2)
      val y0 = rect.y.coerceIn(1, rows - 2)
      val x1 = (rect.x + rect.w - 1).coerceIn(x0, columns - 2)
      val y1 = (rect.y + rect.h - 1).coerceIn(y0, rows - 2)

      // Fill background
      if (bgTrans < 0.95) {
        val fillChar = if (bgTrans > 0.6) '░' else ' '
        val fillFg = if (bgTrans > 0.6) bgColor.toArgbLong() else baseFg
        for (py in y0..y1) {
          for (px in x0..x1) {
            grid[py][px] = TerminalCell(fillChar, fillFg, bgArgb, false)
          }
        }
      }

      // Draw border if tall/wide enough and has border/UIStroke
      val drawBorder = (strokeChild != null || borderPx > 0.0) && (x1 - x0 >= 1) && (y1 - y0 >= 1)
      if (drawBorder) {
        val tl = if (hasCorner) '╭' else '┌'
        val tr = if (hasCorner) '╮' else '┐'
        val bl = if (hasCorner) '╰' else '└'
        val br = if (hasCorner) '╯' else '┘'
        for (px in x0..x1) {
          grid[y0][px] = TerminalCell(if (px == x0) tl else if (px == x1) tr else '─', borderArgb, bgArgb)
          grid[y1][px] = TerminalCell(if (px == x0) bl else if (px == x1) br else '─', borderArgb, bgArgb)
        }
        for (py in (y0 + 1) until y1) {
          grid[py][x0] = TerminalCell('│', borderArgb, bgArgb)
          grid[py][x1] = TerminalCell('│', borderArgb, bgArgb)
        }
      }

      // Render TextLabel / TextButton / TextBox / ImageLabel text
      val text = (node.properties["Text"] as? LuaValue.Str)?.value
      if (text != null) {
        val textColor =
          (node.properties["TextColor3"] as? LuaValue.Userdata)?.data as? RbxColor3
            ?: RbxColor3(0.95, 0.97, 1.0)
        val fgArgb = textColor.toArgbLong()
        val isButton = node.className == "TextButton"
        val displayText = if (isButton) "[$text]" else text
        val innerX0 = if (drawBorder && x1 - x0 >= 2) x0 + 1 else x0
        val innerX1 = if (drawBorder && x1 - x0 >= 2) x1 - 1 else x1
        val innerW = (innerX1 - innerX0 + 1).coerceAtLeast(1)
        val innerY0 = if (drawBorder && y1 - y0 >= 2) y0 + 1 else y0
        val innerY1 = if (drawBorder && y1 - y0 >= 2) y1 - 1 else y1

        val xAlign =
          ((node.properties["TextXAlignment"] as? LuaValue.Userdata)?.data as? RbxEnumItem)?.name
            ?: "Center"
        val yAlign =
          ((node.properties["TextYAlignment"] as? LuaValue.Userdata)?.data as? RbxEnumItem)?.name
            ?: "Center"

        val lines = displayText.split('\n')
        val startRow =
          when (yAlign) {
            "Top" -> innerY0
            "Bottom" -> (innerY1 - lines.size + 1).coerceAtLeast(innerY0)
            else -> innerY0 + ((innerY1 - innerY0 + 1 - lines.size).coerceAtLeast(0)) / 2
          }

        lines.forEachIndexed { lineIdx, lineStr ->
          val py = startRow + lineIdx
          if (py in innerY0..innerY1) {
            val clipped = if (lineStr.length > innerW) lineStr.take(innerW) else lineStr
            val startCol =
              when (xAlign) {
                "Left" -> innerX0
                "Right" -> (innerX1 - clipped.length + 1).coerceAtLeast(innerX0)
                else -> innerX0 + (innerW - clipped.length) / 2
              }
            clipped.forEachIndexed { ci, ch ->
              val px = startCol + ci
              if (px in innerX0..innerX1) {
                grid[py][px] = TerminalCell(ch, fgArgb, bgArgb, bold = isButton)
              }
            }
          }
        }

        if (isButton) {
          interactiveButtons.add(InteractiveButtonInfo(node.id, node.name, text))
        }
      } else if (node.className == "ImageLabel") {
        val label = "[IMG]"
        val py = (y0 + y1) / 2
        val startCol = (x0 + ((x1 - x0 + 1) - label.length) / 2).coerceAtLeast(x0)
        label.forEachIndexed { idx, ch ->
          val px = startCol + idx
          if (px in x0..x1) grid[py][px] = TerminalCell(ch, borderArgb, bgArgb, bold = true)
        }
      }

      // Track signature for real-time frame diffing
      val sig =
        "${node.className}:${node.name}|${rect.x},${rect.y},${rect.w},${rect.h}|${bgColor.rgbLabel()}|${text.orEmpty()}|$visible"
      currentSignatures[node.id] = sig
      if (lastSignatures[node.id] != sig) {
        val desc =
          if (text != null) "${node.name}(\"${text.take(18)}\")"
          else "${node.name}(${rect.w}x${rect.h}@${rect.x},${rect.y})"
        changedNames.add(desc)
      }

      // Layout children (check UIListLayout!)
      val listLayout = node.children.firstOrNull { it.className == "UIListLayout" }
      val innerPad = if (drawBorder && x1 - x0 >= 2 && y1 - y0 >= 2) 1 else 0
      val childContainer =
        RectI(
          x0 + innerPad,
          y0 + innerPad,
          (x1 - x0 + 1 - innerPad * 2).coerceAtLeast(1),
          (y1 - y0 + 1 - innerPad * 2).coerceAtLeast(1),
        )

      val renderChildren = node.children.filter { isRenderableUi(it.className) }
        .sortedBy { (it.properties["ZIndex"] as? LuaValue.Num)?.value ?: 1.0 }

      if (listLayout != null) {
        val dir =
          ((listLayout.properties["FillDirection"] as? LuaValue.Userdata)?.data as? RbxEnumItem)
            ?.name ?: "Vertical"
        val padUdim =
          (listLayout.properties["Padding"] as? LuaValue.Userdata)?.data as? RbxUDim
            ?: RbxUDim(0.0, 0.0)
        val gap = ((padUdim.offset / 8.0).roundToInt()).coerceAtLeast(0)
        var cursorX = childContainer.x
        var cursorY = childContainer.y
        for (child in renderChildren) {
          val vis = (child.properties["Visible"] as? LuaValue.Bool)?.value ?: true
          if (!vis) continue
          val sz = computeNodeSize(child, childContainer)
          val slotRect =
            if (dir == "Horizontal") {
              val r = RectI(cursorX, cursorY, sz.first, childContainer.h)
              cursorX += sz.first + gap
              r
            } else {
              val r = RectI(cursorX, cursorY, childContainer.w, sz.second)
              cursorY += sz.second + gap
              r
            }
          renderNodeWithOverrideRect(child, slotRect, ::renderNode)
        }
      } else {
        for (child in renderChildren) {
          renderNode(child, childContainer)
        }
      }
    }

    for (gui in sortedGuis) {
      renderNode(gui, rootRect)
    }

    // Render executor Drawing objects overlay
    for (d in drawings.toList()) {
      if (d.removed || !d.visible) continue
      val fg = d.color.toArgbLong()
      val dSig = "${d.drawingType}:${d.position}:${d.size}:${d.text}:${d.color}:${d.visible}"
      val syntheticId = -System.identityHashCode(d)
      currentSignatures[syntheticId] = dSig
      if (lastSignatures[syntheticId] != dSig) {
        changedNames.add("Drawing.${d.drawingType}")
      }

      when (d.drawingType.lowercase()) {
        "square" -> {
          val x0 = (1 + d.position.x.roundToInt()).coerceIn(1, columns - 2)
          val y0 = (1 + d.position.y.roundToInt()).coerceIn(1, rows - 2)
          val w = d.size.x.roundToInt().coerceAtLeast(2)
          val h = d.size.y.roundToInt().coerceAtLeast(2)
          val x1 = (x0 + w - 1).coerceIn(x0, columns - 2)
          val y1 = (y0 + h - 1).coerceIn(y0, rows - 2)
          for (px in x0..x1) {
            grid[y0][px] = TerminalCell(if (px == x0) '┏' else if (px == x1) '┓' else '━', fg, grid[y0][px].bgArgb, true)
            grid[y1][px] = TerminalCell(if (px == x0) '┗' else if (px == x1) '┛' else '━', fg, grid[y1][px].bgArgb, true)
          }
          for (py in (y0 + 1) until y1) {
            grid[py][x0] = TerminalCell('┃', fg, grid[py][x0].bgArgb, true)
            grid[py][x1] = TerminalCell('┃', fg, grid[py][x1].bgArgb, true)
            if (d.filled) {
              for (px in (x0 + 1) until x1) {
                grid[py][px] = TerminalCell('▓', fg, grid[py][px].bgArgb, false)
              }
            }
          }
        }
        "text" -> {
          val x0 = (1 + d.position.x.roundToInt()).coerceIn(1, columns - 2)
          val y0 = (1 + d.position.y.roundToInt()).coerceIn(1, rows - 2)
          d.text.forEachIndexed { idx, ch ->
            val px = x0 + idx
            if (px in 1 until columns - 1) {
              grid[y0][px] = TerminalCell(ch, fg, grid[y0][px].bgArgb, bold = true)
            }
          }
        }
        "line" -> {
          drawLine(
            grid,
            (1 + d.from.x.roundToInt()).coerceIn(1, columns - 2),
            (1 + d.from.y.roundToInt()).coerceIn(1, rows - 2),
            (1 + d.to.x.roundToInt()).coerceIn(1, columns - 2),
            (1 + d.to.y.roundToInt()).coerceIn(1, rows - 2),
            fg,
          )
        }
        "circle" -> {
          val cx = (1 + d.position.x.roundToInt()).coerceIn(2, columns - 3)
          val cy = (1 + d.position.y.roundToInt()).coerceIn(2, rows - 3)
          val r = d.radius.roundToInt().coerceIn(1, 8)
          for (angleDeg in 0 until 360 step 15) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val px = (cx + kotlin.math.cos(rad) * r * 1.8).roundToInt()
            val py = (cy + kotlin.math.sin(rad) * r).roundToInt()
            if (py in 1 until rows - 1 && px in 1 until columns - 1) {
              grid[py][px] = TerminalCell('•', fg, grid[py][px].bgArgb, true)
            }
          }
        }
      }
    }

    // Top status bar overlay inside terminal bezel
    val header = " RBX-FRAME #$frameNumber | ${deltaMs}ms | GUIs:$activeGuis "
    val headerFg =
      when {
        monochrome && darkTheme -> 0xFFFAFAFAL
        monochrome && !darkTheme -> 0xFF09090BL
        darkTheme -> 0xFF38BDF8L
        else -> 0xFF0284C7L
      }
    header.forEachIndexed { i, ch ->
      val px = 2 + i
      if (px < columns - 2) {
        grid[0][px] = TerminalCell(ch, headerFg, baseBg, bold = true)
      }
    }

    lastSignatures = currentSignatures
    val diffSummary =
      if (changedNames.isEmpty()) null
      else "[FRAME #${frameNumber.toString().padStart(4, '0')} Δ${deltaMs}ms] ${changedNames.take(3).joinToString(", ")}"

    val finalGrid =
      if (!monochrome) {
        grid.map { it.toList() }
      } else {
        grid.map { row ->
          row.map { cell ->
            cell.copy(
              fgArgb = toGrayscale(cell.fgArgb),
              bgArgb = toGrayscale(cell.bgArgb),
            )
          }
        }
      }

    return TerminalFrameSnapshot(
      frameNumber = frameNumber,
      deltaMs = deltaMs,
      columns = columns,
      rows = rows,
      grid = finalGrid,
      activeGuiCount = activeGuis,
      interactiveButtons = interactiveButtons,
      diffSummary = diffSummary,
    )
  }

  private fun toGrayscale(argb: Long): Long {
    val a = (argb ushr 24) and 0xFF
    val r = ((argb shr 16) and 0xFF).toInt()
    val g = ((argb shr 8) and 0xFF).toInt()
    val b = (argb and 0xFF).toInt()
    val lum = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255).toLong()
    return (a shl 24) or (lum shl 16) or (lum shl 8) or lum
  }

  private inline fun renderNodeWithOverrideRect(
    node: RbxInstance,
    slotRect: RectI,
    renderer: (RbxInstance, RectI) -> Unit,
  ) {
    // Temporarily evaluate node against slotRect by computing with slot size
    val savedPos = node.properties["Position"]
    node.properties["Position"] = LuaValue.Userdata("UDim2", RbxUDim2(0.0, 0.0, 0.0, 0.0))
    renderer(node, slotRect)
    if (savedPos != null) node.properties["Position"] = savedPos
  }

  private fun isRenderableUi(className: String): Boolean =
    className in setOf("ScreenGui", "Frame", "ScrollingFrame", "TextLabel", "TextButton", "ImageLabel", "TextBox")

  private data class RectI(val x: Int, val y: Int, val w: Int, val h: Int)

  private fun computeNodeSize(node: RbxInstance, parent: RectI): Pair<Int, Int> {
    val size = (node.properties["Size"] as? LuaValue.Userdata)?.data as? RbxUDim2
      ?: RbxUDim2(0.4, 0.0, 0.2, 0.0)
    val w = (size.xScale * parent.w + size.xOffset / 8.0).roundToInt().coerceAtLeast(1)
    val h = (size.yScale * parent.h + size.yOffset / 14.0).roundToInt().coerceAtLeast(1)
    return w to h
  }

  private fun computeNodeRect(node: RbxInstance, parent: RectI): RectI {
    val pos = (node.properties["Position"] as? LuaValue.Userdata)?.data as? RbxUDim2 ?: RbxUDim2()
    val anchor = (node.properties["AnchorPoint"] as? LuaValue.Userdata)?.data as? RbxVector2 ?: RbxVector2()
    val (w, h) = computeNodeSize(node, parent)
    val rawX = parent.x + (pos.xScale * parent.w + pos.xOffset / 8.0).roundToInt() - (anchor.x * w).roundToInt()
    val rawY = parent.y + (pos.yScale * parent.h + pos.yOffset / 14.0).roundToInt() - (anchor.y * h).roundToInt()
    return RectI(rawX, rawY, w, h)
  }

  private fun drawLine(
    grid: Array<Array<TerminalCell>>,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
    fg: Long,
  ) {
    var cx = x0
    var cy = y0
    val dx = kotlin.math.abs(x1 - x0)
    val dy = -kotlin.math.abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy
    while (true) {
      if (cy in grid.indices && cx in grid[0].indices) {
        val ch =
          when {
            dx > kotlin.math.abs(dy) * 2 -> '─'
            kotlin.math.abs(dy) > dx * 2 -> '│'
            (sx == sy) -> '╲'
            else -> '╱'
          }
        grid[cy][cx] = TerminalCell(ch, fg, grid[cy][cx].bgArgb, true)
      }
      if (cx == x1 && cy == y1) break
      val e2 = 2 * err
      if (e2 >= dy) {
        err += dy
        cx += sx
      }
      if (e2 <= dx) {
        err += dx
        cy += sy
      }
    }
  }

  private fun blendArgb(bg: Long, fg: Long, alpha: Double): Long {
    val br = ((bg shr 16) and 0xFF).toInt()
    val bgC = ((bg shr 8) and 0xFF).toInt()
    val bb = (bg and 0xFF).toInt()

    val fr = ((fg shr 16) and 0xFF).toInt()
    val fgC = ((fg shr 8) and 0xFF).toInt()
    val fb = (fg and 0xFF).toInt()

    val r = (br + (fr - br) * alpha).roundToInt().coerceIn(0, 255)
    val g = (bgC + (fgC - bgC) * alpha).roundToInt().coerceIn(0, 255)
    val b = (bb + (fb - bb) * alpha).roundToInt().coerceIn(0, 255)
    return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
  }
}
