package com.mediaflow.data.player.background

import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlayerServiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlaybackNotificationManagerTest {

    @Test
    fun buildNotificationHasThreeActionsCompactViewAndRealTitle() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val session = MediaSessionController(
            context = context,
            onPlayRequested = {},
            onPauseRequested = {},
            onStopRequested = {},
        )
        val notification = PlaybackNotificationManager(context).buildNotification(
            serviceState = PlayerServiceState(
                mediaId = "file:///song.m4a",
                title = "Turn Down for What",
                artistOrHost = "DJ Snake",
                playbackState = EnginePlaybackState.PLAYING,
            ),
            sessionToken = session.sessionToken,
        )
        assertEquals("Turn Down for What", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("DJ Snake", notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(3, notification.actions.size)
        assertEquals("Anterior", notification.actions[0].title.toString())
        assertEquals("Pausar", notification.actions[1].title.toString())
        assertEquals("Siguiente", notification.actions[2].title.toString())
        val compact = notification.extras.getIntArray("android.compactActions")
        assertNotNull(compact)
        assertTrue(compact!!.contentEquals(intArrayOf(0, 1, 2)))
        assertNotNull(notification.extras.getParcelable("android.mediaSession"))
        session.release()
    }

    @Test
    fun pausedNotificationUsesPlayActionTitle() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = PlaybackNotificationManager(context).buildNotification(
            PlayerServiceState(
                mediaId = "id",
                title = "Song",
                artistOrHost = "Artist",
                playbackState = EnginePlaybackState.PAUSED,
            ),
        )
        assertEquals(3, notification.actions.size)
        assertEquals("Reproducir", notification.actions[1].title.toString())
    }
}
