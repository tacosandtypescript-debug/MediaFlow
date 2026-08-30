package com.mediaflow.app.ui.player.visualizer.balls

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mediaflow.app.ui.player.visualizer.audio.AudioReactiveState
import com.mediaflow.app.ui.player.visualizer.settings.VisualizerSettings

@Composable
fun BallsVisualizer(
    signal: AudioReactiveState,
    colors: List<Color>,
    settings: VisualizerSettings,
    modifier: Modifier = Modifier,
) {
    var balls by remember { mutableStateOf(emptyList<BallParticle>()) }
    var sizeW by remember { mutableStateOf(0f) }
    var sizeH by remember { mutableStateOf(0f) }
    LaunchedEffect(settings.enabled, settings.particleCount(), signal) {
        if (!settings.enabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0.008f, 0.033f)
                last = now
                if (sizeW > 1f) {
                    if (balls.size != settings.particleCount()) {
                        balls = BallPhysics.seed(settings.particleCount(), sizeW, sizeH)
                    } else {
                        balls = BallPhysics.step(
                            balls, dt, sizeW, sizeH, signal, settings.intensity, settings.motion,
                        )
                    }
                }
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        sizeW = size.width
        sizeH = size.height
        balls.forEach { ball ->
            val color = colors[ball.colorIndex % colors.size]
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.05f)),
                    center = Offset(ball.x, ball.y),
                    radius = ball.radius * 2.2f,
                ),
                radius = ball.radius * 2.2f,
                center = Offset(ball.x, ball.y),
            )
            drawCircle(color.copy(alpha = 0.92f), ball.radius, Offset(ball.x, ball.y))
        }
    }
}
