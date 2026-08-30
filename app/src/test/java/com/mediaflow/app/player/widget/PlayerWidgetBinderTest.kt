package com.mediaflow.app.player.widget

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.mediaflow.app.R
import com.mediaflow.data.player.external.PlayerExternalSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlayerWidgetBinderTest {

    @Test
    fun playingStateShowsPauseAndRealMetadata() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val views = PlayerWidgetBinder.bind(
            context,
            PlayerExternalSnapshot(
                mediaId = "file:///a.m4a",
                title = "Turn Down for What",
                artist = "DJ Snake",
                artworkUrl = null,
                isPlaying = true,
                isLive = false,
                durationMs = 1_000L,
                positionMs = 10L,
            ),
            artwork = null,
        )
        val root = views.apply(context, null)
        assertEquals("Turn Down for What", root.findViewById<TextView>(R.id.widget_title).text.toString())
        assertEquals("DJ Snake", root.findViewById<TextView>(R.id.widget_artist).text.toString())
        val play = root.findViewById<ImageButton>(R.id.widget_play_pause)
        assertNotNull(play)
        assertEquals(View.VISIBLE, play.visibility)
        assertEquals(context.getString(R.string.widget_pause), play.contentDescription)
    }

    @Test
    fun pausedStateShowsPlayDescription() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val views = PlayerWidgetBinder.bind(
            context,
            PlayerExternalSnapshot(
                mediaId = "file:///a.m4a",
                title = "Song",
                artist = "Artist",
                artworkUrl = null,
                isPlaying = false,
                isLive = false,
                durationMs = 1_000L,
                positionMs = 10L,
            ),
            artwork = null,
        )
        val root = views.apply(context, null)
        val play = root.findViewById<ImageButton>(R.id.widget_play_pause)
        assertEquals(context.getString(R.string.widget_play), play.contentDescription)
    }
}
