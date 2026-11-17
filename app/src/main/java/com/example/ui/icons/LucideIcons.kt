package com.example.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Clean vector icon set following Lucide's 24x24 grid, 2dp stroke, round cap, round join geometry.
 */
object LucideIcons {
  private fun lucideIcon(name: String, vararg paths: String): ImageVector {
    val builder =
      ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
      )
    val stroke = SolidColor(Color.Black)
    for (d in paths) {
      val nodes = PathParser().parsePathString(d).toNodes()
      builder.addPath(
        pathData = nodes,
        stroke = stroke,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      )
    }
    return builder.build()
  }

  val Play: ImageVector by lazy {
    lucideIcon("lucide_play", "M6 3 L20 12 L6 21 Z")
  }

  val Square: ImageVector by lazy {
    lucideIcon("lucide_square", "M5 3 H19 A2 2 0 0 1 21 5 V19 A2 2 0 0 1 19 21 H5 A2 2 0 0 1 3 19 V5 A2 2 0 0 1 5 3 Z")
  }

  val Pause: ImageVector by lazy {
    lucideIcon(
      "lucide_pause",
      "M6 4 H10 V20 H6 Z",
      "M14 4 H18 V20 H14 Z",
    )
  }

  val Terminal: ImageVector by lazy {
    lucideIcon(
      "lucide_terminal",
      "M4 17 L10 11 L4 5",
      "M12 19 H20",
    )
  }

  val Code2: ImageVector by lazy {
    lucideIcon(
      "lucide_code_2",
      "M18 16 L22 12 L18 8",
      "M6 8 L2 12 L6 16",
      "M14.5 4 L9.5 20",
    )
  }

  val Cpu: ImageVector by lazy {
    lucideIcon(
      "lucide_cpu",
      "M6 4 H18 A2 2 0 0 1 20 6 V18 A2 2 0 0 1 18 20 H6 A2 2 0 0 1 4 18 V6 A2 2 0 0 1 6 4 Z",
      "M9 9 H15 V15 H9 Z",
      "M15 2 V4",
      "M15 20 V22",
      "M2 15 H4",
      "M2 9 H4",
      "M20 15 H22",
      "M20 9 H22",
      "M9 2 V4",
      "M9 20 V22",
    )
  }

  val Settings: ImageVector by lazy {
    lucideIcon(
      "lucide_settings",
      "M12 9 A3 3 0 1 0 12 15 A3 3 0 1 0 12 9 Z",
      "M19.4 15 A1.65 1.65 0 0 0 19.73 16.82 L19.79 16.88 A2 2 0 1 1 16.96 19.71 L16.9 19.65 A1.65 1.65 0 0 0 15.08 19.32 A1.65 1.65 0 0 0 14 20.84 V21 A2 2 0 1 1 10 21 V20.91 A1.65 1.65 0 0 0 9 19.4 A1.65 1.65 0 0 0 7.18 19.73 L7.12 19.79 A2 2 0 1 1 4.29 16.96 L4.35 16.9 A1.65 1.65 0 0 0 4.68 15.08 A1.65 1.65 0 0 0 3.16 14 H3 A2 2 0 1 1 3 10 H3.09 A1.65 1.65 0 0 0 4.6 9 A1.65 1.65 0 0 0 4.27 7.18 L4.21 7.12 A2 2 0 1 1 7.04 4.29 L7.1 4.35 A1.65 1.65 0 0 0 8.92 4.68 H9 A1.65 1.65 0 0 0 10 3.16 V3 A2 2 0 1 1 14 3 V3.09 A1.65 1.65 0 0 0 15 4.6 A1.65 1.65 0 0 0 16.82 4.27 L16.88 4.21 A2 2 0 1 1 19.71 7.04 L19.65 7.1 A1.65 1.65 0 0 0 19.32 8.92 V9 A1.65 1.65 0 0 0 20.84 10 H21 A2 2 0 1 1 21 14 H20.91 A1.65 1.65 0 0 0 19.4 15 Z",
    )
  }

  val Sun: ImageVector by lazy {
    lucideIcon(
      "lucide_sun",
      "M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8 Z",
      "M12 2 V4",
      "M12 20 V22",
      "M4.93 4.93 L6.34 6.34",
      "M17.66 17.66 L19.07 19.07",
      "M2 12 H4",
      "M20 12 H22",
      "M6.34 17.66 L4.93 19.07",
      "M19.07 4.93 L17.66 6.34",
    )
  }

  val Moon: ImageVector by lazy {
    lucideIcon(
      "lucide_moon",
      "M12 3 A6 6 0 0 0 21 12 A9 9 0 1 1 12 3 Z",
    )
  }

  val Trash2: ImageVector by lazy {
    lucideIcon(
      "lucide_trash_2",
      "M3 6 H21",
      "M19 6 V20 A2 2 0 0 1 17 22 H7 A2 2 0 0 1 5 20 V6",
      "M8 6 V4 A2 2 0 0 1 10 2 H14 A2 2 0 0 1 16 4 V6",
      "M10 11 V17",
      "M14 11 V17",
    )
  }

  val Copy: ImageVector by lazy {
    lucideIcon(
      "lucide_copy",
      "M10 8 H20 A2 2 0 0 1 22 10 V20 A2 2 0 0 1 20 22 H10 A2 2 0 0 1 8 20 V10 A2 2 0 0 1 10 8 Z",
      "M4 16 C2.9 16 2 15.1 2 14 V4 C2 2.9 2.9 2 4 2 H14 C15.1 2 16 2.9 16 4",
    )
  }

  val Check: ImageVector by lazy {
    lucideIcon("lucide_check", "M20 6 L9 17 L4 12")
  }

  val Plus: ImageVector by lazy {
    lucideIcon("lucide_plus", "M5 12 H19", "M12 5 V19")
  }

  val Save: ImageVector by lazy {
    lucideIcon(
      "lucide_save",
      "M19 21 H5 A2 2 0 0 1 3 19 V5 A2 2 0 0 1 5 3 H16 L21 8 V19 A2 2 0 0 1 19 21 Z",
      "M17 21 V13 H7 V21",
      "M7 3 V8 H15",
    )
  }

  val FileCode2: ImageVector by lazy {
    lucideIcon(
      "lucide_file_code_2",
      "M4 22 H18 A2 2 0 0 0 20 20 V7 L15 2 H6 A2 2 0 0 0 4 4 V8",
      "M14 2 V8 H20",
      "M9 15 L12 18 L9 21",
      "M5 15 L2 18 L5 21",
    )
  }

  val Layers: ImageVector by lazy {
    lucideIcon(
      "lucide_layers",
      "M12 2 L2 7 L12 12 L22 7 Z",
      "M2 17 L12 22 L22 17",
      "M2 12 L12 17 L22 12",
    )
  }

  val Zap: ImageVector by lazy {
    lucideIcon("lucide_zap", "M13 2 L3 14 H12 L11 22 L21 10 H12 Z")
  }

  val RefreshCw: ImageVector by lazy {
    lucideIcon(
      "lucide_refresh_cw",
      "M3 12 A9 9 0 0 1 18.36 5.64 L21 8",
      "M21 3 V8 H16",
      "M21 12 A9 9 0 0 1 5.64 18.36 L3 16",
      "M3 21 V16 H8",
    )
  }

  val AlertTriangle: ImageVector by lazy {
    lucideIcon(
      "lucide_alert_triangle",
      "M10.29 3.86 L1.82 18 A2 2 0 0 0 3.53 21 H20.47 A2 2 0 0 0 22.18 18 L13.71 3.86 A2 2 0 0 0 10.29 3.86 Z",
      "M12 9 V13",
      "M12 17 H12.01",
    )
  }

  val CheckCircle2: ImageVector by lazy {
    lucideIcon(
      "lucide_check_circle_2",
      "M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 Z",
      "M9 12 L11 14 L15 10",
    )
  }

  val XCircle: ImageVector by lazy {
    lucideIcon(
      "lucide_x_circle",
      "M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 Z",
      "M15 9 L9 15",
      "M9 9 L15 15",
    )
  }

  val ChevronRight: ImageVector by lazy {
    lucideIcon("lucide_chevron_right", "M9 18 L15 12 L9 6")
  }

  val ChevronDown: ImageVector by lazy {
    lucideIcon("lucide_chevron_down", "M6 9 L12 15 L18 9")
  }

  val Gauge: ImageVector by lazy {
    lucideIcon(
      "lucide_gauge",
      "M12 14 L16 10",
      "M3.34 19 A10 10 0 1 1 20.66 19",
    )
  }

  val TypeIcon: ImageVector by lazy {
    lucideIcon(
      "lucide_type",
      "M4 7 V4 H20 V7",
      "M9 20 H15",
      "M12 4 V20",
    )
  }

  val Clock: ImageVector by lazy {
    lucideIcon(
      "lucide_clock",
      "M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 Z",
      "M12 6 V12 L16 14",
    )
  }

  val Monitor: ImageVector by lazy {
    lucideIcon(
      "lucide_monitor",
      "M4 3 H20 A2 2 0 0 1 22 5 V15 A2 2 0 0 1 20 17 H4 A2 2 0 0 1 2 15 V5 A2 2 0 0 1 4 3 Z",
      "M8 21 H16",
      "M12 17 V21",
    )
  }

  val RotateCcw: ImageVector by lazy {
    lucideIcon(
      "lucide_rotate_ccw",
      "M3 12 A9 9 0 1 0 6 5.3 L3 8",
      "M3 3 V8 H8",
    )
  }

  val ShieldCheck: ImageVector by lazy {
    lucideIcon(
      "lucide_shield_check",
      "M12 22 S20 18 20 12 V5 L12 2 L4 5 V12 C4 18 12 22 12 22 Z",
      "M9 12 L11 14 L15 10",
    )
  }

  val Activity: ImageVector by lazy {
    lucideIcon("lucide_activity", "M22 12 H18 L15 21 L9 3 L6 12 H2")
  }

  val SlidersHorizontal: ImageVector by lazy {
    lucideIcon(
      "lucide_sliders_horizontal",
      "M21 4 H14",
      "M10 4 H3",
      "M21 12 H12",
      "M8 12 H3",
      "M21 20 H16",
      "M12 20 H3",
      "M14 2 V6",
      "M8 10 V14",
      "M16 18 V22",
    )
  }

  val MousePointerClick: ImageVector by lazy {
    lucideIcon(
      "lucide_mouse_pointer_click",
      "M9 9 L14 21 L16.5 16.5 L21 14 Z",
      "M7.2 2.2 L8 5.1",
      "M5.1 8 L2.2 7.2",
      "M14 4.1 L12 6",
      "M6 12 L4.1 14",
    )
  }

  val Braces: ImageVector by lazy {
    lucideIcon(
      "lucide_braces",
      "M8 3 H7 A2 2 0 0 0 5 5 V10 A2 2 0 0 1 3 12 A2 2 0 0 1 5 14 V19 A2 2 0 0 0 7 21 H8",
      "M16 3 H17 A2 2 0 0 1 19 5 V10 A2 2 0 0 0 21 12 A2 2 0 0 0 19 14 V19 A2 2 0 0 1 17 21 H16",
    )
  }

  val ArrowDownToLine: ImageVector by lazy {
    lucideIcon(
      "lucide_arrow_down_to_line",
      "M12 17 V3",
      "M6 11 L12 17 L18 11",
      "M19 21 H5",
    )
  }
}
