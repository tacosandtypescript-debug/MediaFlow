package com.mediaflow.data.resolver.tiktok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TikTokExtractPipelineTest {
    @Test
    fun `canonical video url does not hop`() {
        val hops = AtomicInteger(0)
        val hop = TikTokRedirectHop {
            hops.incrementAndGet()
            TikTokHopResult(500)
        }
        val resolved = TikTokExtractPipeline.resolveCanonical(
            "https://www.tiktok.com/@creator/video/7123456789012345678",
            hop,
        )
        assertEquals(0, hops.get())
        assertEquals("7123456789012345678", resolved.videoId)
        assertEquals(
            "https://www.tiktok.com/@creator/video/7123456789012345678",
            resolved.canonicalUrl,
        )
    }

    @Test
    fun `short link follows redirect to canonical video id`() {
        val hop = TikTokRedirectHop { url ->
            if (url.contains("vt.tiktok.com")) {
                TikTokHopResult(
                    302,
                    location = "https://www.tiktok.com/@user/video/7555123456789012345",
                )
            } else {
                TikTokHopResult(200)
            }
        }
        val resolved = TikTokExtractPipeline.resolveCanonical(
            "https://vt.tiktok.com/ZSVPEWsKB/",
            hop,
        )
        assertEquals("7555123456789012345", resolved.videoId)
        assertEquals(
            "https://www.tiktok.com/@user/video/7555123456789012345",
            resolved.canonicalUrl,
        )
    }

    @Test
    fun `analysis is not invoked when resolution has not succeeded`() {
        val extractCalled = AtomicBoolean(false)
        val fallbackCalled = AtomicBoolean(false)
        val hop = TikTokRedirectHop { url ->
            if (url.contains("vm.tiktok.com")) {
                TikTokHopResult(302, location = "https://www.tiktok.com/foryou")
            } else {
                TikTokHopResult(200)
            }
        }
        try {
            TikTokExtractPipeline.resolveThenExtract(
                sourceUrl = "https://vm.tiktok.com/Zshort/",
                hop = hop,
                primary = TikTokPageExtractor {
                    extractCalled.set(true)
                    "primary"
                },
                fallback = TikTokPageExtractor {
                    fallbackCalled.set(true)
                    "fallback"
                },
            )
            fail("expected VIDEO_ID_NOT_FOUND")
        } catch (error: TikTokResolveException) {
            assertEquals(TikTokResolveStage.VIDEO_ID_NOT_FOUND, error.stage)
        }
        assertFalse(extractCalled.get())
        assertFalse(fallbackCalled.get())
    }

    @Test
    fun `maps 403 and 429 to TIKTOK_BLOCKED`() {
        assertEquals(
            TikTokResolveStage.TIKTOK_BLOCKED,
            TikTokExtractPipeline.mapBlocked(403, null)?.stage,
        )
        assertEquals(
            TikTokResolveStage.TIKTOK_BLOCKED,
            TikTokExtractPipeline.mapBlocked(429, null)?.stage,
        )
        assertEquals(
            TikTokResolveStage.TIKTOK_BLOCKED,
            TikTokExtractPipeline.mapBlocked(200, "Please wait verify you are human")?.stage,
        )
        val hop = TikTokRedirectHop { TikTokHopResult(403, bodySnippet = "blocked") }
        try {
            TikTokExtractPipeline.resolveCanonical("https://vt.tiktok.com/Zx/", hop)
            fail("expected blocked")
        } catch (error: TikTokResolveException) {
            assertEquals(TikTokResolveStage.TIKTOK_BLOCKED, error.stage)
        }
    }

    @Test
    fun `redirect hop failure maps to REDIRECT_FAILED`() {
        val hop = TikTokRedirectHop { error("socket closed") }
        try {
            TikTokExtractPipeline.resolveCanonical("https://vt.tiktok.com/Zx/", hop)
            fail("expected redirect failed")
        } catch (error: TikTokResolveException) {
            assertEquals(TikTokResolveStage.REDIRECT_FAILED, error.stage)
        }
    }

    @Test
    fun `one alternative fallback then EXTRACTOR_FAILED`() {
        val primaryCalls = AtomicInteger(0)
        val fallbackCalls = AtomicInteger(0)
        val hop = TikTokRedirectHop { TikTokHopResult(200) }
        try {
            TikTokExtractPipeline.resolveThenExtract(
                sourceUrl = "https://www.tiktok.com/@a/video/7123456789012345678",
                hop = hop,
                primary = TikTokPageExtractor {
                    primaryCalls.incrementAndGet()
                    error("yt-dlp empty")
                },
                fallback = TikTokPageExtractor {
                    fallbackCalls.incrementAndGet()
                    error("anonymous empty")
                },
            )
            fail("expected extractor failed")
        } catch (error: TikTokResolveException) {
            assertEquals(TikTokResolveStage.EXTRACTOR_FAILED, error.stage)
        }
        assertEquals(1, primaryCalls.get())
        assertEquals(1, fallbackCalls.get())
    }

    @Test
    fun `fallback succeeds after primary extractor fail`() {
        val result = TikTokExtractPipeline.resolveThenExtract(
            sourceUrl = "https://www.tiktok.com/video/7123456789012345678",
            hop = TikTokRedirectHop { TikTokHopResult(500) },
            primary = TikTokPageExtractor { error("primary") },
            fallback = TikTokPageExtractor { canonical ->
                assertTrue(canonical.contains("7123456789012345678"))
                "ok"
            },
        )
        assertEquals("ok", result)
    }
}
