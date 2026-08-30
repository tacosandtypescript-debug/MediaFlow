package com.mediaflow.app.ui.player.visualizer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mediaflow.app.ui.player.background.DynamicPlayerBackground
import com.mediaflow.app.ui.player.palette.PlayerColorPalette
import com.mediaflow.app.ui.player.visualizer.audio.AudioReactiveMapper
import com.mediaflow.app.ui.player.visualizer.audio.AudioReactiveState
import com.mediaflow.app.ui.player.visualizer.audio.OutputMixAnalyzer
import com.mediaflow.app.ui.player.visualizer.aurora.AuroraVisualizer
import com.mediaflow.app.ui.player.visualizer.balls.BallsVisualizer
import com.mediaflow.app.ui.player.visualizer.particles.ParticleFlowVisualizer
import com.mediaflow.app.ui.player.visualizer.rings.PulseRingsVisualizer
import com.mediaflow.app.ui.player.visualizer.settings.VisualizerSettings
import com.mediaflow.app.ui.player.visualizer.settings.VisualizerStyle
import com.mediaflow.app.ui.player.visualizer.spectrum.SpectrumGlowVisualizer
import com.mediaflow.app.ui.player.visualizer.waves.FluidWavesVisualizer

@Composable
fun VisualizerLayer(
    settings: VisualizerSettings,
    palette: PlayerColorPalette,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val analyzer = remember { OutputMixAnalyzer() }
    val analyze = AudioReactiveMapper.shouldAnalyze(settings.enabled) &&
        (isPlaying || !settings.reduceOnPause)
    LaunchedEffect(analyze, isPlaying, settings.enabled) {
        analyzer.setEnabled(settings.enabled && analyze, isPlaying)
    }
    DisposableEffect(Unit) { onDispose { analyzer.release() } }
    val signal by analyzer.state.collectAsState()
    val colors = if (settings.useCoverColors) palette.colors() else PlayerColorPalette.Fallback.colors()

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        DynamicPlayerBackground(palette = palette, modifier = Modifier.fillMaxSize())
        if (settings.enabled) {
            val effective = if (!isPlaying && settings.reduceOnPause) {
                signal.copy(beat = false, amplitude = signal.amplitude * 0.15f, energy = signal.energy * 0.15f)
            } else signal
            ReactiveStyle(
                style = settings.style,
                signal = effective,
                colors = colors,
                settings = settings,
            )
        }
    }
}

@Composable
private fun ReactiveStyle(
    style: VisualizerStyle,
    signal: AudioReactiveState,
    colors: List<androidx.compose.ui.graphics.Color>,
    settings: VisualizerSettings,
) {
    when (style) {
        VisualizerStyle.BALLS -> BallsVisualizer(signal, colors, settings)
        VisualizerStyle.AURORA -> AuroraVisualizer(signal, colors, settings)
        VisualizerStyle.WAVES -> FluidWavesVisualizer(signal, colors, settings)
        VisualizerStyle.RINGS -> PulseRingsVisualizer(signal, colors, settings)
        VisualizerStyle.PARTICLES -> ParticleFlowVisualizer(signal, colors, settings)
        VisualizerStyle.SPECTRUM -> SpectrumGlowVisualizer(signal, colors, settings)
    }
}
