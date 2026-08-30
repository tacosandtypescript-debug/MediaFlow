package com.mediaflow.app.ui.player.visualizer.spectrum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.mediaflow.app.ui.player.visualizer.audio.AudioReactiveState
import com.mediaflow.app.ui.player.visualizer.settings.VisualizerSettings

@Composable
fun SpectrumGlowVisualizer(
    signal: AudioReactiveState,
    colors: List<Color>,
    settings: VisualizerSettings,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxSize()) {
        val bands = floatArrayOf(signal.bass, signal.mids, signal.highs, signal.amplitude, signal.energy)
        val baseY = size.height * 0.82f
        val gap = size.width / (bands.size + 1)
        bands.forEachIndexed { i, v ->
            val h = (40f + v * 160f * settings.intensity)
            val x = gap * (i + 1)
            val c = colors[i % colors.size]
            drawLine(
                c.copy(alpha = 0.55f),
                Offset(x, baseY),
                Offset(x, baseY - h),
                strokeWidth = 10f,
                cap = StrokeCap.Round,
            )
        }
    }
}
