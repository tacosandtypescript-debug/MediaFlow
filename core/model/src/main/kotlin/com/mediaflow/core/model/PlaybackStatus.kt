package com.mediaflow.core.model

/**
 * Visual and operational lifecycle status for played media content.
 */
enum class PlaybackStatus {
    /** Content has never been played. */
    NEW,

    /** Playback was initiated and is currently in progress. */
    IN_PROGRESS,

    /** Playback finished or reached the completion threshold (>= 95%). */
    COMPLETED,
}
