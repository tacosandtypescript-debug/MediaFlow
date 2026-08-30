package com.mediaflow.data.provider.x.dvr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingDvrBufferTest {
    @Test
    fun `window trims to local minutes only`() {
        val buffer = RollingDvrBuffer(DvrWindowMinutes.FIVE)
        repeat(6) { buffer.acceptTick(ByteArray(60_000)) }
        assertTrue(buffer.bufferedDurationMs <= DvrWindowMinutes.FIVE.durationMs)
        assertEquals(DvrWindowMinutes.FIVE.minutes, 5)
        assertEquals(15, DvrWindowMinutes.FIFTEEN.minutes)
        assertEquals(30, DvrWindowMinutes.THIRTY.minutes)
        assertEquals(60, DvrWindowMinutes.SIXTY.minutes)
    }
}
