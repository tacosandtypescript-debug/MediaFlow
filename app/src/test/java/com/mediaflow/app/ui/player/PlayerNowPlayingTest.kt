package com.mediaflow.app.ui.player

import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlayerServiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNowPlayingTest {

    @Test
    fun `embedded title wins over numeric MediaStore id`() {
        val shown = PlayerDisplayMetadata.title(
            taggedTitle = "Turn Down for What",
            serviceTitle = "20567",
            fileName = "20567.m4a",
            uri = "content://media/external/audio/media/20567",
        )
        assertEquals("Turn Down for What", shown)
        assertFalse(shown.all { it.isDigit() })
    }

    @Test
    fun `artist tag is kept when service host is missing`() {
        assertEquals(
            "DJ Snake",
            PlayerDisplayMetadata.artist("DJ Snake", null),
        )
        assertEquals("Inferno", PlayerDisplayMetadata.album("Inferno"))
        assertEquals(null, PlayerDisplayMetadata.artist("<unknown>", "unknown"))
    }

    @Test
    fun `filename is used when tags are missing and uri is a numeric id`() {
        val shown = PlayerDisplayMetadata.title(
            taggedTitle = null,
            serviceTitle = "20567",
            fileName = "Turn Down for What.m4a",
            uri = "content://media/external/audio/media/20567",
        )
        assertEquals("Turn Down for What", shown)
    }

    @Test
    fun `seek fraction maps onto duration`() {
        val duration = 180_000L
        assertEquals(0L, PlayerTimelineMath.positionForFraction(0f, duration))
        assertEquals(90_000L, PlayerTimelineMath.positionForFraction(0.5f, duration))
        assertEquals(duration, PlayerTimelineMath.positionForFraction(1f, duration))
        assertEquals(0L, PlayerTimelineMath.positionForFraction(0.5f, 0L))
    }

    @Test
    fun `displayed position stays on scrub while dragging`() {
        val shown = PlayerTimelineMath.displayedPositionMs(
            isScrubbing = true,
            scrubPositionMs = 12_000L,
            enginePositionMs = 40_000L,
        )
        assertEquals(12_000L, shown)
        assertEquals(
            40_000L,
            PlayerTimelineMath.displayedPositionMs(
                isScrubbing = false,
                scrubPositionMs = 12_000L,
                enginePositionMs = 40_000L,
            ),
        )
    }

    @Test
    fun `PlayerUiState play pause follows engine flag`() {
        val playing = PlayerUiState(
            serviceState = PlayerServiceState(playbackState = EnginePlaybackState.PLAYING),
        )
        val paused = PlayerUiState(
            serviceState = PlayerServiceState(playbackState = EnginePlaybackState.PAUSED),
        )
        assertTrue(playing.isPlaying)
        assertFalse(playing.isPaused)
        assertTrue(paused.isPaused)
        assertFalse(paused.isPlaying)
    }

    @Test
    fun `duration falls back to file metadata when engine duration is zero`() {
        val state = PlayerUiState(
            fileDurationMs = 241_000L,
            serviceState = PlayerServiceState(durationMs = 0L),
        )
        assertEquals(241_000L, state.durationMs)
        assertEquals(
            10_000L,
            state.copy(serviceState = PlayerServiceState(durationMs = 10_000L)).durationMs,
        )
    }

    @Test
    fun `PlayerUiState currentPosition uses scrub while isScrubbing`() {
        val state = PlayerUiState(
            isScrubbing = true,
            scrubPositionMs = 8_000L,
            serviceState = PlayerServiceState(currentPositionMs = 55_000L, durationMs = 120_000L),
        )
        assertEquals(8_000L, state.currentPositionMs)
        assertEquals(
            55_000L,
            state.copy(isScrubbing = false).currentPositionMs,
        )
    }
}
