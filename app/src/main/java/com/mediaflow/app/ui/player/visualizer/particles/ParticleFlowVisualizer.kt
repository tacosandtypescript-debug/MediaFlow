package com.mediaflow.app.ui.player.visualizer.particles

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
import com.mediaflow.app.ui.player.visualizer.audio.AudioReactiveState
import com.mediaflow.app.ui.player.visualizer.balls.BallPhysics
import com.mediaflow.app.ui.player.visualizer.settings.VisualizerSettings

@Composable
fun ParticleFlowVisualizer(
    signal: AudioReactiveState,
    colors: List<Color>,
    settings: VisualizerSettings,
    modifier: Modifier = Modifier,
) {
    var balls by remember { mutableStateOf(BallPhysics.seed(settings.particleCount(), 400f, 800f)) }
    var w by remember { mutableStateOf(400f) }
    var h by remember { mutableStateOf(800f) }
    LaunchedEffect(settings.enabled, signal) {
        var last = 0L
        while (settings.enabled) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0.008f, 0.033f)
                last = now
                balls = BallPhysics.step(balls, dt, w, h, signal, settings.intensity * 0.45f, settings.motion)
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        w = size.width
        h = size.height
        balls.forEach { b ->
            val c = colors[b.colorIndex % colors.size]
            drawCircle(c.copy(alpha = 0.55f + signal.energy * 0.3f), b.radius * 0.45f, Offset(b.x, b.y))
        }
    }
}
