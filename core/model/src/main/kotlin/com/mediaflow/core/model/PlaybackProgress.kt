package com.mediaflow.core.model

/**
 * Persistent playback state for a single media item.
 */
data class PlaybackProgress(
    /** Unique stable identifier for the media (e.g. MediaStore URI string, path, or download id). */
    val mediaId: String,

    /** File path, content URI, or source identifier. */
    val filePath: String,

    /** Total duration in milliseconds. 0L if unknown. */
    val totalDurationMs: Long = 0L,

    /** Current playback position in milliseconds. */
    val currentPositionMs: Long = 0L,

    /** Playback progress percentage between 0.0f and 1.0f. */
    val playbackPercentage: Float = if (totalDurationMs > 0L) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f,

    /** Epoch timestamp in milliseconds of last playback activity. */
    val lastPlayedAt: Long = 0L,

    /** Playback lifecycle status (NEW, IN_PROGRESS, COMPLETED). */
    val status: PlaybackStatus = PlaybackStatus.NEW,

    /** Total number of times this item has been opened/played. */
    val playCount: Int = 0,
) {
    /** True if status is [PlaybackStatus.COMPLETED]. */
    val isCompleted: Boolean
        get() = status == PlaybackStatus.COMPLETED

    /** Formatted percentage rounded to integer (0..100). */
    val percentageInt: Int
        get() = (playbackPercentage * 100f).toInt().coerceIn(0, 100)

    companion object {
        /** Creates a brand new unplayed progress record for an item. */
        fun new(mediaId: String, filePath: String): PlaybackProgress = PlaybackProgress(
            mediaId = mediaId,
            filePath = filePath,
            status = PlaybackStatus.NEW,
            lastPlayedAt = System.currentTimeMillis(),
        )
    }
}
