package com.mediaflow.app.ui.common

import com.mediaflow.app.ui.common.media.isLoadableArtworkUrl
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.app.ui.library.overlayThumbnails
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkUriTest {
    @Test
    fun rejectsLocalMediaFiles() {
        assertFalse(isLoadableArtworkUrl("file:///storage/emulated/0/MediaFlow/song.m4a"))
        assertFalse(isLoadableArtworkUrl("file:///storage/emulated/0/MediaFlow/video.mp4"))
        assertFalse(isLoadableArtworkUrl("file:///tmp/audio.mp3"))
        assertFalse(isLoadableArtworkUrl(null))
        assertFalse(isLoadableArtworkUrl(""))
    }

    @Test
    fun rejectsHttpsHlsAndAudioAsArtwork() {
        assertFalse(isLoadableArtworkUrl("https://stream.pscp.tv/live.m3u8"))
        assertFalse(isLoadableArtworkUrl("https://stream.pscp.tv/replay.m3u8?token=1"))
        assertFalse(isLoadableArtworkUrl("https://cdn.example/space.m4a"))
        assertFalse(isLoadableArtworkUrl("https://cdn.example/clip.mp4"))
        assertNull(preferredArtworkUrl("https://stream.pscp.tv/live.m3u8", null))
        assertEquals(
            "https://pbs.twimg.com/profile_images/host.jpg",
            preferredArtworkUrl(
                "https://stream.pscp.tv/live.m3u8",
                "https://pbs.twimg.com/profile_images/host.jpg",
            ),
        )
    }

    @Test
    fun acceptsRemoteAndImageFiles() {
        assertTrue(isLoadableArtworkUrl("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
        assertTrue(isLoadableArtworkUrl("http://example.com/cover.webp"))
        assertTrue(isLoadableArtworkUrl("file:///data/user/0/com.mediaflow.app/files/thumbs/id.jpg"))
        assertTrue(isLoadableArtworkUrl("content://media/external/images/media/12"))
    }

    @Test
    fun prefersThumbnailOverAvatarAndIgnoresMediaUri() {
        assertEquals(
            "https://cdn.example/thumb.jpg",
            preferredArtworkUrl("https://cdn.example/thumb.jpg", "https://cdn.example/avatar.png"),
        )
        assertEquals(
            "https://cdn.example/avatar.png",
            preferredArtworkUrl("file:///tmp/song.m4a", "https://cdn.example/avatar.png"),
        )
        assertNull(preferredArtworkUrl("file:///tmp/song.m4a", "file:///tmp/video.mp4"))
    }

    @Test
    fun overlayThumbnailsCopiesPersistedArtOntoGalleryRows() {
        val gallery = listOf(
            DownloadItem(
                id = "content://media/1",
                sourceUrl = "https://example.com/v",
                mediaType = MediaType.VIDEO,
                localUri = "content://media/1",
            ),
        )
        val downloads = listOf(
            DownloadItem(
                id = "dl-1",
                sourceUrl = "https://example.com/v",
                mediaType = MediaType.VIDEO,
                localUri = "content://media/1",
                thumbnailUri = "file:///data/user/0/com.mediaflow.app/files/thumbs/dl-1.jpg",
            ),
        )
        val merged = overlayThumbnails(gallery, downloads)
        assertEquals(
            "file:///data/user/0/com.mediaflow.app/files/thumbs/dl-1.jpg",
            merged.single().thumbnailUri,
        )
    }
}
