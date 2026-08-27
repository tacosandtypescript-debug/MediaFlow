package com.mediaflow.data.provider.x.live

import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import com.mediaflow.domain.live.ReplayResolutionResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSpaceEndMonitorTest {

    private val fakeHost = XParticipant(
        userId = "123",
        username = "host_user",
        displayName = "Host User",
        role = ParticipantRole.HOST,
    )

    @Test
    fun `verifySpaceEnded returns Available when Space transitions to ENDED with replay`() = runBlocking {
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

        val monitor = LiveSpaceEndMonitor(
            metadataResolver = mockResolver,
            maxRetries = 2,
            baseDelayMs = 50L,
        )

        val result = monitor.verifySpaceEnded("1jGXgBDyzpNKZ", "https://x.com/i/spaces/1jGXgBDyzpNKZ")
        assertTrue(result is ReplayResolutionResult.Available)
    }

    @Test
    fun `verifySpaceEnded returns Processing when space is still reported LIVE`() = runBlocking {
        val liveSpace = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Space Live",
            state = XSpaceState.LIVE,
            host = fakeHost,
            audioStreamUrl = "https://stream.pscp.tv/live.m3u8",
        )

        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                return liveSpace
            }
        }

        val monitor = LiveSpaceEndMonitor(
            metadataResolver = mockResolver,
            maxRetries = 2,
            baseDelayMs = 50L,
        )

        val result = monitor.verifySpaceEnded("1jGXgBDyzpNKZ", "https://x.com/i/spaces/1jGXgBDyzpNKZ")
        assertTrue(result is ReplayResolutionResult.Processing)
    }
}
