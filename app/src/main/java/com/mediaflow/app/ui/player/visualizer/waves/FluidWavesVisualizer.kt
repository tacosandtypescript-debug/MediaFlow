package com.mediaflow.app.ui.player.visualizer.waves

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mediaflow.app.ui.player.visualizer.audio.AudioReactiveState
import com.mediaflow.app.ui.player.visualizer.settings.VisualizerSettings
import kotlin.math.sin

@Composable
fun FluidWavesVisualizer(
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
                t += ((now - last) / 1_000_000_000f) * (0.8f + settings.motion)
                last = now
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val amp = 18f + signal.bass * 70f * settings.intensity
        colors.take(3).forEachIndexed { i, c ->
            val path = Path()
            val y0 = size.height * (0.55f + i * 0.08f)
            path.moveTo(0f, y0)
            var x = 0f
            while (x <= size.width) {
                val y = y0 + sin((x * 0.012f + t + i).toDouble()).toFloat() * amp *
                    (0.5f + signal.mids * 0.5f)
                path.lineTo(x, y)
                x += 12f
            }
            drawPath(path, c.copy(alpha = 0.45f), style = Stroke(width = 3f + signal.highs * 4f))
        }
    }
}
