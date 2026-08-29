package com.mediaflow.data.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `does not treat unrelated hosts as a social platform`() {
        assertNull(PlatformUrlSupport.platformFor("https://example.com/watch/video"))
        assertNotNull(PlatformUrlSupport.platformFor("https://www.facebook.com/share/r/abc"))
    }
}
