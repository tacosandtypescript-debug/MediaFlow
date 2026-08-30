package com.mediaflow.data

import androidx.test.core.app.ApplicationProvider
import com.mediaflow.data.download.ThumbnailPersister
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThumbnailPersisterTest {
    @Test
    fun harvestNearbyMovesSidecarIntoThumbsAndDeletesJpg() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val downloadDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val media = File(downloadDir, "clip.mp4").apply { writeText("media") }
        val sidecar = File(downloadDir, "clip.webp").apply { writeText("thumb-bytes") }

        val uri = ThumbnailPersister.harvestNearby(context, "dl-1", media)

        assertTrue(uri!!.startsWith("file:"))
        assertTrue(uri.contains("/thumbs/dl-1.webp"))
        assertFalse(sidecar.exists())
        assertTrue(media.exists())
        assertEquals(uri, ThumbnailPersister.existingUri(context, "dl-1"))
    }

    @Test
    fun harvestNearbyReplacesTinyExistingThumbWithLargerSidecar() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val thumbs = ThumbnailPersister.thumbsDir(context)
        File(thumbs, "dl-big.jpg").writeBytes(ByteArray(80))
        val downloadDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val media = File(downloadDir, "song.m4a").apply { writeText("audio") }
        File(downloadDir, "song.jpg").writeBytes(ByteArray(8_000))

        val uri = ThumbnailPersister.harvestNearby(context, "dl-big", media)

        assertTrue(uri!!.contains("/thumbs/dl-big.jpg"))
        assertEquals(8_000L, File(thumbs, "dl-big.jpg").length())
        assertFalse(File(downloadDir, "song.jpg").exists())
    }

    @Test
    fun persistableImageUrlRejectsHlsAndAudio() {
        assertFalse(ThumbnailPersister.isPersistableImageUrl("https://stream.pscp.tv/live.m3u8"))
        assertFalse(ThumbnailPersister.isPersistableImageUrl("https://cdn.example/space.m4a"))
        assertFalse(ThumbnailPersister.isPersistableImageUrl("https://cdn.example/clip.mp4"))
        assertTrue(ThumbnailPersister.isPersistableImageUrl("https://pbs.twimg.com/profile_images/host.jpg"))
        assertTrue(ThumbnailPersister.isPersistableImageUrl("https://pbs.twimg.com/profile_images/host_400x400.jpg?name=orig"))
    }
}
