package com.mediaflow.domain.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** State representing the low-level playback engine status. */
enum class EnginePlaybackState {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

/** Immutable state snapshot of the active playback engine. */
data class EngineState(
    val mediaSource: String? = null,
    val playbackState: EnginePlaybackState = EnginePlaybackState.IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val volume: Int = 100,
    val isMuted: Boolean = false,
    val isAudioOnly: Boolean = false,
    val isVideoAvailable: Boolean = false,
    val errorMessage: String? = null,
) {
    val isPlaying: Boolean
        get() = playbackState == EnginePlaybackState.PLAYING

    val isPaused: Boolean
        get() = playbackState == EnginePlaybackState.PAUSED

    val isEnded: Boolean
        get() = playbackState == EnginePlaybackState.ENDED

    val isError: Boolean
        get() = playbackState == EnginePlaybackState.ERROR

    val progressFraction: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Interface abstracting multimedia playback engines (such as libmpv or test mocks).
 */
interface PlaybackEngine {
    /** Observable state flow of the engine. */
    val state: StateFlow<EngineState>

    /** Centralized flow of playback events. */
    val events: Flow<PlaybackEvent>

    /** Loads and prepares a media source at the requested start position. */
    fun load(mediaSource: String, startPositionMs: Long = 0L, autoPlay: Boolean = true)

    /** Starts or resumes playback. */
    fun play()

    /** Pauses active playback. */
    fun pause()

    /** Stops playback and unloads the current media. */
    fun stop()

    /** Seeks to a specific millisecond timestamp. */
    fun seekTo(positionMs: Long)

    /** Sets the playback speed (e.g. 0.5f, 1.0f, 1.5f, 2.0f). */
    fun setSpeed(speed: Float)

    /** Sets playback volume (0..100). */
    fun setVolume(volume: Int)

    /** Mutes or unmutes audio. */
    fun setMute(muted: Boolean)

    /** Attaches a render surface (e.g. android.view.Surface) for video output. */
    fun attachSurface(surface: Any?)

    /** Detaches the current render surface. */
    fun detachSurface()

    /** Releases all engine and native resources. */
    fun release()
}
