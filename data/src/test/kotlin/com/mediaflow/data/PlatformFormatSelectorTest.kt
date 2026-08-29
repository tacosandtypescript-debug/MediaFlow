package com.mediaflow.data

import com.mediaflow.core.model.MediaType
import com.mediaflow.data.download.PlatformFormatSelector
import com.mediaflow.domain.repository.DownloadRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformFormatSelectorTest {
    @Test
    fun `separated video selects exact video plus audio without unrelated fallback`() {
        val request = DownloadRequest(
            sourceUrl = "https://www.facebook.com/reel/example",
            mediaType = MediaType.VIDEO,
            formatId = "137",
            extension = "mp4",
            requiresMuxing = true,
            qualityLabel = "1080p",
        )

        assertEquals(
            "137+bestaudio[ext=m4a]/137+bestaudio[acodec^=mp4a]/137+bestaudio[acodec^=aac]/137+bestaudio",
            PlatformFormatSelector.select(request),
        )
    }

    @Test
    fun `progressive selected format remains exact`() {
        val request = DownloadRequest(
            sourceUrl = "https://www.facebook.com/reel/example",
            mediaType = MediaType.VIDEO,
            formatId = "18",
            extension = "mp4",
            requiresMuxing = false,
        )

        assertEquals("18", PlatformFormatSelector.select(request))
    }

    @Test
    fun `fallback quality is bounded when no format id exists`() {
        val request = DownloadRequest(
            sourceUrl = "https://www.tiktok.com/@example/video/1",
            mediaType = MediaType.VIDEO,
            qualityLabel = "720p",
        )

        assertEquals(
            "bv*[ext=mp4][height<=720]+ba[ext=m4a]/b[ext=mp4][height<=720]/b[height<=720]/b",
            PlatformFormatSelector.select(request),
        )
    }

    @Test
    fun `missing format id prefers mp4 progressive then best`() {
        val request = DownloadRequest(
            sourceUrl = "https://www.youtube.com/watch?v=example",
            mediaType = MediaType.VIDEO,
        )

        assertEquals("bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b", PlatformFormatSelector.select(request))
    }

    @Test
    fun `yt-dlp format id prefers mp4 progressive then best`() {
        val request = DownloadRequest(
            sourceUrl = "https://www.youtube.com/watch?v=example",
            mediaType = MediaType.VIDEO,
            formatId = "yt-dlp",
        )

        assertEquals("bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b", PlatformFormatSelector.select(request))
    }

    @Test
    fun `audio without format id prefers m4a`() {
        val request = DownloadRequest(
            sourceUrl = "https://www.youtube.com/watch?v=example",
            mediaType = MediaType.AUDIO,
        )

        assertEquals("bestaudio[ext=m4a]/bestaudio", PlatformFormatSelector.select(request))
    }
}
