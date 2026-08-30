package com.mediaflow.data

import com.mediaflow.core.model.MediaType
import com.mediaflow.data.resolver.YtDlpSourceResolver
import com.mediaflow.data.resolver.tiktok.TikTokExtractPipeline
import com.mediaflow.data.resolver.tiktok.TikTokResolveException
import com.mediaflow.data.resolver.tiktok.TikTokResolveStage
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

    @Test
    fun `parses root-level single stream JSON without formats array`() = runTest {
        val resolver = YtDlpSourceResolver(RuntimeEnvironment.getApplication())
        val info = resolver.parseForTest("https://www.tiktok.com/@user/video/123", """
            {
              "id": "123",
              "title": "TikTok Reel",
              "url": "https://cdn.tiktok.com/video.mp4",
              "ext": "mp4",
              "width": 720,
              "height": 1280,
              "duration": 15.0
            }
        """.trimIndent())

        assertEquals("TikTok Reel", info.title)
        assertEquals(15L, info.durationSeconds)
        assertEquals(1, info.availableFormats.size)
        val format = info.availableFormats.first()
        assertEquals(MediaType.VIDEO, format.mediaType)
        assertEquals("mp4", format.extension)
        assertEquals(720, format.width)
        assertEquals(1280, format.height)
        assertTrue(format.isProgressive)
        assertFalse(format.requiresMuxing)
    }

    @Test
    fun `parses formats with unspecified codecs but valid extensions`() = runTest {
        val resolver = YtDlpSourceResolver(RuntimeEnvironment.getApplication())
        val info = resolver.parseForTest("https://www.instagram.com/reel/abc", """
            {
              "id": "abc",
              "title": "Instagram Post",
              "formats": [
                {"format_id": "0", "ext": "mp4", "url": "https://cdn.instagram.com/video.mp4", "height": 1080, "width": 1080}
              ]
            }
        """.trimIndent())

        assertEquals("Instagram Post", info.title)
        assertEquals(1, info.availableFormats.size)
        val format = info.availableFormats.first()
        assertEquals(MediaType.VIDEO, format.mediaType)
        assertEquals(1080, format.height)
        assertTrue(format.isProgressive)
    }

    @Test
    fun `TikTok stage codes appear verbatim in analysis errors`() {
        val resolver = YtDlpSourceResolver(RuntimeEnvironment.getApplication())
        val url = "https://vt.tiktok.com/ZSVWNejMx/"
        val stages = listOf(
            TikTokResolveStage.URL_RESOLUTION_FAILED,
            TikTokResolveStage.REDIRECT_FAILED,
            TikTokResolveStage.VIDEO_ID_NOT_FOUND,
            TikTokResolveStage.TIKTOK_BLOCKED,
            TikTokResolveStage.EXTRACTOR_FAILED,
            TikTokResolveStage.MEDIA_URL_FAILED,
            TikTokResolveStage.DOWNLOAD_FAILED,
        )
        for (stage in stages) {
            val message = resolver.friendlyAnalysisError(
                url,
                TikTokResolveException(stage, stage.name),
            )
            assertTrue(message.startsWith("$stage:"))
        }
        assertEquals(
            TikTokResolveStage.TIKTOK_BLOCKED,
            TikTokExtractPipeline.mapBlocked(403, null)?.stage,
        )
        assertEquals(
            TikTokResolveStage.TIKTOK_BLOCKED,
            TikTokExtractPipeline.mapBlocked(429, "too many requests")?.stage,
        )
    }

    @Test
    fun `TikTok webpage failure is mapped to a short Spanish message`() {
        val resolver = YtDlpSourceResolver(RuntimeEnvironment.getApplication())
        val raw = IllegalStateException(
            "DownloadError: ERROR: [TikTok] 7647678491193838865: Unexpected response from webpage request; please report this issue on  https://github.com/yt-dlp/yt-dlp/issues?q= , filling out the appropriate issue template. Confirm you are on the latest version using  yt-dlp -U",
        )
        val message = resolver.friendlyAnalysisError("https://vt.tiktok.com/ZSVWNejMx/", raw)
        assertTrue(message.contains("TikTok"))
        assertFalse(message.contains("github.com"))
        assertFalse(message.contains("yt-dlp -U"))
        assertTrue(message.length < 220)
    }

    @Test
    fun `parses YouTube playlist entries without downloading them yet`() = runTest {
        val resolver = YtDlpSourceResolver(RuntimeEnvironment.getApplication())
        val info = resolver.parseForTest(
            "https://www.youtube.com/playlist?list=PLtest",
            """
            {
              "_type": "playlist",
              "title": "Mi lista",
              "entries": [
                {"id": "aaa", "title": "Primero", "thumbnail": "https://i.ytimg.com/vi/aaa/hqdefault.jpg", "duration": 12},
                {"id": "bbb", "title": "Segundo", "webpage_url": "https://www.youtube.com/watch?v=bbb", "duration": 30},
                {"id": "ccc"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("Mi lista", info.title)
        assertEquals(3, info.playlistEntries.size)
        assertEquals("https://www.youtube.com/watch?v=aaa", info.playlistEntries[0].sourceUrl)
        assertEquals("Primero", info.playlistEntries[0].title)
        assertEquals("https://www.youtube.com/watch?v=bbb", info.playlistEntries[1].sourceUrl)
        assertTrue(info.availableFormats.any { it.mediaType == MediaType.VIDEO })
        assertTrue(info.availableFormats.any { it.mediaType == MediaType.AUDIO })
    }

    @Test
    fun `YouTube Music watch json keeps audio m4a and drops storyboards`() = runTest {
        val resolver = YtDlpSourceResolver(RuntimeEnvironment.getApplication())
        val info = resolver.parseForTest(
            "https://music.youtube.com/watch?v=jf6tbohQG_E&si=P9Ig0EuVhBe2tRO7",
            """
            {
              "id": "jf6tbohQG_E",
              "title": "IYKYK",
              "duration": 156,
              "thumbnail": "https://i.ytimg.com/vi/jf6tbohQG_E/maxresdefault.jpg",
              "formats": [
                {"format_id":"sb0","ext":"mhtml","format_note":"storyboard","vcodec":"none","acodec":"none","height":180},
                {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2","abr":128},
                {"format_id":"251","ext":"webm","vcodec":"none","acodec":"opus","abr":160},
                {"format_id":"18","ext":"mp4","format_note":"360p","width":640,"height":360,"vcodec":"avc1.42001E","acodec":"mp4a.40.2"},
                {"format_id":"137","ext":"mp4","format_note":"1080p","width":1920,"height":1080,"vcodec":"avc1.640020","acodec":"none"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("IYKYK", info.title)
        assertEquals(156L, info.durationSeconds)
        assertTrue(info.playlistEntries.isEmpty())
        assertEquals(emptyList<String>(), info.availableFormats.map { it.formatId }.filter { it.startsWith("sb") })
        val audioIds = info.availableFormats.filter { it.mediaType == MediaType.AUDIO }.map { it.formatId }
        assertEquals(listOf("140", "251"), audioIds.sorted())
        assertTrue(info.availableFormats.any { it.formatId == "18" && it.mediaType == MediaType.VIDEO })
        assertTrue(info.availableFormats.any { it.formatId == "137" && it.requiresMuxing })
    }

    @Test
    fun `YouTube reload and sign-in errors are mapped to Spanish`() {
        val resolver = YtDlpSourceResolver(RuntimeEnvironment.getApplication())
        val music = "https://music.youtube.com/watch?v=jf6tbohQG_E&si=P9Ig0EuVhBe2tRO7"
        val reload = resolver.friendlyAnalysisError(
            music,
            IllegalStateException("ERROR: [youtube] jf6tbohQG_E: The page needs to be reloaded."),
        )
        assertTrue(reload.contains("YouTube"))
        assertFalse(reload.contains("reloaded"))
        val signIn = resolver.friendlyAnalysisError(
            music,
            IllegalStateException("ERROR: [youtube] jf6tbohQG_E: Please sign in"),
        )
        assertTrue(signIn.contains("YouTube"))
        assertTrue(signIn.contains("cookies"))
    }
}
