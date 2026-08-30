package com.mediaflow.domain.player.xspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLagMathTest {

    @Test
    fun lagIsPlayheadMinusLiveEdge() {
        assertEquals(0L, LiveLagMath.lagMs(liveEdgeMs = 10_000L, playheadMs = 10_000L))
        assertEquals(-24_000L, LiveLagMath.lagMs(liveEdgeMs = 90_000L, playheadMs = 66_000L))
    }

    @Test
    fun behindLiveUsesThresholdNotHardcodedMinusOne() {
        assertFalse(LiveLagMath.behindLive(-400L))
        assertTrue(LiveLagMath.behindLive(-500L))
        assertTrue(LiveLagMath.behindLive(-24_100L))
    }

    @Test
    fun formatShowsClockNotGenericCopy() {
        assertEquals("-00:24", LiveLagMath.format(-24_000L))
        assertEquals("-02:16", LiveLagMath.format(-136_000L))
        assertEquals("-1:00:05", LiveLagMath.format(-3_605_000L))
    }
}
