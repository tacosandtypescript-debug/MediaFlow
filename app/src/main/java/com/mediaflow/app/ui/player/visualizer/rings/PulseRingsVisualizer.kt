package com.mediaflow.app.ui.player.visualizer.rings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mediaflow.app.ui.player.visualizer.audio.AudioReactiveState
import com.mediaflow.app.ui.player.visualizer.settings.VisualizerSettings

private data class Ring(var r: Float, var a: Float, val color: Color)

@Composable
fun PulseRingsVisualizer(
    signal: AudioReactiveState,
    colors: List<Color>,
    settings: VisualizerSettings,
    modifier: Modifier = Modifier,
) {
    var rings by remember { mutableStateOf(listOf<Ring>()) }
    LaunchedEffect(signal.beat, settings.enabled) {
        if (signal.beat && settings.enabled) {
            rings = (rings + Ring(12f, 0.7f * settings.intensity, colors.first())).takeLast(8)
        }
    }
    LaunchedEffect(settings.enabled) {
        while (settings.enabled) {
            withFrameNanos {
                rings = rings.map { it.copy(r = it.r + 90f * 0.016f, a = it.a - 0.012f) }
                    .filter { it.a > 0.02f }
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val c = Offset(size.width / 2f, size.height * 0.42f)
        rings.forEach { ring ->
            drawCircle(ring.color.copy(alpha = ring.a), ring.r, c, style = Stroke(width = 4f))
        }
    }
}
