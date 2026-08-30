package com.mediaflow.data.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformUrlSupportTest {
    @Test
    fun `matches common Facebook hosts including share reels`() {
        val urls = listOf(
            "https://www.facebook.com/share/r/1JdP2B9h4L/?mibextid=wwXIfr",
            "https://m.facebook.com/watch/?v=123",
            "https://web.facebook.com/reel/123",
            "https://fb.watch/abcDEF/",
            "https://www.fb.com/reel/123",
        )
        urls.forEach { url ->
            assertEquals(url, PlatformUrlSupport.Platform.FACEBOOK, PlatformUrlSupport.platformFor(url))
            assertEquals(true, PlatformUrlSupport.isSupported(url))
        }
    }

    @Test
    fun `matches Instagram TikTok YouTube and X hosts`() {
        val expected = mapOf(
            "https://www.instagram.com/reel/DcSOPIsuOpy/" to PlatformUrlSupport.Platform.INSTAGRAM,
            "https://instagr.am/p/DcSOPIsuOpy/" to PlatformUrlSupport.Platform.INSTAGRAM,
            "https://vt.tiktok.com/ZSVPEWsKB/" to PlatformUrlSupport.Platform.TIKTOK,
            "https://vm.tiktok.com/ZSVPEWsKB/" to PlatformUrlSupport.Platform.TIKTOK,
            "https://www.tiktok.com/@user/video/123" to PlatformUrlSupport.Platform.TIKTOK,
            "https://youtu.be/dQw4w9WgXcQ" to PlatformUrlSupport.Platform.YOUTUBE,
            "https://youtu.be/dQw4w9WgXcQ?si=sharetoken" to PlatformUrlSupport.Platform.YOUTUBE,
            "https://www.youtube.com/shorts/dQw4w9WgXcQ" to PlatformUrlSupport.Platform.YOUTUBE,
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ" to PlatformUrlSupport.Platform.YOUTUBE,
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ" to PlatformUrlSupport.Platform.YOUTUBE,
            "https://twitter.com/user/status/123" to PlatformUrlSupport.Platform.X,
            "https://x.com/user/status/123/video/1" to PlatformUrlSupport.Platform.X,
            "https://x.com/i/spaces/1jGXgBDyzpNKZ" to PlatformUrlSupport.Platform.X,
        )
        expected.forEach { (url, platform) ->
            assertEquals(url, platform, PlatformUrlSupport.platformFor(url))
        }
    }

    @Test
    fun `detects YouTube playlist urls and ignores mix lists`() {
        assertTrue(
            PlatformUrlSupport.isYoutubePlaylist(
                "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
            ),
        )
        assertFalse(
            PlatformUrlSupport.isYoutubePlaylist(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
            ),
        )
        assertFalse(PlatformUrlSupport.isYoutubePlaylist("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(PlatformUrlSupport.isYoutubePlaylist("https://youtu.be/dQw4w9WgXcQ"))
        assertFalse(
            PlatformUrlSupport.isYoutubePlaylist(
                "https://music.youtube.com/watch?v=jf6tbohQG_E&si=P9Ig0EuVhBe2tRO7",
            ),
        )
    }

    @Test
    fun `YouTube Music share links canonicalize to a watch video id`() {
        val music =
            "https://music.youtube.com/watch?v=jf6tbohQG_E&si=P9Ig0EuVhBe2tRO7"
        assertTrue(PlatformUrlSupport.isYoutubeMusic(music))
        assertEquals("jf6tbohQG_E", PlatformUrlSupport.youtubeVideoId(music))
        assertEquals(
            "https://www.youtube.com/watch?v=jf6tbohQG_E",
            PlatformUrlSupport.canonicalExtractionUrl(music),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            PlatformUrlSupport.canonicalExtractionUrl("https://youtu.be/dQw4w9WgXcQ?si=abc"),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            PlatformUrlSupport.canonicalExtractionUrl(
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=share",
            ),
        )
        assertFalse(PlatformUrlSupport.isYoutubeMusic("https://www.youtube.com/watch?v=jf6tbohQG_E"))
        assertEquals(
            "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
            PlatformUrlSupport.canonicalExtractionUrl(
                "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
            ),
        )
    }

    @Test
    fun `does not treat unrelated hosts as a social platform`() {
        assertNull(PlatformUrlSupport.platformFor("https://example.com/watch/video"))
        assertNotNull(PlatformUrlSupport.platformFor("https://www.facebook.com/share/r/abc"))
    }

    @Test
    fun `direct mp4 and m4a urls are DIRECT not a generic yt-dlp page`() {
        val mp4 = "https://cdn.example.com/clip.mp4"
        val m4a = "https://cdn.example.com/audio.m4a?token=abc"
        assertEquals(PlatformUrlSupport.Platform.DIRECT, PlatformUrlSupport.platformFor(mp4))
        assertEquals(PlatformUrlSupport.Platform.DIRECT, PlatformUrlSupport.platformFor(m4a))
        assertTrue(PlatformUrlSupport.isDirectMedia(mp4))
        assertFalse(PlatformUrlSupport.isGenericYtDlpPage(mp4))
        assertFalse(PlatformUrlSupport.isGenericYtDlpPage(m4a))
        assertTrue(PlatformUrlSupport.isGenericYtDlpPage("https://example.com/watch/video"))
    }

    @Test
    fun `YouTube shorts canonicalize to a watch video id`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            PlatformUrlSupport.canonicalExtractionUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ"),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            PlatformUrlSupport.canonicalExtractionUrl("https://youtu.be/dQw4w9WgXcQ?si=sharetoken"),
        )
    }
}
