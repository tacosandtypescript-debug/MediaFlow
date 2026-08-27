package com.mediaflow.data

import com.mediaflow.core.model.MediaType
import com.mediaflow.data.resolver.YtDlpSourceResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YtDlpSourceResolverTest {
    @Test
    fun `json analysis preserves progressive separated tracks and technical metadata`() = runTest {
        val resolver = YtDlpSourceResolver(RuntimeEnvironment.getApplication())
        val info = resolver.parseForTest("https://example.test/video", """
            {
              "title":"Legal sample",
              "duration":42.7,
              "thumbnail":"https://cdn.example/thumb.jpg",
              "formats":[
                {"format_id":"18","ext":"mp4","format_note":"360p","width":640,"height":360,"fps":30,"vcodec":"avc1","acodec":"mp4a.40.2","tbr":800,"filesize":1000},
                {"format_id":"137","ext":"mp4","container":"mp4","width":1920,"height":1080,"fps":60,"vcodec":"avc1.640028","acodec":"none","vbr":5000,"filesize_approx":9000},
                {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2","abr":128,"filesize":2000}
              ]
            }
        """.trimIndent())

        assertEquals("Legal sample", info.title)
        assertEquals(42L, info.durationSeconds)
        assertEquals(3, info.availableFormats.size)
        val progressive = info.availableFormats.first { it.formatId == "18" }
        val videoOnly = info.availableFormats.first { it.formatId == "137" }
        val audioOnly = info.availableFormats.first { it.formatId == "140" }
        assertTrue(progressive.isProgressive)
        assertFalse(progressive.requiresMuxing)
        assertEquals("360p", progressive.qualityLabel)
        assertTrue(videoOnly.requiresMuxing)
        assertEquals(1920, videoOnly.width)
        assertEquals(60.0, videoOnly.fps!!, 0.01)
        assertEquals(MediaType.AUDIO, audioOnly.mediaType)
        assertEquals("m4a", audioOnly.extension)
    }

    @Test
    fun `parses X Space json and enriches with spaceMetadata`() = runTest {
        val resolver = YtDlpSourceResolver(RuntimeEnvironment.getApplication())
        val info = resolver.parseForTest("https://x.com/fake_user/status/12345", """
            {
              "id": "mock_space_id_99",
              "extractor": "twitter:spaces",
              "extractor_key": "TwitterSpaces",
              "title": "Audio Space Discussion",
              "uploader": "Space Host",
              "uploader_id": "spacehost",
              "thumbnail": "https://pbs.twimg.com/profile_images/1/pic.jpg",
              "duration": 3600,
              "was_live": true,
              "formats": [
                {"format_id": "0", "ext": "m4a", "vcodec": "none", "acodec": "aac", "url": "https://pscp.tv/stream.m3u8"}
              ]
            }
        """.trimIndent())

        assertNotNull(info.spaceMetadata)
        assertEquals("mock_space_id_99", info.spaceMetadata?.id)
        assertEquals("Audio Space Discussion", info.spaceMetadata?.title)
        assertEquals("Space Host", info.spaceMetadata?.host?.displayName)
        assertEquals("spacehost", info.spaceMetadata?.host?.username)
        assertEquals(1, info.availableFormats.size)
    }
}
