package com.mediaflow.data.provider.x.live

import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import com.mediaflow.domain.live.ReplayResolutionResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XSpaceReplayResolverTest {

    private val fakeHost = XParticipant(
        userId = "123",
        username = "host_user",
        displayName = "Host User",
        role = ParticipantRole.HOST,
    )

    @Test
    fun `resolveReplay returns Available when ended space has audio stream`() = runBlocking {
        val endedSpace = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Space Finalizado",
            state = XSpaceState.ENDED,
            host = fakeHost,
            audioStreamUrl = "https://stream.pscp.tv/replay.m3u8",
            recordingAvailable = true,
        )

        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                return endedSpace
            }
        }

        val replayResolver = XSpaceReplayResolver(mockResolver)
        val result = replayResolver.resolveReplay("1jGXgBDyzpNKZ", "https://x.com/i/spaces/1jGXgBDyzpNKZ")

        assertTrue(result is ReplayResolutionResult.Available)
        val available = result as ReplayResolutionResult.Available
        assertEquals("https://stream.pscp.tv/replay.m3u8", available.replayUrl)
        assertEquals("1jGXgBDyzpNKZ", available.space.id)
    }

    @Test
    fun `resolveReplay returns NotAvailable when host disabled recording`() = runBlocking {
        val endedSpaceNoRecording = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Space Sin Grabación",
            state = XSpaceState.ENDED,
            host = fakeHost,
            audioStreamUrl = null,
            recordingAvailable = false,
        )

        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                return endedSpaceNoRecording
            }
        }

        val replayResolver = XSpaceReplayResolver(mockResolver)
        val result = replayResolver.resolveReplay("1jGXgBDyzpNKZ", "https://x.com/i/spaces/1jGXgBDyzpNKZ")

        assertTrue(result is ReplayResolutionResult.NotAvailable)
    }

    @Test
    fun `resolveReplay returns Processing when space is still live`() = runBlocking {
        val liveSpace = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Space En Vivo",
            state = XSpaceState.LIVE,
            host = fakeHost,
            audioStreamUrl = "https://stream.pscp.tv/live.m3u8",
        )

        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                return liveSpace
            }
        }

        val replayResolver = XSpaceReplayResolver(mockResolver)
        val result = replayResolver.resolveReplay("1jGXgBDyzpNKZ", "https://x.com/i/spaces/1jGXgBDyzpNKZ")

        assertTrue(result is ReplayResolutionResult.Processing)
    }
}
