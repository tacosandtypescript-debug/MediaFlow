package com.mediaflow.data.provider.x.spaces

import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.spaces.XSpaceCapabilities.Companion.FIELD_DURATION
import com.mediaflow.data.provider.x.spaces.XSpaceCapabilities.Companion.FIELD_LIVE_LISTENERS
import com.mediaflow.data.provider.x.spaces.XSpaceCapabilities.Companion.FIELD_REMOTE_DVR
import com.mediaflow.data.provider.x.spaces.XSpaceCapabilities.Companion.FIELD_SEEK
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])

class XSpaceCapabilitiesTest {

    companion object {
        const val LIVE_STATUS_URL = "https://x.com/barvabe/status/2094108808637305253?s=46"
        const val LIVE_SPACE_URL = "https://x.com/i/spaces/1rGmqplYpggGy"
        const val ENDED_STATUS_URL = "https://x.com/respaldodeandre/status/2094078407109710217?s=46"
        const val ENDED_SPACE_URL = "https://x.com/i/spaces/1NGarowkqQlJj"
    }

    private val host = XParticipant(displayName = "Host", username = "host")

    @Test
    fun missingGraphqlListenerKeyDoesNotInventAudience() {
        val json = JSONObject(
            """
            {
              "data": {
                "audioSpace": {
                  "metadata": {
                    "state": "Running",
                    "title": "Santo Rosario y Ángelus. 📿🙏🏻",
                    "is_space_available_for_replay": false,
                    "creator_results": {
                      "result": {
                        "rest_id": "1",
                        "legacy": { "name": "Bárbara V.", "screen_name": "barvabe" }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val space = XSpaceMetadataResolver().parseGraphqlAudioSpace(
            spaceId = "1rGmqplYpggGy",
            originalUrl = LIVE_STATUS_URL,
            json = json,
        )
        assertEquals(0, space.liveListenersCount)
        val caps = XSpaceCapabilities.from(space, graphqlHadLiveListeners = false)
        assertEquals(XSpaceFieldAvailability.UNAVAILABLE, caps.availability(FIELD_LIVE_LISTENERS))
    }

    @Test
    fun liveVersusEndedCapabilitiesDifferOnlyPerContract() {
        val live = space(
            id = "1rGmqplYpggGy",
            url = LIVE_SPACE_URL,
            state = XSpaceState.LIVE,
            listeners = 22,
            stream = "https://prod-fastly.video.pscp.tv/live.m3u8",
        )
        val ended = space(
            id = "1NGarowkqQlJj",
            url = ENDED_SPACE_URL,
            state = XSpaceState.ENDED,
            listeners = 190,
            duration = 6828L,
            recording = true,
            stream = "https://prod-fastly.video.pscp.tv/replay.m3u8",
        )
        val liveCaps = XSpaceCapabilities.from(live, graphqlHadLiveListeners = true)
        val endedCaps = XSpaceCapabilities.from(ended, graphqlHadLiveListeners = true)

        assertEquals(XSpaceFieldAvailability.DYNAMIC, liveCaps.availability(FIELD_LIVE_LISTENERS))
        assertEquals(XSpaceFieldAvailability.APPROXIMATE, endedCaps.availability(FIELD_LIVE_LISTENERS))
        assertEquals(XSpaceFieldAvailability.UNAVAILABLE, liveCaps.availability(FIELD_DURATION))
        assertEquals(XSpaceFieldAvailability.AVAILABLE, endedCaps.availability(FIELD_DURATION))
        assertEquals(XSpaceFieldAvailability.UNAVAILABLE, liveCaps.availability(FIELD_SEEK))
        assertEquals(XSpaceFieldAvailability.AVAILABLE, endedCaps.availability(FIELD_SEEK))
        assertEquals(XSpaceStreamProtocol.HLS, liveCaps.stream.protocol)
        assertEquals(XSpaceStreamProtocol.HLS, endedCaps.stream.protocol)
        assertFalse(liveCaps.liveSeekAllowed)
        assertTrue(endedCaps.stream.seekSupported)
        assertEquals(LIVE_STATUS_URL, live.url.takeIf { it.contains("barvabe") } ?: LIVE_STATUS_URL)
        assertTrue(ended.url == ENDED_SPACE_URL)
    }

    @Test
    fun missingRemoteDvrDoesNotClaimArbitraryLiveSeek() {
        val live = space(
            id = "1rGmqplYpggGy",
            url = LIVE_SPACE_URL,
            state = XSpaceState.LIVE,
            stream = "https://prod-fastly.video.pscp.tv/live.m3u8",
        )
        val stream = XSpaceStreamInfo.fromPlaybackUrl(live.audioStreamUrl, spaceEnded = false)
        assertNull(stream.remoteDvrWindowSeconds)
        assertFalse(stream.hasRemoteDvr)
        assertFalse(stream.seekSupported)
        val caps = XSpaceCapabilities.from(live, stream)
        assertEquals(XSpaceFieldAvailability.UNAVAILABLE, caps.availability(FIELD_REMOTE_DVR))
        assertEquals(XSpaceFieldAvailability.UNAVAILABLE, caps.availability(FIELD_SEEK))
        assertFalse(caps.liveSeekAllowed)
    }

    private fun space(
        id: String,
        url: String,
        state: XSpaceState,
        listeners: Int = 0,
        duration: Long = 0L,
        recording: Boolean = false,
        stream: String? = null,
    ) = XSpace(
        id = id,
        url = url,
        title = "Space",
        state = state,
        host = host,
        durationSeconds = duration,
        recordingAvailable = recording,
        liveListenersCount = listeners,
        audioStreamUrl = stream,
    )
}
