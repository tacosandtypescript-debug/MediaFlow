package com.mediaflow.app.ui.player

import com.mediaflow.core.model.PlaybackQueueItem
import com.mediaflow.core.model.Playlist
import com.mediaflow.core.model.XSpace
import com.mediaflow.domain.live.LiveSpaceEndState
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlayerServiceState

/**
 * Ephemeral event for animated seek feedback (±10s).
 */
sealed class SeekFeedbackEvent {
    data class Rewind(val seconds: Int = 10, val timestamp: Long = System.currentTimeMillis()) : SeekFeedbackEvent()
    data class Forward(val seconds: Int = 10, val timestamp: Long = System.currentTimeMillis()) : SeekFeedbackEvent()
}

/**
 * Immutable UI state model for the native media player.
 */
data class PlayerUiState(
    val mediaUri: String = "",
    val title: String = "",
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val subtitle: String? = null,
    val fileDurationMs: Long = 0L,
    val serviceState: PlayerServiceState = PlayerServiceState(),
    val isControlsVisible: Boolean = true,
    val isFullscreen: Boolean = false,
    val spaceMetadata: XSpace? = null,
    val seekFeedback: SeekFeedbackEvent? = null,
    val isScrubbing: Boolean = false,
    val scrubPositionMs: Long = 0L,
    val liveEndState: LiveSpaceEndState = LiveSpaceEndState.ActiveLive,
    val isAutoDownloadEnabled: Boolean = false,
    val isFavorite: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
) {
    val isPlaying: Boolean
        get() = serviceState.isPlaying

    val isPaused: Boolean
        get() = serviceState.isPaused

    val isEnded: Boolean
        get() = serviceState.isEnded

    val isPreparing: Boolean
        get() = serviceState.playbackState == EnginePlaybackState.PREPARING

    val isBuffering: Boolean
        get() = serviceState.playbackState == EnginePlaybackState.PREPARING

    val isError: Boolean
        get() = serviceState.isError

    val errorMessage: String?
        get() = serviceState.errorMessage

    val durationMs: Long
        get() = serviceState.durationMs.takeIf { it > 0L } ?: fileDurationMs

    val currentPositionMs: Long
        get() = PlayerTimelineMath.displayedPositionMs(
            isScrubbing = isScrubbing,
            scrubPositionMs = scrubPositionMs,
            enginePositionMs = serviceState.currentPositionMs,
        )

    val speed: Float
        get() = serviceState.speed

    val volume: Int
        get() = serviceState.volume

    val isMuted: Boolean
        get() = serviceState.isMuted

    val isAudioOnly: Boolean
        get() = serviceState.isAudioOnly

    val isBroadcastLive: Boolean
        get() = spaceMetadata?.isLive == true && liveEndState is LiveSpaceEndState.ActiveLive

    val isLiveSession: Boolean
        get() = serviceState.isLive ||
            spaceMetadata?.isLive == true ||
            liveEndState !is LiveSpaceEndState.ActiveLive

    val isLive: Boolean
        get() = isBroadcastLive || (serviceState.isLive && liveEndState is LiveSpaceEndState.ActiveLive)

    val queue: List<PlaybackQueueItem>
        get() = serviceState.queue

    val queueIndex: Int
        get() = serviceState.queueIndex

    val hasNext: Boolean
        get() = serviceState.hasNext

    val hasPrevious: Boolean
        get() = serviceState.hasPrevious

    val playbackContext: String?
        get() = serviceState.playbackContext
}
