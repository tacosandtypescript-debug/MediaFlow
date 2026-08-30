package com.mediaflow.app.ui.player.visualizer.audio

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Captures the mixed output session. Does not start unless [enabled] is true.
 */
class OutputMixAnalyzer {
    private val _state = MutableStateFlow(AudioReactiveState())
    val state: StateFlow<AudioReactiveState> = _state.asStateFlow()
    private var visualizer: Visualizer? = null
    private var previousEnergy = 0f

    fun setEnabled(enabled: Boolean, isPlaying: Boolean) {
        if (!AudioReactiveMapper.shouldAnalyze(enabled) || !isPlaying) {
            release()
            _state.value = AudioReactiveMapper.fromBands(0f, 0f, 0f, 0f, isPlaying = false, previousEnergy = 0f)
            previousEnergy = 0f
            return
        }
        if (visualizer != null) return
        runCatching {
            val viz = Visualizer(0)
            val range = Visualizer.getCaptureSizeRange()
            viz.captureSize = range[1].coerceAtMost(512)
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, sr: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, sr: Int) {
                        if (fft == null) return
                        val bands = AudioReactiveMapper.bandsFromFft(fft)
                        val rms = AudioReactiveMapper.rmsFromWave(fft)
                        val mapped = AudioReactiveMapper.fromBands(
                            bass = bands.first,
                            mids = bands.second,
                            highs = bands.third,
                            rms = rms,
                            isPlaying = true,
                            previousEnergy = previousEnergy,
                        )
                        previousEnergy = mapped.energy
                        _state.value = mapped
                    }
                },
                Visualizer.getMaxCaptureRate() / 4,
                false,
                true,
            )
            viz.enabled = true
            visualizer = viz
        }.onFailure {
            visualizer = null
        }
    }

    fun release() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
    }
}
