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

        assertEquals("137+bestaudio", PlatformFormatSelector.select(request))
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

        assertEquals("best[height<=720]/best", PlatformFormatSelector.select(request))
    }
}
