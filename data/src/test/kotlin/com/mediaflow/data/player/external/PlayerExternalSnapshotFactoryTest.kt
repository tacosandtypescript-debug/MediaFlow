package com.mediaflow.data.player.external

import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlayerServiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExternalSnapshotFactoryTest {

    @Test
    fun usesRealTitleAndArtistFromPlayerState() {
        val snapshot = PlayerExternalSnapshotFactory.from(
            PlayerServiceState(
                mediaId = "file:///song.m4a",
                title = "Turn Down for What",
                artistOrHost = "DJ Snake",
                artworkUrl = "file:///cache/cover.jpg",
                playbackState = EnginePlaybackState.PLAYING,
                durationMs = 60_000L,
                currentPositionMs = 1_000L,
            ),
        )
        assertEquals("Turn Down for What", snapshot.title)
        assertEquals("DJ Snake", snapshot.artist)
        assertTrue(snapshot.isPlaying)
        assertEquals("file:///cache/cover.jpg", snapshot.artworkUrl)
    }

    @Test
    fun playAndPauseProduceDifferentVisualKeys() {
        val playing = PlayerExternalSnapshotFactory.from(
            PlayerServiceState(
                mediaId = "id",
                title = "Song",
                artistOrHost = "Artist",
                playbackState = EnginePlaybackState.PLAYING,
            ),
        )
        val paused = PlayerExternalSnapshotFactory.from(
            PlayerServiceState(
                mediaId = "id",
                title = "Song",
                artistOrHost = "Artist",
                playbackState = EnginePlaybackState.PAUSED,
            ),
        )
        assertTrue(playing.isPlaying)
        assertFalse(paused.isPlaying)
        assertNotEquals(playing.visualKey, paused.visualKey)
    }

    @Test
    fun positionTicksDoNotChangeVisualKey() {
        val a = PlayerExternalSnapshotFactory.from(
            PlayerServiceState(
                mediaId = "id",
                title = "Song",
                playbackState = EnginePlaybackState.PLAYING,
                currentPositionMs = 10L,
            ),
        )
        val b = PlayerExternalSnapshotFactory.from(
            PlayerServiceState(
                mediaId = "id",
                title = "Song",
                playbackState = EnginePlaybackState.PLAYING,
                currentPositionMs = 500L,
            ),
        )
        assertEquals(a.visualKey, b.visualKey)
    }

    @Test
    fun blankTitleFallsBackToMediaFlow() {
        val snapshot = PlayerExternalSnapshotFactory.from(
            PlayerServiceState(mediaId = "id", title = "  ", playbackState = EnginePlaybackState.IDLE),
        )
        assertEquals("MediaFlow", snapshot.title)
        assertEquals("En pausa", snapshot.artist)
        assertFalse(snapshot.isPlaying)
    }
}
