package com.mediaflow.data.provider.x.dvr

/**
 * Local rolling window. Inspector remote DVR is unavailable on LIVE.
 */
class RollingDvrBuffer(
    window: DvrWindowMinutes = DvrWindowMinutes.FIFTEEN,
) {
    var window: DvrWindowMinutes = window
        set(value) {
            field = value
            trim()
        }

    private val segments = ArrayDeque<DvrSegment>()
    private var nextIndex = 0
    private var nextRelativeStartMs = 0L

    val bufferedDurationMs: Long
        get() = segments.sumOf { it.durationMs }

    fun acceptTick(bytes: ByteArray): DvrSegment {
        val segment = DvrSegment(nextIndex++, nextRelativeStartMs, bytes.copyOf())
        nextRelativeStartMs += segment.durationMs
        segments.addLast(segment)
        trim()
        return segment
    }

    fun snapshot(): List<DvrSegment> = segments.toList()

    fun clear() {
        segments.clear()
        nextIndex = 0
        nextRelativeStartMs = 0L
    }

    private fun trim() {
        val limit = window.durationMs
        while (segments.isNotEmpty() && bufferedDurationMs > limit) {
            segments.removeFirst()
        }
    }
}
