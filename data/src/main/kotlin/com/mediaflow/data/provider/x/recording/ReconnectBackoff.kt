package com.mediaflow.data.provider.x.recording

class ReconnectBackoff(
    private val delaysMs: List<Long> = listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L),
) {
    private var attempt = 0

    val maxAttempts: Int get() = delaysMs.size
    val attemptsUsed: Int get() = attempt
    val exhausted: Boolean get() = attempt >= delaysMs.size

    fun nextDelayMs(): Long? {
        if (exhausted) return null
        val delay = delaysMs[attempt]
        attempt += 1
        return delay
    }

    fun reset() {
        attempt = 0
    }
}
