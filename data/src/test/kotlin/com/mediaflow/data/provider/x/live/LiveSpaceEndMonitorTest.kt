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

    @Test
    fun `verifySpaceEnded returns Processing when ended recording has no replay url yet`() = runBlocking {
        val processingSpace = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Space Procesando",
            state = XSpaceState.ENDED,
            host = fakeHost,
            audioStreamUrl = null,
            recordingAvailable = true,
        )
        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                return processingSpace
            }
        }
        val monitor = LiveSpaceEndMonitor(
            metadataResolver = mockResolver,
            maxRetries = 2,
            baseDelayMs = 10L,
            sleeper = {},
        )
        val result = monitor.verifySpaceEnded("1jGXgBDyzpNKZ", "https://x.com/i/spaces/1jGXgBDyzpNKZ")
        assertTrue(result is ReplayResolutionResult.Processing)
    }

    @Test
    fun `verifySpaceEnded does not exceed maxRetries GraphQL hits`() = runBlocking {
        var hits = 0
        val unknown = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Unknown",
            state = XSpaceState.UNKNOWN,
            host = fakeHost,
        )
        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                hits++
                return unknown
            }
        }
        val monitor = LiveSpaceEndMonitor(
            metadataResolver = mockResolver,
            maxRetries = 3,
            baseDelayMs = 5L,
            sleeper = {},
        )
        monitor.verifySpaceEnded("1jGXgBDyzpNKZ", "https://x.com/i/spaces/1jGXgBDyzpNKZ")
        assertTrue("hits=$hits", hits <= 4)
    }

    @Test
    fun `waitForReplay becomes Available on a later attempt then stops`() = runBlocking {
        var hits = 0
        val processing = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Procesando",
            state = XSpaceState.ENDED,
            host = fakeHost,
            audioStreamUrl = null,
            recordingAvailable = true,
        )
        val ready = processing.copy(audioStreamUrl = "https://stream.pscp.tv/replay.m3u8")
        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                hits++
                return if (hits >= 3) ready else processing
            }
        }
        val monitor = LiveSpaceEndMonitor(
            metadataResolver = mockResolver,
            maxRetries = 1,
            baseDelayMs = 1L,
            replayWaitDelaysMs = listOf(1L, 1L, 1L),
            maxReplayWaitAttempts = 8,
            sleeper = {},
        )
        val result = monitor.waitForReplay("1jGXgBDyzpNKZ", "https://x.com/i/spaces/1jGXgBDyzpNKZ")
        assertTrue(result is ReplayResolutionResult.Available)
        assertTrue("should stop after Available, hits=$hits", hits <= 8)
    }

    @Test
    fun `waitForReplay stops at max attempts without looping forever`() = runBlocking {
        var hits = 0
        val processing = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Procesando",
            state = XSpaceState.ENDED,
            host = fakeHost,
            audioStreamUrl = null,
            recordingAvailable = true,
        )
        val mockResolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                hits++
                return processing
            }
        }
        val monitor = LiveSpaceEndMonitor(
            metadataResolver = mockResolver,
            maxRetries = 1,
            baseDelayMs = 1L,
            replayWaitDelaysMs = listOf(1L),
            maxReplayWaitAttempts = 3,
            sleeper = {},
        )
        val result = monitor.waitForReplay("1jGXgBDyzpNKZ", "https://x.com/i/spaces/1jGXgBDyzpNKZ")
        assertTrue(result is ReplayResolutionResult.NotAvailable)
        assertEquals(LiveSpaceEndMonitor.REPLAY_TIMEOUT_MESSAGE, (result as ReplayResolutionResult.NotAvailable).reason)
        assertTrue("hits=$hits", hits <= 4)
    }

    @Test
    fun `verifySpaceEnded returns NotAvailable when ended without recording or url`() = runBlocking {
        val ended = XSpace(
            id = "1jGXgBDyzpNKZ",
            url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
            title = "Sin grabación",
            state = XSpaceState.ENDED,
            host = fakeHost,
            audioStreamUrl = null,
            recordingAvailable = false,
        )
        val monitor = LiveSpaceEndMonitor(
            metadataResolver = object : XSpaceMetadataResolver() {
                override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace = ended
            },
            maxRetries = 1,
            sleeper = {},
        )
        val result = monitor.verifySpaceEnded("1jGXgBDyzpNKZ", "https://x.com/i/spaces/1jGXgBDyzpNKZ")
        assertTrue(result is ReplayResolutionResult.NotAvailable)
    }
}
