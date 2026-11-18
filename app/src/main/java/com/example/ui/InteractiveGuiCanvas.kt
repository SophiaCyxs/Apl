package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lua.GuiDrawCommand
import com.example.lua.GuiRenderScene
import com.example.ui.theme.LocalBloxPalette
import com.example.ui.theme.LocalBloxVisuals
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun InteractiveGuiCanvas(
  scene: GuiRenderScene?,
  isRunning: Boolean,
  codeFont: FontFamily,
  onViewportSizeDp: (Float, Float) -> Unit,
  onTapTarget: (Int) -> Unit,
  onTextBoxTap: (instanceId: Int, currentText: String, name: String) -> Unit = { _, _, _ -> },
  onDragTarget: (instanceId: Int, dxNorm: Double, dyNorm: Double, dxLogical: Double, dyLogical: Double) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalBloxPalette.current
  val visuals = LocalBloxVisuals.current
  val density = LocalDensity.current
  val textMeasurer = rememberTextMeasurer()

  val latestScene by rememberUpdatedState(scene)
  val latestTap by rememberUpdatedState(onTapTarget)
  val latestTextBoxTap by rememberUpdatedState(onTextBoxTap)
  val latestDrag by rememberUpdatedState(onDragTarget)

  var pressedInstanceId by remember { mutableStateOf<Int?>(null) }
  var activeDragId by remember { mutableStateOf<Int?>(null) }

  val shape = RoundedCornerShape(16.dp)
  val elev = visuals.panelShadowDp.coerceIn(0f, 24f).dp
  val bgAlpha = if (visuals.hasBackdrop) visuals.panelOpacity.coerceIn(0.45f, 1f) else 1f

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(240.dp)
        .let { m -> if (elev > 0.dp) m.shadow(elev, shape, clip = false) else m }
        .clip(shape)
        .background(palette.terminalBg.copy(alpha = bgAlpha))
        .border(
          1.dp,
          if (isRunning) MaterialTheme.colorScheme.primary else palette.border.copy(alpha = if (visuals.hasBackdrop) 0.78f else 1f),
          shape,
        )
        .onSizeChanged { intSize ->
          if (intSize.width > 0 && intSize.height > 0) {
            with(density) {
              onViewportSizeDp(intSize.width.toDp().value, intSize.height.toDp().value)
            }
          }
        }
        .testTag("gui_preview_canvas"),
  ) {
    val activeScene = scene
    if (activeScene == null || (activeScene.frameNumber == 0L && activeScene.commands.isEmpty() && activeScene.notifications.isEmpty())) {
      Box(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        Text(
          text = "> drawing processor idle · run ScreenGui / LocalScript / UI Lib script",
          style =
            TextStyle(
              fontFamily = codeFont,
              fontSize = 12.sp,
              color = palette.textMuted,
            ),
        )
      }
    } else {
      Canvas(
        modifier =
          Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
              detectTapGestures(
                onPress = { offset ->
                  val s = latestScene
                  if (s != null && size.width > 0 && size.height > 0) {
                    val lx = offset.x * (s.width / size.width.toFloat())
                    val ly = offset.y * (s.height / size.height.toFloat())
                    val hit =
                      s.hitTargets.asReversed().firstOrNull {
                        (it.isClickable || it.isTextInput) && it.contains(lx, ly)
                      }
                    pressedInstanceId = hit?.instanceId
                  }
                  tryAwaitRelease()
                  pressedInstanceId = null
                },
                onTap = { offset ->
                  val s = latestScene ?: return@detectTapGestures
                  if (size.width <= 0 || size.height <= 0) return@detectTapGestures
                  val lx = offset.x * (s.width / size.width.toFloat())
                  val ly = offset.y * (s.height / size.height.toFloat())
                  val hit =
                    s.hitTargets.asReversed().firstOrNull {
                      (it.isClickable || it.isTextInput) && it.contains(lx, ly)
                    }
                  if (hit != null) {
                    if (hit.isTextInput) {
                      latestTextBoxTap(hit.instanceId, hit.label ?: "", hit.name)
                    } else {
                      latestTap(hit.instanceId)
                    }
                  }
                },
              )
            }
            .pointerInput(Unit) {
              detectDragGestures(
                onDragStart = { offset ->
                  val s = latestScene ?: return@detectDragGestures
                  if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                  val lx = offset.x * (s.width / size.width.toFloat())
                  val ly = offset.y * (s.height / size.height.toFloat())
                  val dragTarget =
                    s.hitTargets.asReversed().firstOrNull {
                      (it.isScrollable || it.isDraggable) && it.contains(lx, ly)
                    }
                  activeDragId = dragTarget?.instanceId
                },
                onDrag = { change, dragAmount ->
                  val id = activeDragId ?: return@detectDragGestures
                  val s = latestScene ?: return@detectDragGestures
                  if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                  change.consume()
                  val dxNorm = (dragAmount.x / size.width.toFloat()).toDouble()
                  val dyNorm = (dragAmount.y / size.height.toFloat()).toDouble()
                  val dxLogical = (dragAmount.x * (s.width / size.width.toFloat())).toDouble()
                  val dyLogical = (dragAmount.y * (s.height / size.height.toFloat())).toDouble()
                  latestDrag(id, dxNorm, dyNorm, dxLogical, dyLogical)
                },
                onDragEnd = { activeDragId = null },
                onDragCancel = { activeDragId = null },
              )
            },
      ) {
        val sx = if (activeScene.width > 0f) size.width / activeScene.width else 1f
        val sy = if (activeScene.height > 0f) size.height / activeScene.height else 1f
        val scaleAvg = (sx + sy) * 0.5f

        var saveDepth = 0
        try {
          val gridStep = 24f * scaleAvg
          val gridColor = palette.border.copy(alpha = 0.25f)
          var gx = gridStep
          while (gx < size.width) {
            drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
            gx += gridStep
          }
          var gy = gridStep
          while (gy < size.height) {
            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            gy += gridStep
          }

          for (cmd in activeScene.commands) {
            when (cmd) {
              is GuiDrawCommand.PushClip -> {
                drawContext.canvas.save()
                saveDepth++
                val l = cmd.x * sx
                val t = cmd.y * sy
                val r = (cmd.x + cmd.width) * sx
                val b = (cmd.y + cmd.height) * sy
                val cr = cmd.cornerRadius * scaleAvg
                if (cr > 0.5f) {
                  val path =
                    Path().apply {
                      addRoundRect(
                        RoundRect(
                          rect = Rect(l, t, r, b),
                          cornerRadius = CornerRadius(cr, cr),
                        )
                      )
                    }
                  drawContext.canvas.clipPath(path)
                } else {
                  drawContext.canvas.clipRect(l, t, r, b)
                }
              }

              GuiDrawCommand.PopClip -> {
                if (saveDepth > 0) {
                  drawContext.canvas.restore()
                  saveDepth--
                }
              }

              is GuiDrawCommand.PushRotate -> {
                drawContext.canvas.save()
                saveDepth++
                val px = cmd.pivotX * sx
                val py = cmd.pivotY * sy
                drawContext.canvas.translate(px, py)
                drawContext.canvas.rotate(cmd.degrees)
                drawContext.canvas.translate(-px, -py)
              }

              GuiDrawCommand.PopRotate -> {
                if (saveDepth > 0) {
                  drawContext.canvas.restore()
                  saveDepth--
                }
              }

              is GuiDrawCommand.DrawRect -> {
                val l = cmd.x * sx
                val t = cmd.y * sy
                val w = cmd.width * sx
                val h = cmd.height * sy
                val cr = cmd.cornerRadius * scaleAvg
                val topLeft = Offset(l, t)
                val rectSize = Size(w, h)
                val corner = CornerRadius(cr, cr)

                if (cmd.backgroundAlpha > 0.005f) {
                  val stops = cmd.gradientStops
                  if (stops != null && stops.size >= 2) {
                    val rad = Math.toRadians(cmd.gradientRotationDeg.toDouble())
                    val dx = cos(rad).toFloat() * w * 0.5f
                    val dy = sin(rad).toFloat() * h * 0.5f
                    val cx = l + w * 0.5f
                    val cy = t + h * 0.5f
                    val brush =
                      Brush.linearGradient(
                        colorStops = stops.map { (pos, argb) -> pos to Color(argb) }.toTypedArray(),
                        start = Offset(cx - dx, cy - dy),
                        end = Offset(cx + dx, cy + dy),
                      )
                    drawRoundRect(
                      brush = brush,
                      topLeft = topLeft,
                      size = rectSize,
                      cornerRadius = corner,
                      alpha = cmd.backgroundAlpha,
                    )
                  } else {
                    drawRoundRect(
                      color = Color(cmd.backgroundArgb),
                      topLeft = topLeft,
                      size = rectSize,
                      cornerRadius = corner,
                      alpha = cmd.backgroundAlpha,
                    )
                  }
                }

                if (pressedInstanceId == cmd.instanceId) {
                  drawRoundRect(
                    color = Color.White,
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = corner,
                    alpha = 0.18f,
                  )
                }

                if (cmd.strokeThickness > 0.05f && cmd.strokeAlpha > 0.005f) {
                  val strokeW = (cmd.strokeThickness * scaleAvg).coerceAtLeast(1f)
                  drawRoundRect(
                    color = Color(cmd.strokeArgb),
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = corner,
                    alpha = cmd.strokeAlpha,
                    style = Stroke(width = strokeW),
                  )
                }
              }

              is GuiDrawCommand.DrawText -> {
                if (cmd.textAlpha <= 0.005f || cmd.text.isEmpty()) continue
                val boxX = cmd.x * sx
                val boxY = cmd.y * sy
                val boxW = max(8f, cmd.width * sx)
                val boxH = max(8f, cmd.height * sy)
                val computedFontPx =
                  if (cmd.textScaled) (boxH * 0.52f).coerceIn(9f * scaleAvg, 28f * scaleAvg)
                  else (cmd.textSize * scaleAvg).coerceIn(8f * scaleAvg, 34f * scaleAvg)
                val fontSp = with(density) { computedFontPx.toSp() }
                val textFamily =
                  if (cmd.fontName.contains("Mono", true) || cmd.fontName.equals("Code", true)) {
                    codeFont
                  } else {
                    FontFamily.SansSerif
                  }
                val effectiveAlpha = if (cmd.isPlaceholder) cmd.textAlpha * 0.7f else cmd.textAlpha
                val style =
                  TextStyle(
                    fontFamily = textFamily,
                    fontSize = fontSp,
                    fontWeight = if (cmd.isBold) FontWeight.SemiBold else FontWeight.Normal,
                    color = Color(cmd.textArgb).copy(alpha = effectiveAlpha),
                  )
                val maxLines =
                  if (cmd.textWrapped) max(1, (boxH / (computedFontPx * 1.15f)).toInt())
                  else 1
                val layout =
                  textMeasurer.measure(
                    text = AnnotatedString(cmd.text),
                    style = style,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = cmd.textWrapped,
                    maxLines = maxLines,
                    constraints = Constraints(maxWidth = boxW.roundToInt(), maxHeight = boxH.roundToInt()),
                  )
                val tx =
                  when (cmd.xAlign) {
                    "Left" -> boxX
                    "Right" -> boxX + (boxW - layout.size.width).coerceAtLeast(0f)
                    else -> boxX + (boxW - layout.size.width) * 0.5f
                  }
                val ty =
                  when (cmd.yAlign) {
                    "Top" -> boxY
                    "Bottom" -> boxY + (boxH - layout.size.height).coerceAtLeast(0f)
                    else -> boxY + (boxH - layout.size.height) * 0.5f
                  }
                drawText(layout, topLeft = Offset(tx, ty))
              }

              is GuiDrawCommand.DrawImageBadge -> {
                if (cmd.alpha <= 0.005f) continue
                val l = cmd.x * sx
                val t = cmd.y * sy
                val w = cmd.width * sx
                val h = cmd.height * sy
                val tint = Color(cmd.tintArgb).copy(alpha = cmd.alpha * 0.85f)
                val iconSz = min(w, h) * 0.45f
                val cx = l + w * 0.5f
                val cy = t + h * 0.5f
                val half = iconSz * 0.5f
                if (cmd.imageId == "viewport") {
                  // 3D wireframe cube icon for ViewportFrame
                  val offset3d = half * 0.35f
                  drawRect(
                    color = tint,
                    topLeft = Offset(cx - half, cy - half + offset3d),
                    size = Size(iconSz - offset3d, iconSz - offset3d),
                    style = Stroke(width = 1.4f * scaleAvg),
                  )
                  drawRect(
                    color = tint,
                    topLeft = Offset(cx - half + offset3d, cy - half),
                    size = Size(iconSz - offset3d, iconSz - offset3d),
                    style = Stroke(width = 1.4f * scaleAvg),
                  )
                } else {
                  drawRoundRect(
                    color = tint,
                    topLeft = Offset(cx - half, cy - half),
                    size = Size(iconSz, iconSz),
                    cornerRadius = CornerRadius(4f * scaleAvg, 4f * scaleAvg),
                    style = Stroke(width = 1.5f * scaleAvg),
                  )
                  drawCircle(
                    color = tint,
                    radius = iconSz * 0.14f,
                    center = Offset(cx - half * 0.35f, cy - half * 0.3f),
                  )
                  val mountain =
                    Path().apply {
                      moveTo(cx - half * 0.7f, cy + half * 0.55f)
                      lineTo(cx - half * 0.1f, cy - half * 0.1f)
                      lineTo(cx + half * 0.2f, cy + half * 0.25f)
                      lineTo(cx + half * 0.45f, cy - half * 0.05f)
                      lineTo(cx + half * 0.7f, cy + half * 0.55f)
                      close()
                    }
                  drawPath(mountain, color = tint)
                }
              }

              is GuiDrawCommand.DrawScrollBar -> {
                val tx = cmd.trackX * sx
                val ty = cmd.trackY * sy
                val tw = cmd.trackW * sx
                val th = cmd.trackH * sy
                drawRoundRect(
                  color = Color(cmd.thumbArgb),
                  topLeft = Offset(tx, ty),
                  size = Size(tw, th),
                  cornerRadius = CornerRadius(tw * 0.5f, tw * 0.5f),
                  alpha = 0.18f,
                )
                drawRoundRect(
                  color = Color(cmd.thumbArgb),
                  topLeft = Offset(tx, cmd.thumbY * sy),
                  size = Size(tw, cmd.thumbH * sy),
                  cornerRadius = CornerRadius(tw * 0.5f, tw * 0.5f),
                  alpha = 0.72f,
                )
              }

              is GuiDrawCommand.DrawingSquare -> {
                val cr = cmd.cornerRadius * scaleAvg
                drawRoundRect(
                  color = Color(cmd.argb),
                  topLeft = Offset(cmd.x * sx, cmd.y * sy),
                  size = Size(cmd.width * sx, cmd.height * sy),
                  cornerRadius = CornerRadius(cr, cr),
                  alpha = cmd.alpha,
                  style = if (cmd.filled) Fill else Stroke(width = (cmd.thickness * scaleAvg).coerceAtLeast(1f)),
                )
              }

              is GuiDrawCommand.DrawingCircle -> {
                drawCircle(
                  color = Color(cmd.argb),
                  radius = cmd.radius * scaleAvg,
                  center = Offset(cmd.centerX * sx, cmd.centerY * sy),
                  alpha = cmd.alpha,
                  style = if (cmd.filled) Fill else Stroke(width = (cmd.thickness * scaleAvg).coerceAtLeast(1f)),
                )
              }

              is GuiDrawCommand.DrawingLine -> {
                drawLine(
                  color = Color(cmd.argb),
                  start = Offset(cmd.fromX * sx, cmd.fromY * sy),
                  end = Offset(cmd.toX * sx, cmd.toY * sy),
                  strokeWidth = (cmd.thickness * scaleAvg).coerceAtLeast(1f),
                  cap = StrokeCap.Round,
                  alpha = cmd.alpha,
                )
              }

              is GuiDrawCommand.DrawingText -> {
                val fontSp = with(density) { (cmd.textSize * scaleAvg).toSp() }
                if (cmd.outline) {
                  val outLayout =
                    textMeasurer.measure(
                      text = AnnotatedString(cmd.text),
                      style =
                        TextStyle(
                          fontFamily = codeFont,
                          fontSize = fontSp,
                          fontWeight = FontWeight.SemiBold,
                          color = Color(cmd.outlineArgb).copy(alpha = cmd.alpha),
                        ),
                    )
                  val baseX =
                    if (cmd.center) cmd.x * sx - outLayout.size.width * 0.5f else cmd.x * sx
                  val baseY = cmd.y * sy
                  val off = max(1f, scaleAvg)
                  drawText(outLayout, topLeft = Offset(baseX - off, baseY))
                  drawText(outLayout, topLeft = Offset(baseX + off, baseY))
                  drawText(outLayout, topLeft = Offset(baseX, baseY - off))
                  drawText(outLayout, topLeft = Offset(baseX, baseY + off))
                }
                val layout =
                  textMeasurer.measure(
                    text = AnnotatedString(cmd.text),
                    style =
                      TextStyle(
                        fontFamily = codeFont,
                        fontSize = fontSp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(cmd.argb).copy(alpha = cmd.alpha),
                      ),
                  )
                val tx = if (cmd.center) cmd.x * sx - layout.size.width * 0.5f else cmd.x * sx
                drawText(layout, topLeft = Offset(tx, cmd.y * sy))
              }

              is GuiDrawCommand.DrawingPolygon -> {
                if (cmd.points.size >= 3) {
                  val path =
                    Path().apply {
                      moveTo(cmd.points[0].first * sx, cmd.points[0].second * sy)
                      for (i in 1 until cmd.points.size) {
                        lineTo(cmd.points[i].first * sx, cmd.points[i].second * sy)
                      }
                      close()
                    }
                  drawPath(
                    path = path,
                    color = Color(cmd.argb),
                    alpha = cmd.alpha,
                    style = if (cmd.filled) Fill else Stroke(width = (cmd.thickness * scaleAvg).coerceAtLeast(1f)),
                  )
                }
              }
            }
          }
        } finally {
          while (saveDepth > 0) {
            drawContext.canvas.restore()
            saveDepth--
          }
        }
      }

      // StarterGui:SetCore("SendNotification") Toast Overlay
      val notifs = activeScene.notifications
      if (notifs.isNotEmpty()) {
        Column(
          modifier =
            Modifier
              .align(Alignment.BottomEnd)
              .padding(8.dp)
              .widthIn(max = 220.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          notifs.forEach { n ->
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
              shadowElevation = 4.dp,
              modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
            ) {
              Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                  text = n.title,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (n.text.isNotEmpty()) {
                  Text(
                    text = n.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                    maxLines = 2,
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
