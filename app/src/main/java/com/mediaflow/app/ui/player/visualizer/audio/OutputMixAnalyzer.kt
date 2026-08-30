package com.mediaflow.app.ui.player.visualizer.audio

import com.mediaflow.data.player.PlaybackAudioBridge
import com.mediaflow.data.player.PlaybackPcmFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Maps [PlaybackAudioBridge] player PCM / band frames off the UI thread.
 * Does not start unless the visualizer is enabled and playback is active.
 */
class OutputMixAnalyzer(
    private val frames: StateFlow<PlaybackPcmFrame> = PlaybackAudioBridge.frame,
) {
    private val _state = MutableStateFlow(AudioReactiveState())
    val state: StateFlow<AudioReactiveState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectJob: Job? = null
    private var previousEnergy = 0f

    @Volatile
    var isAnalyzing: Boolean = false
        private set

    enum class Transport { Play, Pause, Seek, Next, Previous }

    fun setEnabled(enabled: Boolean, isPlaying: Boolean) {
        val run = AudioReactiveMapper.shouldAnalyze(enabled, isPlaying)
        PlaybackAudioBridge.setVisualizerEnabled(run)
        if (!run) {
            stopAnalysis()
            return
        }
        if (isAnalyzing) return
        isAnalyzing = true
        collectJob = scope.launch {
            frames.collect { frame ->
                val mapped = withContext(Dispatchers.Default) {
                    ingest(frame)
                }
                _state.value = mapped
            }
        }
    }

    fun onTransport(event: Transport) {
        previousEnergy = 0f
        when (event) {
            Transport.Pause -> {
                isAnalyzing = false
                collectJob?.cancel()
                collectJob = null
                PlaybackAudioBridge.setVisualizerEnabled(false)
                _state.value = AudioReactiveMapper.fromBands(
                    0f, 0f, 0f, 0f, isPlaying = false, previousEnergy = 0f,
                )
            }
            Transport.Seek, Transport.Next, Transport.Previous -> {
                PlaybackAudioBridge.reset()
                _state.value = AudioReactiveMapper.fromBands(
                    0f, 0f, 0f, 0f, isPlaying = isAnalyzing, previousEnergy = 0f,
                )
            }
            Transport.Play -> { /* analysis continues if setEnabled(true, true) */ }
        }
    }

    fun ingest(frame: PlaybackPcmFrame): AudioReactiveState {
        if (!isAnalyzing && !frame.isPlaying) {
            previousEnergy = 0f
            return AudioReactiveState(isPlaying = false)
        }
        val mapped = AudioReactiveMapper.fromPlaybackFrame(
            pcm = frame.pcm,
            fft = frame.fft,
            bass = frame.bass,
            lowMids = frame.lowMids,
            mids = frame.mids,
            highs = frame.highs,
            rms = frame.rms,
            isPlaying = frame.isPlaying,
            previousEnergy = previousEnergy,
        )
        previousEnergy = mapped.energy
        return mapped
    }

    fun ingestPcm(pcm: ByteArray, isPlaying: Boolean): AudioReactiveState {
        val mapped = AudioReactiveMapper.fromPcm(pcm, isPlaying, previousEnergy)
        previousEnergy = mapped.energy
        _state.value = mapped
        return mapped
    }

    fun ingestFft(fft: ByteArray, rms: Float, isPlaying: Boolean): AudioReactiveState {
        val bands = AudioReactiveMapper.bandsFromFft(fft)
        val mapped = AudioReactiveMapper.fromBands(
            bands.first, bands.second, bands.third, rms, isPlaying, previousEnergy,
        )
        previousEnergy = mapped.energy
        _state.value = mapped
        return mapped
    }

    fun release() {
        stopAnalysis()
        scope.cancel()
    }

    private fun stopAnalysis() {
        isAnalyzing = false
        collectJob?.cancel()
        collectJob = null
        previousEnergy = 0f
        _state.value = AudioReactiveMapper.fromBands(
            0f, 0f, 0f, 0f, isPlaying = false, previousEnergy = 0f,
        )
        PlaybackAudioBridge.reset()
    }
}
