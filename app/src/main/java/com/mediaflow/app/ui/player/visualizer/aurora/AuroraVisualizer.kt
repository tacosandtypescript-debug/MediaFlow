package com.mediaflow.app.ui.player.visualizer.aurora

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mediaflow.app.ui.player.visualizer.audio.AudioReactiveState
import com.mediaflow.app.ui.player.visualizer.settings.VisualizerSettings
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AuroraVisualizer(
    signal: AudioReactiveState,
    colors: List<Color>,
    settings: VisualizerSettings,
    modifier: Modifier = Modifier,
) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(settings.enabled) {
        var last = 0L
        while (settings.enabled) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = (now - last) / 1_000_000_000f
                last = now
                t += dt * (0.15f + settings.motion * 0.35f + signal.energy * 0.4f)
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val pulse = 0.35f + signal.bass * 0.45f * settings.intensity
        colors.take(3).forEachIndexed { i, c ->
            val cx = size.width * (0.25f + 0.25f * i + 0.08f * cos((t + i).toDouble()).toFloat())
            val cy = size.height * (0.3f + 0.12f * sin((t * 0.7f + i).toDouble()).toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = 0.4f * pulse), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.minDimension * (0.45f + signal.energy * 0.2f),
                ),
                radius = size.minDimension * (0.45f + signal.energy * 0.2f),
                center = Offset(cx, cy),
            )
        }
    }
}
