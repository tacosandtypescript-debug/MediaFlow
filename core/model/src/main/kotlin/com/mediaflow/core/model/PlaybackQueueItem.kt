package com.mediaflow.core.model

/**
 * Model representing an item in the active playback queue.
 */
data class PlaybackQueueItem(
    val mediaUri: String,
    val title: String,
    val artistOrHost: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val isLive: Boolean = false,
)
