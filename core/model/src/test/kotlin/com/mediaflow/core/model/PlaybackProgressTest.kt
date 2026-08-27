package com.mediaflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {

    @Test
    fun `new progress initializes with status NEW and 0 percent`() {
        val progress = PlaybackProgress.new(mediaId = "test-123", filePath = "/path/to/video.mp4")
        assertEquals("test-123", progress.mediaId)
        assertEquals("/path/to/video.mp4", progress.filePath)
        assertEquals(PlaybackStatus.NEW, progress.status)
        assertEquals(0f, progress.playbackPercentage, 0.001f)
        assertEquals(0, progress.percentageInt)
        assertFalse(progress.isCompleted)
    }

    @Test
    fun `percentage is calculated accurately when duration is positive`() {
        val progress = PlaybackProgress(
            mediaId = "item-1",
            filePath = "/path/media.mp4",
            totalDurationMs = 100_000L,
            currentPositionMs = 64_000L,
            status = PlaybackStatus.IN_PROGRESS,
            lastPlayedAt = 1000L,
        )
        assertEquals(0.64f, progress.playbackPercentage, 0.001f)
        assertEquals(64, progress.percentageInt)
        assertFalse(progress.isCompleted)
    }

    @Test
    fun `completed progress reports isCompleted true`() {
        val progress = PlaybackProgress(
            mediaId = "item-2",
            filePath = "/path/media.mp4",
            totalDurationMs = 100_000L,
            currentPositionMs = 96_000L,
            status = PlaybackStatus.COMPLETED,
            lastPlayedAt = 2000L,
        )
        assertTrue(progress.isCompleted)
        assertEquals(96, progress.percentageInt)
    }
}
