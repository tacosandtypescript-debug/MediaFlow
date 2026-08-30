package com.mediaflow.data.player

/**
 * Ensures a single [com.mediaflow.domain.player.PlaybackEvent.PlaybackFinished]
 * is emitted per loaded media (eof-reached and END_FILE can both fire).
 *
 * [reset] disarms the gate so END_FILE from replacing the previous file
 * cannot be treated as the new track ending.
 */
class PlaybackFinishedGate {
    @Volatile
    private var armed: Boolean = false
    @Volatile
    private var emitted: Boolean = false

    fun reset() {
        armed = false
        emitted = false
    }

    @Synchronized
    fun markStarted() {
        armed = true
    }

    @Synchronized
    fun tryMarkEmitted(): Boolean {
        if (!armed || emitted) return false
        emitted = true
        return true
    }
}
