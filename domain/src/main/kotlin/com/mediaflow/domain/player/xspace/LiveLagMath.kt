package com.mediaflow.domain.player.xspace

import java.util.Locale

/**
 * Live-edge lag from playhead vs ingest clock. Negative means the listener is behind live.
 */
object LiveLagMath {
    fun lagMs(liveEdgeMs: Long, playheadMs: Long): Long = playheadMs - liveEdgeMs

    fun behindLive(lagMs: Long, thresholdMs: Long = 500L): Boolean = lagMs <= -thresholdMs

    fun format(lagMs: Long): String {
        val behind = (-lagMs).coerceAtLeast(0L)
        val totalSec = behind / 1_000L
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return if (hours > 0) {
            String.format(Locale.US, "-%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "-%02d:%02d", minutes, seconds)
        }
    }
}
