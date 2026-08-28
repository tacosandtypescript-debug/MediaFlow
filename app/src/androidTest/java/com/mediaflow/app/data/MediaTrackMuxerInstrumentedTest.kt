package com.mediaflow.app.data

import android.media.MediaExtractor
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mediaflow.data.media.MediaTrackMuxer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Android MediaMuxer against two staged, separate tracks. */
@RunWith(AndroidJUnit4::class)
class MediaTrackMuxerInstrumentedTest {
    @Test
    fun combinesSeparateH264AndAacTracksIntoPlayableMp4() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "mux-instrumented")
        val video = File(directory, "video.mp4")
        val audio = File(directory, "audio.m4a")
        val output = File(directory, "combined.mp4")
        try {
            directory.mkdirs()
            val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
            testAssets.open("video.mp4").use { input ->
                video.outputStream().use { input.copyTo(it) }
            }
            testAssets.open("audio.m4a").use { input ->
                audio.outputStream().use { input.copyTo(it) }
            }
            assertTrue("video fixture was not staged", video.isFile && video.length() > 0L)
            assertTrue("audio fixture was not staged", audio.isFile && audio.length() > 0L)
            MediaTrackMuxer.mergeMp4(video, audio, output).getOrThrow()
            assertTrue(output.isFile && output.length() > 0L)

            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                assertEquals(2, extractor.trackCount)
                assertTrue(extractor.getTrackFormat(0).getString("mime")?.startsWith("video/") == true)
                assertTrue(extractor.getTrackFormat(1).getString("mime")?.startsWith("audio/") == true)
            } finally {
                extractor.release()
            }

            val ready = CountDownLatch(1)
            var player: ExoPlayer? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                player = ExoPlayer.Builder(context).build().also { instance ->
                    instance.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) ready.countDown()
                        }
                    })
                    instance.setMediaItem(MediaItem.fromUri(Uri.fromFile(output)))
                    instance.prepare()
                }
            }
            try {
                assertTrue("Media3 could not prepare the combined MP4", ready.await(10, TimeUnit.SECONDS))
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    assertTrue("Media3 reported an empty combined duration", player?.duration ?: 0L > 0L)
                }
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    player?.release()
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
