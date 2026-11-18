package com.example.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import kotlin.math.roundToInt

@Immutable
data class BloxPalette(
  val isDark: Boolean,
  val isMonochrome: Boolean,
  val terminalBg: Color,
  val border: Color,
  val textMuted: Color,
  val success: Color,
  val warning: Color,
  val error: Color,
  val accent: Color,
  val syntax: SyntaxColors,
)

@Immutable
data class BloxVisuals(
  val syntaxGradientEnabled: Boolean = false,
  val syntaxGradientMoving: Boolean = true,
  val fontGradientMoving: Boolean = false,
  val gradientColors: List<Color> = SyntaxGradientPreset.SILVER_MIST.colors,
  val panelOpacity: Float = 0.88f,
  val panelShadowDp: Float = 6f,
  val hasBackdrop: Boolean = true,
) {
  fun effectivePanelAlpha(): Float = if (hasBackdrop) panelOpacity else 1f
}

val LocalBloxVisuals = staticCompositionLocalOf { BloxVisuals() }

val LocalGradientPhaseState = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

val LocalBloxPalette =
  staticCompositionLocalOf {
    BloxPalette(
      isDark = true,
      isMonochrome = true,
      terminalBg = MonoDarkTerminal,
      border = MonoDarkBorder,
      textMuted = MonoDarkMuted,
      success = Color(0xFFE4E4E7),
      warning = Color(0xFFA1A1AA),
      error = Color(0xFFFFFFFF),
      accent = Color(0xFFFAFAFA),
      syntax = MonoDarkSyntax,
    )
  }

@Composable
fun Modifier.bloxSurface(
  shape: Shape = RoundedCornerShape(14.dp),
  color: Color = MaterialTheme.colorScheme.surface,
  borderColor: Color = LocalBloxPalette.current.border,
  alphaOverride: Float? = null,
): Modifier {
  val visuals = LocalBloxVisuals.current
  val palette = LocalBloxPalette.current
  val alpha = (alphaOverride ?: visuals.effectivePanelAlpha()).coerceIn(0.2f, 1f)
  val elev = visuals.panelShadowDp.coerceIn(0f, 14f).dp
  val topRimColor =
    if (palette.isDark) Color.White.copy(alpha = if (visuals.hasBackdrop) 0.16f else 0.10f)
    else borderColor.copy(alpha = 0.85f)
  val bottomRimColor = borderColor.copy(alpha = if (visuals.hasBackdrop) 0.45f else 0.85f)
  val rimBrush =
    remember(topRimColor, bottomRimColor) {
      Brush.verticalGradient(listOf(topRimColor, bottomRimColor))
    }
  return this
    .let { mod ->
      if (elev > 0.5.dp) mod.shadow(elevation = elev, shape = shape, clip = false) else mod
    }
    .background(color.copy(alpha = alpha), shape)
    .border(1.dp, rimBrush, shape)
}

@Composable
fun MovingGradientText(
  text: String,
  style: TextStyle,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  force: Boolean = false,
) {
  val visuals = LocalBloxVisuals.current
  val useGradient = visuals.fontGradientMoving || force
  val effectiveStyle =
    if (useGradient) {
      val phase = LocalGradientPhaseState.current.value
      style.copy(brush = buildMovingGradientBrush(visuals.gradientColors, phase, spanPx = 560f))
    } else {
      style
    }
  Text(
    text = text,
    style = effectiveStyle,
    color = if (useGradient) Color.Unspecified else color,
    fontWeight = fontWeight,
    maxLines = maxLines,
    overflow = overflow,
    modifier = modifier,
  )
}

private val MonoDarkScheme =
  darkColorScheme(
    primary = Color(0xFFFAFAFA),
    onPrimary = Color(0xFF09090B),
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = Color(0xFFA1A1AA),
    onSecondary = Color(0xFF09090B),
    secondaryContainer = Color(0xFF1F1F23),
    onSecondaryContainer = Color(0xFFE4E4E7),
    tertiary = Color(0xFF71717A),
    onTertiary = Color(0xFFFAFAFA),
    background = MonoDarkBg,
    onBackground = MonoDarkText,
    surface = MonoDarkSurface,
    onSurface = MonoDarkText,
    surfaceVariant = MonoDarkSurfaceElev,
    onSurfaceVariant = MonoDarkMuted,
    outline = MonoDarkBorder,
    outlineVariant = Color(0xFF1E1E22),
    error = Color(0xFFFFFFFF),
    onError = Color(0xFF09090B),
  )

private val MonoLightScheme =
  lightColorScheme(
    primary = Color(0xFF09090B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Color(0xFF09090B),
    secondary = Color(0xFF52525B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = Color(0xFF18181B),
    tertiary = Color(0xFF71717A),
    onTertiary = Color(0xFFFFFFFF),
    background = MonoLightBg,
    onBackground = MonoLightText,
    surface = MonoLightSurface,
    onSurface = MonoLightText,
    surfaceVariant = MonoLightSurfaceElev,
    onSurfaceVariant = MonoLightMuted,
    outline = MonoLightBorder,
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFF09090B),
    onError = Color(0xFFFFFFFF),
  )

private val PhosphorDarkScheme =
  darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFDCFCE7),
    secondary = Color(0xFFFACC15),
    onSecondary = Color(0xFF1C1917),
    secondaryContainer = Color(0xFF1A2E22),
    onSecondaryContainer = Color(0xFFBBF7D0),
    background = Color(0xFF070B08),
    onBackground = Color(0xFFDCFCE7),
    surface = Color(0xFF0D1410),
    onSurface = Color(0xFFDCFCE7),
    surfaceVariant = Color(0xFF141F18),
    onSurfaceVariant = Color(0xFF6B8F78),
    outline = Color(0xFF1F3326),
    error = Color(0xFFF87171),
    onError = Color(0xFF280606),
  )

private val PhosphorLightScheme =
  lightColorScheme(
    primary = Color(0xFF15803D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF047857),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF3F7F4),
    onBackground = Color(0xFF0B1F13),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1F13),
    surfaceVariant = Color(0xFFE6EFE9),
    onSurfaceVariant = Color(0xFF4B6354),
    outline = Color(0xFFC6D8CC),
    error = Color(0xFFDC2626),
    onError = Color.White,
  )

private val CobaltDarkScheme =
  darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF04131D),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF031A12),
    secondaryContainer = Color(0xFF0F293A),
    onSecondaryContainer = Color(0xFFBAE6FD),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF111822),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF182230),
    onSurfaceVariant = Color(0xFF8B98A9),
    outline = Color(0xFF233042),
    error = Color(0xFFF87171),
    onError = Color(0xFF280606),
  )

private val CobaltLightScheme =
  lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE9EFF6),
    onSurfaceVariant = Color(0xFF526071),
    outline = Color(0xFFD0DAE5),
    error = Color(0xFFDC2626),
    onError = Color.White,
  )

@Composable
fun BloxLuaTheme(
  darkTheme: Boolean = true,
  themePalette: ThemePalette = ThemePalette.MONOCHROME,
  settings: AppSettings? = null,
  content: @Composable () -> Unit,
) {
  val colorScheme: ColorScheme
  val basePalette: BloxPalette

  when (themePalette) {
    ThemePalette.MONOCHROME -> {
      colorScheme = if (darkTheme) MonoDarkScheme else MonoLightScheme
      basePalette =
        if (darkTheme) {
          BloxPalette(
            isDark = true,
            isMonochrome = true,
            terminalBg = MonoDarkTerminal,
            border = MonoDarkBorder,
            textMuted = MonoDarkMuted,
            success = Color(0xFFE4E4E7),
            warning = Color(0xFFA1A1AA),
            error = Color(0xFFFFFFFF),
            accent = Color(0xFFFAFAFA),
            syntax = MonoDarkSyntax,
          )
        } else {
          BloxPalette(
            isDark = false,
            isMonochrome = true,
            terminalBg = MonoLightTerminal,
            border = MonoLightBorder,
            textMuted = MonoLightMuted,
            success = Color(0xFF18181B),
            warning = Color(0xFF52525B),
            error = Color(0xFF09090B),
            accent = Color(0xFF09090B),
            syntax = MonoLightSyntax,
          )
        }
    }
    ThemePalette.PHOSPHOR -> {
      colorScheme = if (darkTheme) PhosphorDarkScheme else PhosphorLightScheme
      basePalette =
        if (darkTheme) {
          BloxPalette(
            isDark = true,
            isMonochrome = false,
            terminalBg = Color(0xFF040805),
            border = Color(0xFF1F3326),
            textMuted = Color(0xFF6B8F78),
            success = Color(0xFF4ADE80),
            warning = Color(0xFFFACC15),
            error = Color(0xFFF87171),
            accent = Color(0xFF4ADE80),
            syntax = PhosphorDarkSyntax,
          )
        } else {
          BloxPalette(
            isDark = false,
            isMonochrome = false,
            terminalBg = Color(0xFFF6FAF7),
            border = Color(0xFFC6D8CC),
            textMuted = Color(0xFF4B6354),
            success = Color(0xFF15803D),
            warning = Color(0xFFB45309),
            error = Color(0xFFDC2626),
            accent = Color(0xFF15803D),
            syntax = PhosphorLightSyntax,
          )
        }
    }
    ThemePalette.COBALT -> {
      colorScheme = if (darkTheme) CobaltDarkScheme else CobaltLightScheme
      basePalette =
        if (darkTheme) {
          BloxPalette(
            isDark = true,
            isMonochrome = false,
            terminalBg = Color(0xFF06090D),
            border = Color(0xFF233042),
            textMuted = Color(0xFF8B98A9),
            success = Color(0xFF34D399),
            warning = Color(0xFFFBBF24),
            error = Color(0xFFF87171),
            accent = Color(0xFF38BDF8),
            syntax = CobaltDarkSyntax,
          )
        } else {
          BloxPalette(
            isDark = false,
            isMonochrome = false,
            terminalBg = Color(0xFFF8FAFC),
            border = Color(0xFFD0DAE5),
            textMuted = Color(0xFF526071),
            success = Color(0xFF059669),
            warning = Color(0xFFD97706),
            error = Color(0xFFDC2626),
            accent = Color(0xFF0284C7),
            syntax = CobaltLightSyntax,
          )
        }
    }
  }

  val palette =
    if (settings != null) {
      basePalette.copy(
        syntax =
          basePalette.syntax.withOverrides(
            keywordArgb = settings.syntaxKeywordArgb,
            typeArgb = settings.syntaxTypeArgb,
            executorArgb = settings.syntaxExecutorArgb,
            builtinArgb = settings.syntaxBuiltinArgb,
            stringArgb = settings.syntaxStringArgb,
            numberArgb = settings.syntaxNumberArgb,
            commentArgb = settings.syntaxCommentArgb,
            operatorArgb = settings.syntaxOperatorArgb,
          )
      )
    } else {
      basePalette
    }

  val shouldAnimateGradient =
    settings != null &&
      ((settings.syntaxGradientEnabled && settings.syntaxGradientMoving) || settings.fontGradientMoving)

  val phaseState: State<Float> =
    if (shouldAnimateGradient) {
      val speed = (settings?.gradientSpeed ?: 1f).coerceIn(0.2f, 3f)
      val durationMs = (5500f / speed).roundToInt().coerceIn(1400, 20000)
      val transition = rememberInfiniteTransition(label = "blox_gradient_motion")
      transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
          ),
        label = "gradient_phase",
      )
    } else {
      remember { mutableFloatStateOf(0f) }
    }

  val visuals =
    remember(
      settings?.syntaxGradientEnabled,
      settings?.syntaxGradientMoving,
      settings?.fontGradientMoving,
      settings?.gradientPreset,
      settings?.panelOpacity,
      settings?.panelShadowDp,
      settings?.backgroundMode,
    ) {
      if (settings != null) {
        BloxVisuals(
          syntaxGradientEnabled = settings.syntaxGradientEnabled,
          syntaxGradientMoving = settings.syntaxGradientMoving,
          fontGradientMoving = settings.fontGradientMoving,
          gradientColors = settings.gradientPreset.colors,
          panelOpacity = settings.panelOpacity,
          panelShadowDp = settings.panelShadowDp,
          hasBackdrop = settings.backgroundMode != BackgroundMode.SOLID,
        )
      } else {
        BloxVisuals()
      }
    }

  CompositionLocalProvider(
    LocalBloxPalette provides palette,
    LocalBloxVisuals provides visuals,
    LocalGradientPhaseState provides phaseState,
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
  }
}
