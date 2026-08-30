package com.mediaflow.data.player.background

import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceRecordingServiceTest {
    @Test
    fun `notification title and elapsed formatting`() {
        assertEquals("Recording X Space", SpaceRecordingService.NOTIFICATION_TITLE)
        assertEquals("0:05", SpaceRecordingService.formatElapsed(5_000L))
        assertEquals("1:02:03", SpaceRecordingService.formatElapsed(3_723_000L))
    }
}
