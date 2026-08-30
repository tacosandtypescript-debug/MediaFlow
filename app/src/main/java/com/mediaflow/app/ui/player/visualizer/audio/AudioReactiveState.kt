package com.mediaflow.app.ui.player.visualizer.audio

/** Shared playback-derived bands. Values in 0..1 except flags. */
data class AudioReactiveState(
    val amplitude: Float = 0f,
    val bass: Float = 0f,
    val mids: Float = 0f,
    val highs: Float = 0f,
    val beat: Boolean = false,
    val energy: Float = 0f,
    val peak: Float = 0f,
    val isPlaying: Boolean = false,
)
