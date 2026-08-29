package com.mediaflow.data.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymousHtmlExtractorTest {
    @Test
    fun `TikTok playAddr unicode escapes become an https CDN URL`() {
        val html = """
            <script id="SIGI_STATE" type="application/json">
            {"ItemModule":{"123":{"video":{"playAddr":"https:\u002F\u002Fv16-webapp-prime.tiktok.com\u002Fvideo\u002Ftos\u002Fv\u002F123\u002F?a=1\u0026br=1000"}}}}
            </script>
        """.trimIndent()

        assertEquals(
            "https://v16-webapp-prime.tiktok.com/video/tos/v/123/?a=1&br=1000",
            TikTokAnonymousResolver.extractPlayAddress(html),
        )
    }

    @Test
    fun `TikTok downloadAddr is preferred over playAddr`() {
        val html = """
            {"video":{"playAddr":"https://cdn.tiktok.com/play.mp4","downloadAddr":"https://cdn.tiktok.com/file.mp4"}}
        """.trimIndent()
        assertEquals(
            "https://cdn.tiktok.com/file.mp4",
            TikTokAnonymousResolver.extractPlayAddress(html),
        )
    }

    @Test
    fun `TikTok play_addr url_list and og video are accepted`() {
        val listHtml = """{"play_addr":{"uri":"v100","url_list":["https://v16m.tiktokcdn.com/file.mp4"]}}"""
        assertEquals(
            "https://v16m.tiktokcdn.com/file.mp4",
            TikTokAnonymousResolver.extractPlayAddress(listHtml),
        )

        val ogHtml = """<meta property="og:video" content="https://v16.tiktokcdn.com/watch.mp4&amp;ok=1" />"""
        assertEquals(
            "https://v16.tiktokcdn.com/watch.mp4&ok=1",
            TikTokAnonymousResolver.extractPlayAddress(ogHtml),
        )
    }

    @Test
    fun `TikTok login pages are not treated as video URLs`() {
        val html = """{"playAddr":"https://www.tiktok.com/login?redirect=video"}"""
        assertNull(TikTokAnonymousResolver.extractPlayAddress(html))
    }

    @Test
    fun `Instagram embed path is rewritten onto www instagram`() {
        assertEquals(
            "https://www.instagram.com/reel/DcSOPIsuOpy/embed/",
            InstagramAnonymousResolver.embedUrl("https://www.instagram.com/reel/DcSOPIsuOpy/?igsi=abc"),
        )
        assertEquals(
            "https://www.instagram.com/p/DcSOPIsuOpy/embed/",
            InstagramAnonymousResolver.embedUrl("https://instagr.am/p/DcSOPIsuOpy/"),
        )
        val alreadyEmbed = "https://www.instagram.com/reel/DcSOPIsuOpy/embed/"
        assertEquals(alreadyEmbed, InstagramAnonymousResolver.embedUrl(alreadyEmbed))
    }

    @Test
    fun `Instagram HTML exposes og video and video_url JSON`() {
        val ogHtml = """
            <meta property="og:video" content="https://scontent.cdninstagram.com/o1/v/t16/clip.mp4?_nc_ht=scontent" />
        """.trimIndent()
        assertEquals(
            "https://scontent.cdninstagram.com/o1/v/t16/clip.mp4?_nc_ht=scontent",
            InstagramAnonymousResolver.extractVideoUrlFromHtml(ogHtml),
        )

        val jsonHtml = """
            {"video_url":"https:\/\/scontent.cdninstagram.com\/o1\/v\/t16\/reel.mp4?efg=1"}
        """.trimIndent()
        assertEquals(
            "https://scontent.cdninstagram.com/o1/v/t16/reel.mp4?efg=1",
            InstagramAnonymousResolver.extractVideoUrlFromHtml(jsonHtml),
        )

        val versionsHtml = """
            {"video_versions":[{"type":101,"url":"https:\u002F\u002Fscontent-mad1-1.cdninstagram.com\u002Fv\u002Ft50.2886-16\u002F123_n.mp4"}]}
        """.trimIndent()
        assertEquals(
            "https://scontent-mad1-1.cdninstagram.com/v/t50.2886-16/123_n.mp4",
            InstagramAnonymousResolver.extractVideoUrlFromHtml(versionsHtml),
        )
    }

    @Test
    fun `Instagram login wall without a CDN mp4 yields nothing`() {
        val html = """
            <html><body>Log in to Instagram
            <meta property="og:image" content="https://instagram.com/static/login.jpg" />
            </body></html>
        """.trimIndent()
        assertNull(InstagramAnonymousResolver.extractVideoUrlFromHtml(html))
    }

    @Test
    fun `anonymous Instagram user agent is a real Chrome 131-136 build`() {
        val ua = InstagramAnonymousResolver.BROWSER_USER_AGENT
        assertFalse(ua.contains("Chrome/150"))
        assertTrue(ua.contains("Chrome/13"))
        val chrome = Regex("""Chrome/(13[1-6])""").find(ua)
        assertTrue("Expected Chrome 131-136 in $ua", chrome != null)
    }
}
