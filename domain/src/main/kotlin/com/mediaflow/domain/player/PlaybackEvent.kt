package com.mediaflow.domain.player

/**
 * Centralized playback events emitted by the player lifecycle and engine.
 */
sealed interface PlaybackEvent {
    /** Media source was opened/loaded by the engine. */
    data class MediaOpened(
        val mediaId: String,
        val durationMs: Long,
        val isAudioOnly: Boolean,
    ) : PlaybackEvent

    /** Playback transitioned to playing state. */
    data class PlaybackStarted(val mediaId: String, val positionMs: Long) : PlaybackEvent

    /** Playback transitioned to paused state. */
    data class PlaybackPaused(val mediaId: String, val positionMs: Long) : PlaybackEvent

    /** Playback position updated during playback or seek. */
    data class PositionChanged(
        val mediaId: String,
        val positionMs: Long,
        val durationMs: Long,
    ) : PlaybackEvent

    /** Playback completed naturally or reached EOF. */
    data class PlaybackFinished(val mediaId: String, val durationMs: Long) : PlaybackEvent

    /** Media playback session closed/released. */
    data class MediaClosed(val mediaId: String, val lastPositionMs: Long) : PlaybackEvent

    /** Playback encountered a recoverable or fatal error. */
    data class PlaybackError(val mediaId: String, val message: String, val isFatal: Boolean = false) : PlaybackEvent
}
