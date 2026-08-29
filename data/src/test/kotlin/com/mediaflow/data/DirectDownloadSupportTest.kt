package com.mediaflow.data

import androidx.media3.exoplayer.offline.Download
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaType
import com.mediaflow.data.download.Media3DownloadStateMapper
import com.mediaflow.data.resolver.DirectUrlSourceResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectDownloadSupportTest {
    private val resolver = DirectUrlSourceResolver()

    @Test
    fun `empty URL is rejected`() {
        val result = resolver.createRequest("", MediaType.VIDEO, null, null)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `HTTP URL is rejected`() {
        val result = resolver.createRequest(
            "http://example.com/video.mp4",
            MediaType.VIDEO,
            null,
            null,
        )
        assertFalse(result.isSuccess)
    }

    @Test
    fun `direct HTTPS video is accepted with automatic name`() = runTest {
        val source = "https://media.example/video.mp4?token=redacted"
        val result = resolver.createRequest(source, MediaType.VIDEO, "Automática", null)

        assertTrue(result.isSuccess)
        assertEquals(source, result.getOrThrow().sourceUrl)
        assertEquals("video.mp4", result.getOrThrow().fileName)
        assertEquals("video/mp4", result.getOrThrow().mimeType)
        assertEquals("mp4", result.getOrThrow().extension)

        val info = resolver.analyze(source)
        assertEquals("video.mp4", info.title)
        assertEquals(MediaType.VIDEO, info.availableFormats.single().mediaType)
    }

    @Test
    fun `generic HTTPS web page is delegated to yt-dlp`() = runTest {
        val result = resolver.createRequest(
            "https://example.com/watch/video",
            MediaType.VIDEO,
            null,
            null,
        )
        assertTrue(result.isSuccess)
        assertEquals("yt-dlp", resolver.analyze("https://example.com/watch/video").availableFormats.single().formatId)
    }

    @Test
    fun `public Facebook share reel is accepted by the platform resolver`() {
        val source = "https://www.facebook.com/share/r/1JdP2B9h4L/?mibextid=wwXIfr"
        val result = resolver.createRequest(source, MediaType.VIDEO, "Automática", null)

        assertTrue(result.isSuccess)
        assertEquals(source, result.getOrThrow().sourceUrl)
        assertEquals("video/mp4", result.getOrThrow().mimeType)
        assertEquals("mp4", result.getOrThrow().extension)
    }

    @Test
    fun `Facebook platform link accepts audio and video requests`() {
        val result = resolver.createRequest(
            "https://www.facebook.com/share/r/1JdP2B9h4L/",
            MediaType.AUDIO,
            "Alta",
            null,
        )

        assertTrue(result.isSuccess)
        assertEquals(MediaType.AUDIO, result.getOrThrow().mediaType)
        assertEquals("m4a", result.getOrThrow().extension)
    }

    @Test
    fun `YouTube X Instagram and TikTok platform links are accepted`() {
        val urls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://x.com/nicdunz/status/2090872826529988887/video/1?s=46",
            "https://twitter.com/nicdunz/status/2090872826529988887",
            "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            "https://www.instagram.com/reel/DcSOPIsuOpy/?igsi=MTVtMzlpazloOGxpeg==",
            "https://instagr.am/p/DcSOPIsuOpy/",
            "https://vt.tiktok.com/ZSVPEWsKB/",
            "https://vm.tiktok.com/ZSVPEWsKB/",
            "https://m.facebook.com/watch/?v=1234567890",
            "https://fb.watch/abcDEF/",
            "https://fb.com/reel/1234567890",
        )

        urls.forEach { url ->
            val result = resolver.createRequest(url, MediaType.VIDEO, "Automática", null)
            assertTrue("Expected platform URL to be accepted: $url", result.isSuccess)
            assertEquals(MediaType.VIDEO, result.getOrThrow().mediaType)
        }
    }

    @Test
    fun `custom name is sanitized and receives the source extension`() {
        val result = resolver.createRequest(
            "https://example.com/audio.m4a",
            MediaType.AUDIO,
            "Alta",
            "  sesión:/audio  ",
        )

        assertTrue(result.isSuccess)
        assertEquals("sesiónaudio.m4a", result.getOrThrow().fileName)
    }

    @Test
    fun `selected type must match the direct file`() {
        val result = resolver.createRequest(
            "https://example.com/audio.mp3",
            MediaType.VIDEO,
            null,
            null,
        )
        assertFalse(result.isSuccess)
    }

    @Test
    fun `Media3 states map to domain states`() {
        assertEquals(DownloadStatus.QUEUED, Media3DownloadStateMapper.map(Download.STATE_QUEUED, 0))
        assertEquals(DownloadStatus.PREPARING, Media3DownloadStateMapper.map(Download.STATE_RESTARTING, 0))
        assertEquals(DownloadStatus.DOWNLOADING, Media3DownloadStateMapper.map(Download.STATE_DOWNLOADING, 0))
        assertEquals(DownloadStatus.PAUSED, Media3DownloadStateMapper.map(Download.STATE_STOPPED, 1))
        assertEquals(DownloadStatus.IDLE, Media3DownloadStateMapper.map(Download.STATE_STOPPED, 0))
        assertEquals(DownloadStatus.COMPLETED, Media3DownloadStateMapper.map(Download.STATE_COMPLETED, 0))
        assertEquals(DownloadStatus.FAILED, Media3DownloadStateMapper.map(Download.STATE_FAILED, 0))
        assertEquals(DownloadStatus.CANCELED, Media3DownloadStateMapper.map(Download.STATE_REMOVING, 0))
    }
}
