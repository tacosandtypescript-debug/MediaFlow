package com.mediaflow.data.player.background

import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlayerServiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MediaSessionControllerTest {

    @Test
    fun lockScreenActionsIncludeSkipPlayPauseAndSeek() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = MediaSessionController(
            context = context,
            onPlayRequested = {},
            onPauseRequested = {},
            onStopRequested = {},
        )
        controller.updatePlaybackState(
            PlayerServiceState(
                mediaId = "id",
                title = "Song",
                playbackState = EnginePlaybackState.PLAYING,
                durationMs = 10_000L,
                currentPositionMs = 100L,
            ),
        )
        controller.updateMetadata("Song", "Artist", durationMs = 10_000L)
        val playback = MediaControllerCompat(context, controller.sessionToken).playbackState
        val actions = playback?.actions ?: 0L
        assertTrue(actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_PLAY_PAUSE != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_SEEK_TO != 0L)
        assertEquals(PlaybackStateCompat.STATE_PLAYING, playback?.state)
        val metadata = MediaControllerCompat(context, controller.sessionToken).metadata
        assertEquals("Song", metadata?.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE))
        controller.release()
    }

    @Test
    fun skipToNextAndPreviousInvokeCallbacks() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var next = 0
        var prev = 0
        val controller = MediaSessionController(
            context = context,
            onPlayRequested = {},
            onPauseRequested = {},
            onStopRequested = {},
            onSkipNextRequested = { next++ },
            onSkipPreviousRequested = { prev++ },
        )
        controller.updatePlaybackState(
            PlayerServiceState(
                mediaId = "id",
                title = "Song",
                playbackState = EnginePlaybackState.PLAYING,
            ),
        )
        val mediaController = MediaControllerCompat(context, controller.sessionToken)
        mediaController.transportControls.skipToNext()
        mediaController.transportControls.skipToPrevious()
        assertEquals(1, next)
        assertEquals(1, prev)
        controller.release()
    }
}
