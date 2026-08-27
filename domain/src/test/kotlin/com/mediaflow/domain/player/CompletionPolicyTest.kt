package com.mediaflow.domain.player

import com.mediaflow.core.model.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionPolicyTest {

    private val policy = CompletionPolicy()

    @Test
    fun `isCompleted returns true when position exceeds 95 percent`() {
        // 95_000 / 100_000 = 0.95 -> completed
        assertTrue(policy.isCompleted(positionMs = 95_000L, durationMs = 100_000L))
        assertTrue(policy.isCompleted(positionMs = 99_000L, durationMs = 100_000L))
    }

    @Test
    fun `isCompleted returns false when position is under 95 percent`() {
        // 94_000 / 100_000 = 0.94 -> not completed
        assertFalse(policy.isCompleted(positionMs = 94_000L, durationMs = 100_000L))
        assertFalse(policy.isCompleted(positionMs = 50_000L, durationMs = 100_000L))
        assertFalse(policy.isCompleted(positionMs = 0L, durationMs = 100_000L))
    }

    @Test
    fun `isCompleted returns true when isEof is true regardless of position calculation`() {
        assertTrue(policy.isCompleted(positionMs = 10_000L, durationMs = 100_000L, isEof = true))
    }

    @Test
    fun `determineStatus handles NEW, IN_PROGRESS and COMPLETED correctly`() {
        assertEquals(
            PlaybackStatus.NEW,
            policy.determineStatus(positionMs = 0L, durationMs = 100_000L),
        )
        assertEquals(
            PlaybackStatus.IN_PROGRESS,
            policy.determineStatus(positionMs = 50_000L, durationMs = 100_000L),
        )
        assertEquals(
            PlaybackStatus.COMPLETED,
            policy.determineStatus(positionMs = 96_000L, durationMs = 100_000L),
        )
        assertEquals(
            PlaybackStatus.COMPLETED,
            policy.determineStatus(positionMs = 10_000L, durationMs = 100_000L, isEof = true),
        )
    }

    @Test
    fun `computeResumePosition resets completed content to 0`() {
        val resumePosition = policy.computeResumePosition(
            savedPositionMs = 98_000L,
            totalDurationMs = 100_000L,
            status = PlaybackStatus.COMPLETED,
        )
        assertEquals(0L, resumePosition)
    }

    @Test
    fun `computeResumePosition filters small initial jitter under margin`() {
        val resumePosition = policy.computeResumePosition(
            savedPositionMs = 1_500L,
            totalDurationMs = 100_000L,
            status = PlaybackStatus.IN_PROGRESS,
        )
        assertEquals(0L, resumePosition)
    }

    @Test
    fun `computeResumePosition keeps saved position when mid-playback`() {
        val resumePosition = policy.computeResumePosition(
            savedPositionMs = 45_000L,
            totalDurationMs = 100_000L,
            status = PlaybackStatus.IN_PROGRESS,
        )
        assertEquals(45_000L, resumePosition)
    }
}
