package com.mediaflow.data.player

/**
 * Ensures a single [com.mediaflow.domain.player.PlaybackEvent.PlaybackFinished]
 * is emitted per loaded media (eof-reached and END_FILE can both fire).
 */
class PlaybackFinishedGate {
    @Volatile
    private var emitted: Boolean = false

    fun reset() {
        emitted = false
    }

    @Synchronized
    fun tryMarkEmitted(): Boolean {
        if (emitted) return false
        emitted = true
        return true
    }
}
