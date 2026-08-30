package com.mediaflow.data.provider.x.spaces

import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpaceAvailabilityResolverTest {
    private fun space(
        state: XSpaceState,
        stream: String? = "https://example/live.m3u8",
        recording: Boolean = false,
        duration: Long = 0L,
    ) = XSpace(
        id = "1jGXgBDyzpNKZ",
        url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
        title = "Space",
        state = state,
        host = XParticipant(displayName = "Host", username = "host"),
        durationSeconds = duration,
        recordingAvailable = recording,
        audioStreamUrl = stream,
    )

    @Test
    fun liveWithStreamIsLiveAndHasNoDuration() {
        val live = space(XSpaceState.LIVE, duration = 3600L)
        assertEquals(SpaceAvailability.LIVE, SpaceAvailabilityResolver.from(live))
        assertNull(SpaceAvailabilityResolver.displayDurationSeconds(live))
    }

    @Test
    fun endedWithoutReplayIsUnavailable() {
        val ended = space(XSpaceState.ENDED, stream = null, recording = false, duration = 90L)
        assertEquals(SpaceAvailability.UNAVAILABLE, SpaceAvailabilityResolver.from(ended))
    }

    @Test
    fun endedReplayReportsRealDuration() {
        val ended = space(XSpaceState.ENDED, stream = "https://example/replay.m4a", recording = true, duration = 90L)
        assertEquals(SpaceAvailability.ENDED, SpaceAvailabilityResolver.from(ended))
        assertEquals(90L, SpaceAvailabilityResolver.displayDurationSeconds(ended))
    }

    @Test
    fun upcomingIsScheduled() {
        assertEquals(
            SpaceAvailability.SCHEDULED,
            SpaceAvailabilityResolver.from(space(XSpaceState.UPCOMING, stream = null)),
        )
    }
}
