package com.mediaflow.domain.player

import com.mediaflow.core.model.PlaybackStatus

/**
 * Policy defining when content is considered completed and how progress positions are resolved.
 */
class CompletionPolicy(
    val completionThreshold: Float = DEFAULT_COMPLETION_THRESHOLD,
    val resumeSafetyMarginMs: Long = DEFAULT_RESUME_MARGIN_MS,
) {
    /**
     * Determines whether the given playback position constitutes a completed playback.
     */
    fun isCompleted(positionMs: Long, durationMs: Long, isEof: Boolean = false): Boolean {
        if (isEof) return true
        if (durationMs <= 0L) return false
        val percentage = positionMs.toFloat() / durationMs.toFloat()
        return percentage >= completionThreshold
    }

    /**
     * Determines the appropriate [PlaybackStatus] given position, duration, and EOF state.
     */
    fun determineStatus(positionMs: Long, durationMs: Long, isEof: Boolean = false): PlaybackStatus {
        if (isCompleted(positionMs, durationMs, isEof)) {
            return PlaybackStatus.COMPLETED
        }
        if (positionMs > 0L) {
            return PlaybackStatus.IN_PROGRESS
        }
        return PlaybackStatus.NEW
    }

    /**
     * Computes the initial starting position when reopening content.
     * - If [status] is COMPLETED, starts from 0L.
     * - If [savedPositionMs] is within the safety margin (< 2s), starts from 0L to prevent micro-jumps.
     * - Otherwise returns [savedPositionMs].
     */
    fun computeResumePosition(
        savedPositionMs: Long,
        totalDurationMs: Long,
        status: PlaybackStatus,
    ): Long {
        if (status == PlaybackStatus.COMPLETED) {
            return 0L
        }
        if (savedPositionMs <= resumeSafetyMarginMs) {
            return 0L
        }
        if (totalDurationMs > 0L && savedPositionMs >= totalDurationMs) {
            return 0L
        }
        return savedPositionMs
    }

    companion object {
        /** Default threshold (95%) to mark media as completed. */
        const val DEFAULT_COMPLETION_THRESHOLD = 0.95f

        /** Small margin (2 seconds) below which playback starts from the beginning. */
        const val DEFAULT_RESUME_MARGIN_MS = 2_000L
    }
}
