package com.example.ui

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AppSettings
import com.example.ui.theme.BackgroundMode
import com.example.ui.theme.LocalBloxPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class StarNode(
  val xFrac: Float,
  val yFrac: Float,
  val radius: Float,
  val speedMul: Float,
  val phase: Float,
  val twinkleFreq: Float,
  val tintIndex: Int,
)

@Composable
fun GalaxyBackdropLayer(
  settings: AppSettings,
  modifier: Modifier = Modifier,
) {
  val palette = LocalBloxPalette.current
  val blurMod =
    if (settings.backgroundBlurDp > 0.5f) {
      Modifier.blur(settings.backgroundBlurDp.dp)
    } else {
      Modifier
    }
  val liveBlurMod =
    if (settings.backgroundBlurDp > 0.5f) {
      Modifier.blur(settings.backgroundBlurDp.coerceAtMost(12f).dp)
    } else {
      Modifier
    }

  val customBitmap by
    produceState<ImageBitmap?>(initialValue = null, settings.customBackgroundPath, settings.backgroundMode) {
      value =
        if (settings.backgroundMode == BackgroundMode.CUSTOM_IMAGE && settings.customBackgroundPath.isNotBlank()) {
          withContext(Dispatchers.IO) {
            runCatching {
              val f = File(settings.customBackgroundPath)
              if (!f.exists()) return@runCatching null
              val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
              BitmapFactory.decodeFile(f.absolutePath, opts)
              var sample = 1
              while (opts.outWidth / sample > 1440 || opts.outHeight / sample > 1440) {
                sample *= 2
              }
              val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
              BitmapFactory.decodeFile(f.absolutePath, decodeOpts)?.asImageBitmap()
            }.getOrNull()
          }
        } else {
          null
        }
    }

  val videoExists =
    remember(settings.customVideoPath, settings.backgroundMode) {
      settings.backgroundMode == BackgroundMode.CUSTOM_VIDEO &&
        settings.customVideoPath.isNotBlank() &&
        File(settings.customVideoPath).exists()
    }

  Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    when {
      videoExists -> {
        LoopingVideoBackdrop(
          videoPath = settings.customVideoPath,
          muted = settings.backgroundVideoMuted,
          modifier = Modifier.fillMaxSize().then(liveBlurMod),
        )
      }

      settings.backgroundMode == BackgroundMode.CUSTOM_IMAGE && customBitmap != null -> {
        Image(
          bitmap = customBitmap!!,
          contentDescription = "Custom background",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().then(blurMod),
        )
      }

      settings.backgroundMode == BackgroundMode.SOLID -> {
        // Minimal clean background
      }

      else -> {
        AnimatedGalaxyCanvas(
          darkMode = palette.isDark,
          isMonochrome = palette.isMonochrome,
          accent = palette.accent,
          speed = settings.galaxySpeed,
          density = settings.galaxyStarDensity,
          modifier = Modifier.fillMaxSize().then(liveBlurMod),
        )
      }
    }

    if (settings.backgroundMode != BackgroundMode.SOLID && settings.backgroundDimAlpha > 0.01f) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = settings.backgroundDimAlpha.coerceIn(0f, 0.9f))),
      )
    }
  }
}

@Composable
private fun LoopingVideoBackdrop(
  videoPath: String,
  muted: Boolean,
  modifier: Modifier = Modifier,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  var mediaPlayer by remember(videoPath) { mutableStateOf<MediaPlayer?>(null) }
  var renderSurface by remember(videoPath) { mutableStateOf<Surface?>(null) }

  LaunchedEffect(mediaPlayer, muted) {
    val vol = if (muted) 0f else 0.65f
    runCatching { mediaPlayer?.setVolume(vol, vol) }
  }

  DisposableEffect(lifecycleOwner, videoPath) {
    val observer =
      LifecycleEventObserver { _, event ->
        val mp = mediaPlayer ?: return@LifecycleEventObserver
        when (event) {
          Lifecycle.Event.ON_PAUSE -> runCatching { if (mp.isPlaying) mp.pause() }
          Lifecycle.Event.ON_RESUME -> runCatching { if (!mp.isPlaying) mp.start() }
          else -> Unit
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      runCatching {
        mediaPlayer?.stop()
        mediaPlayer?.release()
      }
      mediaPlayer = null
      runCatching { renderSurface?.release() }
      renderSurface = null
    }
  }

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      TextureView(ctx).apply {
        val textureView = this
        var videoW = 0
        var videoH = 0

        fun applyCenterCrop(viewW: Int, viewH: Int) {
          if (viewW <= 0 || viewH <= 0 || videoW <= 0 || videoH <= 0) return
          val scaleX = viewW.toFloat() / videoW.toFloat()
          val scaleY = viewH.toFloat() / videoH.toFloat()
          val maxScale = maxOf(scaleX, scaleY)
          val normX = (videoW * maxScale) / viewW.toFloat()
          val normY = (videoH * maxScale) / viewH.toFloat()
          val matrix = Matrix()
          matrix.setScale(normX, normY, viewW / 2f, viewH / 2f)
          textureView.setTransform(matrix)
        }

        surfaceTextureListener =
          object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
              val s = Surface(surfaceTexture)
              renderSurface = s
              val mp =
                MediaPlayer().apply {
                  runCatching {
                    setDataSource(videoPath)
                    setSurface(s)
                    isLooping = true
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    setOnVideoSizeChangedListener { _, vw, vh ->
                      videoW = vw
                      videoH = vh
                      textureView.post { applyCenterCrop(textureView.width, textureView.height) }
                    }
                    setOnPreparedListener { player ->
                      val vol = if (muted) 0f else 0.65f
                      player.setVolume(vol, vol)
                      videoW = player.videoWidth
                      videoH = player.videoHeight
                      textureView.post { applyCenterCrop(textureView.width, textureView.height) }
                      player.start()
                    }
                    setOnErrorListener { _, _, _ -> true }
                    prepareAsync()
                  }
                }
              mediaPlayer = mp
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
              applyCenterCrop(width, height)
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
              runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
              }
              mediaPlayer = null
              runCatching { renderSurface?.release() }
              renderSurface = null
              return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
          }
      }
    },
  )
}

@Composable
private fun AnimatedGalaxyCanvas(
  darkMode: Boolean,
  isMonochrome: Boolean,
  accent: Color,
  speed: Float,
  density: Float,
  modifier: Modifier = Modifier,
) {
  val starCount = (72 * density.coerceIn(0.3f, 2f)).roundToInt().coerceIn(22, 120)
  val stars =
    remember(starCount) {
      val rng = Random(20260501)
      List(starCount) {
        StarNode(
          xFrac = rng.nextFloat(),
          yFrac = rng.nextFloat(),
          radius = 0.8f + rng.nextFloat() * 2.1f,
          speedMul = 0.25f + rng.nextFloat() * 1.15f,
          phase = rng.nextFloat() * (2f * PI.toFloat()),
          twinkleFreq = 1f + rng.nextFloat() * 2.5f,
          tintIndex = rng.nextInt(4),
        )
      }
    }

  val baseBg = remember(darkMode) { if (darkMode) Color(0xFF040407) else Color(0xFFECEEF3) }
  val nebulaColors1 =
    remember(darkMode, isMonochrome, accent) {
      val primary =
        when {
          isMonochrome && darkMode -> Color(0xFF3F3F46)
          isMonochrome -> Color(0xFF71717A)
          else -> accent
        }
      listOf(primary.copy(alpha = if (darkMode) 0.22f else 0.16f), Color.Transparent)
    }
  val nebulaColors2 =
    remember(darkMode, isMonochrome) {
      val secondary =
        when {
          isMonochrome && darkMode -> Color(0xFF27272A)
          isMonochrome -> Color(0xFFA1A1AA)
          else -> Color(0xFF7C3AED)
        }
      listOf(secondary.copy(alpha = if (darkMode) 0.20f else 0.14f), Color.Transparent)
    }
  val nebulaColors3 =
    remember(darkMode, isMonochrome) {
      val core =
        when {
          isMonochrome && darkMode -> Color(0xFF52525B)
          isMonochrome -> Color(0xFFD4D4D8)
          else -> Color(0xFF0EA5E9)
        }
      listOf(core.copy(alpha = if (darkMode) 0.15f else 0.10f), Color.Transparent)
    }
  val paletteTints =
    remember(darkMode, isMonochrome) {
      val starBaseColor = if (darkMode) Color.White else Color(0xFF18181B)
      if (isMonochrome) {
        listOf(
          starBaseColor,
          starBaseColor.copy(alpha = 0.85f),
          if (darkMode) Color(0xFFD4D4D8) else Color(0xFF3F3F46),
          if (darkMode) Color(0xFFA1A1AA) else Color(0xFF52525B),
        )
      } else {
        listOf(
          starBaseColor,
          Color(0xFFBAE6FD),
          Color(0xFFDDD6FE),
          Color(0xFFFDE68A),
        )
      }
    }

  val durationMs = (24000f / speed.coerceIn(0.15f, 3.0f)).roundToInt().coerceIn(6000, 90000)
  val transition = rememberInfiniteTransition(label = "galaxy_motion")
  val orbitPhaseState =
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = durationMs, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "galaxy_orbit",
    )

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@Canvas

    drawRect(color = baseBg)

    val orbitPhase = orbitPhaseState.value
    val angle = orbitPhase * 2f * PI.toFloat()

    // Drifting cosmic nebula clouds
    val c1 = Offset(w * (0.28f + 0.16f * cos(angle)), h * (0.30f + 0.14f * sin(angle)))
    val c2 = Offset(w * (0.72f + 0.18f * sin(angle * 0.8f)), h * (0.68f + 0.14f * cos(angle * 0.8f)))
    val c3 = Offset(w * (0.50f + 0.12f * cos(-angle)), h * (0.48f + 0.18f * sin(-angle)))

    drawCircle(
      brush = Brush.radialGradient(colors = nebulaColors1, center = c1, radius = w * 0.65f),
      center = c1,
      radius = w * 0.65f,
    )
    drawCircle(
      brush = Brush.radialGradient(colors = nebulaColors2, center = c2, radius = w * 0.60f),
      center = c2,
      radius = w * 0.60f,
    )
    drawCircle(
      brush = Brush.radialGradient(colors = nebulaColors3, center = c3, radius = w * 0.50f),
      center = c3,
      radius = w * 0.50f,
    )

    // Starfield with parallax drift + sinusoidal twinkle
    for (star in stars) {
      val sx = ((star.xFrac + orbitPhase * star.speedMul) % 1f) * w
      val sy = ((star.yFrac + orbitPhase * star.speedMul * 0.35f) % 1f) * h
      val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(angle * star.twinkleFreq + star.phase))
      val col = paletteTints[star.tintIndex].copy(alpha = (0.28f + 0.62f * twinkle).coerceIn(0.1f, 0.95f))
      drawCircle(
        color = col,
        radius = star.radius * (0.8f + 0.25f * twinkle),
        center = Offset(sx, sy),
      )
      if (star.radius > 2.3f && twinkle > 0.72f) {
        val arm = star.radius * 2.4f
        drawLine(
          color = col.copy(alpha = col.alpha * 0.55f),
          start = Offset(sx - arm, sy),
          end = Offset(sx + arm, sy),
          strokeWidth = 0.9f,
        )
        drawLine(
          color = col.copy(alpha = col.alpha * 0.55f),
          start = Offset(sx, sy - arm),
          end = Offset(sx, sy + arm),
          strokeWidth = 0.9f,
        )
      }
    }
  }
}
