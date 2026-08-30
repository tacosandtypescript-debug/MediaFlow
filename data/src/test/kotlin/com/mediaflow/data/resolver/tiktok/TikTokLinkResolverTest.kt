package com.mediaflow.data.resolver.tiktok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokLinkResolverTest {
    private val videoId = "7123456789012345678"
    private val canonical = "https://www.tiktok.com/@demo/video/$videoId"

    @Test
    fun `full at-user video path`() {
        val result = resolver().resolve("https://www.tiktok.com/@demo/video/$videoId")
        val success = result as TikTokResolveResult.Success
        assertEquals(videoId, success.link.videoId)
        assertEquals(canonical, success.link.canonicalUrl)
        assertTrue(TikTokUrlSanitizer.looksLikeTikTokHost(success.link.sanitizedUrl))
    }

    @Test
    fun `www host is TikTok`() {
        val paste = "https://www.tiktok.com/@demo/video/$videoId"
        val success = resolver().resolve(paste) as TikTokResolveResult.Success
        assertEquals(videoId, success.link.videoId)
        assertTrue(TikTokUrlSanitizer.looksLikeTikTokHost(paste))
    }

    @Test
    fun `vm short host is TikTok and follows Location chain`() {
        val short = "https://vm.tiktok.com/ZSVPEWsKB/"
        assertTrue(TikTokUrlSanitizer.isShortHost(short))
        assertTrue(TikTokUrlSanitizer.looksLikeTikTokHost(short))
        val hopper = mapHopper(
            short.trimEnd('/') + "/" to "https://www.tiktok.com/@demo/video/$videoId?_t=abc",
        )
        val success = TikTokLinkResolver(hopper).resolve(short) as TikTokResolveResult.Success
        assertEquals(videoId, success.link.videoId)
        assertEquals(canonical, success.link.canonicalUrl)
        assertEquals(listOf("https://vm.tiktok.com/ZSVPEWsKB/", canonical), success.link.redirectChain)
        assertFalse(success.link.canonicalUrl.contains("_t="))
    }

    @Test
    fun `vt short host is TikTok and follows Location chain`() {
        val short = "https://vt.tiktok.com/ZSxyz/"
        assertTrue(TikTokUrlSanitizer.isShortHost(short))
        val hopper = mapHopper(
            "https://vt.tiktok.com/ZSxyz/" to "https://m.tiktok.com/v/$videoId",
            "https://m.tiktok.com/v/$videoId" to canonical,
        )
        val success = TikTokLinkResolver(hopper).resolve(short) as TikTokResolveResult.Success
        assertEquals(videoId, TikTokVideoId.fromUrl(success.link.canonicalUrl))
        assertEquals(canonical, success.link.canonicalUrl)
    }

    @Test
    fun `tracking query ttclid utm and si stripped from canonical`() {
        val dirty =
            "https://www.tiktok.com/@demo/video/$videoId?ttclid=1&utm_source=share&utm_medium=ios&si=abc"
        val success = resolver().resolve(dirty) as TikTokResolveResult.Success
        assertEquals(canonical, success.link.canonicalUrl)
        assertFalse(success.link.canonicalUrl.contains("ttclid"))
        assertFalse(success.link.canonicalUrl.contains("utm_"))
        assertFalse(success.link.canonicalUrl.contains("si="))
        assertEquals(videoId, TikTokVideoId.fromUrl(success.link.sanitizedUrl))
        assertFalse(success.link.sanitizedUrl.contains("ttclid"))
        assertFalse(success.link.sanitizedUrl.contains("utm_"))
        assertFalse(success.link.sanitizedUrl.contains("si="))
    }

    @Test
    fun `Android share paste with surrounding text strips to URL`() {
        val paste = "Mira esto https://www.tiktok.com/@demo/video/$videoId?utm_source=copy 🔥"
        val extracted = TikTokUrlSanitizer.extractUrl(paste)!!
        assertFalse(extracted.contains("Mira"))
        val success = resolver().resolve(paste) as TikTokResolveResult.Success
        assertEquals(videoId, success.link.videoId)
        assertEquals(canonical, success.link.canonicalUrl)
    }

    @Test
    fun `redirect Location chain landing on at-user video id`() {
        val start = "https://vm.tiktok.com/ABC123/"
        val hopper = mapHopper(
            "https://vm.tiktok.com/ABC123/" to "https://www.tiktok.com/t/ABC123/?_r=1",
            "https://www.tiktok.com/t/ABC123/" to "https://www.tiktok.com/@demo/video/$videoId?is_from_webapp=1",
        )
        val success = TikTokLinkResolver(hopper).resolve(start) as TikTokResolveResult.Success
        assertEquals(videoId, success.link.videoId)
        assertEquals(canonical, success.link.canonicalUrl)
        assertTrue(success.link.redirectChain.size >= 2)
        assertEquals(canonical, success.link.redirectChain.last())
    }

    @Test
    fun `short link 200 without video id is VIDEO_ID_NOT_FOUND`() {
        val hopper = TikTokHttpHopper { TikTokHttpHopper.Hop(status = 200, location = null) }
        val result = TikTokLinkResolver(hopper).resolve("https://vm.tiktok.com/NOPE/")
        val failure = result as TikTokResolveResult.Failure
        assertEquals(TikTokResolveStage.VIDEO_ID_NOT_FOUND, failure.stage)
    }

    @Test
    fun `short link 302 without Location is REDIRECT_FAILED`() {
        val hopper = TikTokHttpHopper { TikTokHttpHopper.Hop(status = 302, location = null) }
        val result = TikTokLinkResolver(hopper).resolve("https://vm.tiktok.com/NOPE/")
        val failure = result as TikTokResolveResult.Failure
        assertEquals(TikTokResolveStage.REDIRECT_FAILED, failure.stage)
    }

    @Test
    fun `blocked hop is TIKTOK_BLOCKED`() {
        val hopper = TikTokHttpHopper { TikTokHttpHopper.Hop(status = 403, blocked = true) }
        val result = TikTokLinkResolver(hopper).resolve("https://vm.tiktok.com/BLOCK/")
        assertEquals(TikTokResolveStage.TIKTOK_BLOCKED, (result as TikTokResolveResult.Failure).stage)
    }

    @Test
    fun `numeric id when path has video id`() {
        assertEquals(videoId, TikTokVideoId.fromUrl("https://www.tiktok.com/@user/video/$videoId"))
        assertEquals(videoId, TikTokVideoId.fromUrl("https://tiktok.com/video/$videoId"))
        assertEquals(null, TikTokVideoId.fromUrl("https://vm.tiktok.com/ZSVPEWsKB/"))
    }

    @Test
    fun `m tiktok host is TikTok and canonicalizes video id`() {
        val paste = "https://m.tiktok.com/@demo/video/$videoId?utm_campaign=share"
        assertTrue(TikTokUrlSanitizer.looksLikeTikTokHost(paste))
        val success = resolver().resolve(paste) as TikTokResolveResult.Success
        assertEquals(videoId, success.link.videoId)
        assertEquals(canonical, success.link.canonicalUrl)
        assertFalse(success.link.sanitizedUrl.contains("utm_"))
        assertPipeline(success)
    }

    @Test
    fun `scheme-less vm and vt hosts are TikTok`() {
        val vm = "vm.tiktok.com/ZSVPEWsKB/"
        val vt = "vt.tiktok.com/ZSxyz/"
        assertTrue(TikTokUrlSanitizer.looksLikeTikTokHost(vm))
        assertTrue(TikTokUrlSanitizer.looksLikeTikTokHost(vt))
        assertTrue(TikTokUrlSanitizer.isShortHost(vm))
        assertTrue(TikTokUrlSanitizer.isShortHost(vt))
        val extractedVm = TikTokUrlSanitizer.extractUrl("mira $vm extra")!!
        assertTrue(extractedVm.startsWith("https://"))
        val hopper = mapHopper(
            "https://vm.tiktok.com/ZSVPEWsKB/" to canonical,
        )
        val success = TikTokLinkResolver(hopper).resolve("compartido $vm") as TikTokResolveResult.Success
        assertEquals(videoId, success.link.videoId)
        assertPipeline(success)
        assertTrue(success.trace.pipeline.contains("sanitized=https://vm.tiktok.com/ZSVPEWsKB/"))
        assertTrue(success.trace.pipeline.contains("videoId=$videoId"))
        assertTrue(success.trace.pipeline.contains("extractor=${TikTokResolveTrace.EXTRACTOR}"))
    }

    @Test
    fun `resolve trace pipeline is input sanitized redirects canonical videoId extractor`() {
        val success = resolver().resolve("https://www.tiktok.com/@demo/video/$videoId") as TikTokResolveResult.Success
        assertPipeline(success)
        val pipeline = success.trace.pipeline
        assertTrue(pipeline.startsWith("input="))
        assertTrue(pipeline.contains(" -> sanitized="))
        assertTrue(pipeline.contains(" -> redirects="))
        assertTrue(pipeline.contains(" -> canonical="))
        assertTrue(pipeline.contains(" -> videoId=$videoId"))
        assertTrue(pipeline.endsWith(" -> extractor=${TikTokResolveTrace.EXTRACTOR}"))
    }

    private fun assertPipeline(success: TikTokResolveResult.Success) {
        val pipeline = success.trace.pipeline
        assertTrue(pipeline.contains("input="))
        assertTrue(pipeline.contains("sanitized="))
        assertTrue(pipeline.contains("redirects="))
        assertTrue(pipeline.contains("canonical="))
        assertTrue(pipeline.contains("videoId="))
        assertTrue(pipeline.contains("extractor="))
        assertEquals(success.link.trace.pipeline, success.trace.pipeline)
    }

    private fun resolver() = TikTokLinkResolver(
        hopper = TikTokHttpHopper { error("no network") },
    )

    private fun mapHopper(vararg pairs: Pair<String, String>): TikTokHttpHopper {
        val map = pairs.toMap()
        return TikTokHttpHopper { url ->
            val loc = map[url] ?: map[url.trimEnd('/')] ?: map["$url/"]
            TikTokHttpHopper.Hop(status = 301, location = loc)
        }
    }
}
