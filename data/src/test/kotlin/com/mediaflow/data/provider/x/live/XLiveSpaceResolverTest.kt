package com.mediaflow.data.provider.x.live

import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XLiveSpaceResolverTest {

    private val fakeHost = XParticipant(
        userId = "12345",
        username = "elonmusk",
        displayName = "Elon Musk",
        role = ParticipantRole.HOST,
    )

    @Test
    fun `resolveLiveSpace succeeds for LIVE Space with playable stream`() = runBlocking {
        val testSpace = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Space en vivo de prueba",
            state = XSpaceState.LIVE,
            host = fakeHost,
            audioStreamUrl = "https://prod-fastly-us-east-1.video.pscp.tv/Transcoding/v1/hls/test/master_playlist.m3u8",
            liveListenersCount = 1250,
        )

        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                return testSpace
            }
        }

        val liveResolver = XLiveSpaceResolver(metadataResolver = mockResolver)
        val result = liveResolver.resolveLiveSpace("https://x.com/i/spaces/1jGXgBDyzpNKZ")

        assertTrue(result.isSuccess)
        val source = result.getOrNull()
        assertNotNull(source)
        assertEquals("1jGXgBDyzpNKZ", source?.spaceId)
        assertEquals("Space en vivo de prueba", source?.title)
        assertEquals(XSpaceState.LIVE, source?.state)
        assertEquals("https://prod-fastly-us-east-1.video.pscp.tv/Transcoding/v1/hls/test/master_playlist.m3u8", source?.streamUrl)
        assertEquals(1250, source?.liveListenersCount)
        assertTrue(source?.isLive == true)
    }

    @Test
    fun `resolveLiveSpace fails with scheduled message for UPCOMING Space`() = runBlocking {
        val testSpace = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Space futuro",
            state = XSpaceState.UPCOMING,
            host = fakeHost,
        )

        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                return testSpace
            }
        }

        val liveResolver = XLiveSpaceResolver(metadataResolver = mockResolver)
        val result = liveResolver.resolveLiveSpace("https://x.com/i/spaces/1jGXgBDyzpNKZ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("todavía no ha comenzado") == true)
    }

    @Test
    fun `resolveLiveSpace succeeds for ENDED Space with replay stream available`() = runBlocking {
        val testSpace = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Space finalizado con grabación",
            state = XSpaceState.ENDED,
            host = fakeHost,
            audioStreamUrl = "https://prod-fastly-us-east-1.video.pscp.tv/Transcoding/v1/hls/replay/master_playlist.m3u8",
            recordingAvailable = true,
        )

        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                return testSpace
            }
        }

        val liveResolver = XLiveSpaceResolver(metadataResolver = mockResolver)
        val result = liveResolver.resolveLiveSpace("https://x.com/i/spaces/1jGXgBDyzpNKZ")

        assertTrue(result.isSuccess)
        val source = result.getOrNull()
        assertNotNull(source)
        assertEquals("1jGXgBDyzpNKZ", source?.spaceId)
        assertEquals(XSpaceState.ENDED, source?.state)
    }
}
