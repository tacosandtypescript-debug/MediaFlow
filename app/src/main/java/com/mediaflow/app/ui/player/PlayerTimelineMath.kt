package com.mediaflow.app.ui.player

/**
 * Pure seek-bar math shared by [com.mediaflow.app.ui.player.components.PlayerTimeline] and tests.
 */
object PlayerTimelineMath {
    fun positionForFraction(fraction: Float, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val clamped = fraction.toDouble().coerceIn(0.0, 1.0)
        return (clamped * durationMs.toDouble())
            .toLong()
            .coerceIn(0L, durationMs)
    }

    fun fractionForPosition(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun displayedPositionMs(
        isScrubbing: Boolean,
        scrubPositionMs: Long,
        enginePositionMs: Long,
    ): Long = if (isScrubbing) scrubPositionMs else enginePositionMs
}
