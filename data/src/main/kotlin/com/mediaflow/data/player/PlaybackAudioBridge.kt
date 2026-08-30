package com.mediaflow.data.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Unsigned 8-bit PCM (128 = silence) plus optional Visualizer-style FFT. */
data class PlaybackPcmFrame(
    val pcm: ByteArray = ByteArray(0),
    val fft: ByteArray = ByteArray(0),
    val bass: Float = 0f,
    val lowMids: Float = 0f,
    val mids: Float = 0f,
    val highs: Float = 0f,
    val rms: Float = 0f,
    val isPlaying: Boolean = false,
) {
    companion object {
        val Silent = PlaybackPcmFrame()
    }
}

/**
 * Cross-module tap: engine publishes playback PCM/bands; UI analyzer consumes them.
 * Visualizer OFF clears [enabled] so the engine must stop extra analysis.
 */
object PlaybackAudioBridge {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _frame = MutableStateFlow(PlaybackPcmFrame.Silent)
    val frame: StateFlow<PlaybackPcmFrame> = _frame.asStateFlow()

    fun setVisualizerEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) {
            _frame.value = PlaybackPcmFrame.Silent
        }
    }

    fun publish(frame: PlaybackPcmFrame) {
        if (!_enabled.value) return
        _frame.value = frame
    }

    fun reset() {
        _frame.value = PlaybackPcmFrame.Silent
    }
}
