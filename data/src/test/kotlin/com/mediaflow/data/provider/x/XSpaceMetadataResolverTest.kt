package com.mediaflow.data.provider.x

import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XSpaceMetadataResolverTest {

    private val resolver = XSpaceMetadataResolver()

    @Test
    fun `parses rich real GraphQL AudioSpace response accurately`() {
        val sampleJson = JSONObject("""
        {
          "data": {
            "audioSpace": {
              "metadata": {
                "created_at": 1787796571762,
                "started_at": 1787796578569,
                "ended_at": "1787801991889",
                "state": "Ended",
                "title": "ASOCIACIÓN DE MADRES SOLTERAS A FAVOR DE DOSIS Y SU FUNA",
                "is_space_available_for_replay": true,
                "total_live_listeners": 190,
                "total_replay_watched": 19,
                "creator_results": {
                  "result": {
                    "rest_id": "2007303125829595137",
                    "legacy": {
                      "name": "Fake Kiffs Deus",
                      "screen_name": "FakeKiffs",
                      "profile_image_url_https": "https://pbs.twimg.com/profile_images/2091666581973532672/tSJoVaOr_normal.jpg"
                    }
                  }
                }
              },
              "participants": {
                "admins": [
                  {
                    "display_name": "Fake Kiffs Deus",
                    "twitter_screen_name": "FakeKiffs",
                    "avatar_url": "https://pbs.twimg.com/profile_images/2091666581973532672/tSJoVaOr_normal.jpg",
                    "user": { "rest_id": "2007303125829595137" }
                  }
                ],
                "speakers": [
                  {
                    "display_name": "Guest Speaker 1",
                    "twitter_screen_name": "guest1",
                    "avatar_url": "https://pbs.twimg.com/profile_images/1/pic_normal.jpg",
                    "user": { "rest_id": "99901" }
                  }
                ],
                "listeners": []
              }
            }
          }
        }
        """.trimIndent())

        val space = resolver.parseGraphqlAudioSpace(
            spaceId = "1wGWjlyzqeNKQ",
            originalUrl = "https://x.com/fakekiffs/status/2092796653707067736?s=46",
            json = sampleJson,
        )

        assertEquals("1wGWjlyzqeNKQ", space.id)
        assertEquals("ASOCIACIÓN DE MADRES SOLTERAS A FAVOR DE DOSIS Y SU FUNA", space.title)
        assertEquals(XSpaceState.ENDED, space.state)
        assertTrue(space.recordingAvailable)
        assertEquals(190, space.liveListenersCount)
        assertEquals(19, space.replayCount)

        // Host
        assertEquals("Fake Kiffs Deus", space.host.displayName)
        assertEquals("FakeKiffs", space.host.username)
        assertEquals("@FakeKiffs", space.host.formattedHandle)
        assertEquals(ParticipantRole.HOST, space.host.role)
        assertEquals("2007303125829595137", space.host.userId)
        assertTrue(space.host.avatarUrl?.contains("_400x400") == true)

        // Speakers
        assertEquals(1, space.speakers.size)
        assertEquals("Guest Speaker 1", space.speakers[0].displayName)
        assertEquals("@guest1", space.speakers[0].formattedHandle)
        assertEquals(ParticipantRole.SPEAKER, space.speakers[0].role)

        // All speakers (host + speaker)
        assertEquals(2, space.allSpeakers.size)
        assertEquals(5413L, space.durationSeconds) // 1h 30m 13s
        assertEquals("1 h 30 min", space.formattedDuration)
    }

    @Test
    fun `handles space with no extra speakers and missing timestamps safely`() {
        val minimalJson = JSONObject("""
        {
          "data": {
            "audioSpace": {
              "metadata": {
                "state": "Running",
                "title": "Live Discussion",
                "is_space_available_for_replay": false,
                "creator_results": {
                  "result": {
                    "rest_id": "123",
                    "legacy": {
                      "name": "Host Only",
                      "screen_name": "hostonly"
                    }
                  }
                }
              },
              "participants": {
                "admins": [],
                "speakers": []
              }
            }
          }
        }
        """.trimIndent())

        val space = resolver.parseGraphqlAudioSpace(
            spaceId = "s_min",
            originalUrl = "https://x.com/i/spaces/s_min",
            json = minimalJson,
        )

        assertEquals("s_min", space.id)
        assertEquals("Live Discussion", space.title)
        assertEquals(XSpaceState.LIVE, space.state)
        assertEquals(0, space.speakers.size)
        assertEquals(1, space.allSpeakers.size)
        assertEquals("Host Only", space.host.displayName)
        assertEquals(0L, space.durationSeconds)
        assertEquals(false, space.recordingAvailable)
    }

    @Test
    fun `live GraphQL plus yt-dlp HLS does not set recordingAvailable`() {
        val liveJson = JSONObject("""
        {
          "data": {
            "audioSpace": {
              "metadata": {
                "state": "Running",
                "title": "Live Discussion",
                "is_space_available_for_replay": false,
                "creator_results": {
                  "result": {
                    "rest_id": "123",
                    "legacy": {
                      "name": "Host Only",
                      "screen_name": "hostonly"
                    }
                  }
                }
              }
            }
          }
        }
        """.trimIndent())
        val ytDlp = JSONObject("""
        {
          "formats": [
            { "url": "https://prod-fastly-us-west-2.video.pscp.tv/live.m3u8" }
          ]
        }
        """.trimIndent())

        val space = resolver.parseGraphqlAudioSpace(
            spaceId = "s_live",
            originalUrl = "https://x.com/i/spaces/s_live",
            json = liveJson,
            ytDlpJson = ytDlp,
        )

        org.junit.Assert.assertFalse(space.recordingAvailable)
        assertEquals("https://prod-fastly-us-west-2.video.pscp.tv/live.m3u8", space.audioStreamUrl)
        assertEquals(XSpaceState.LIVE, space.state)
    }

    @Test
    fun `yt-dlp fallback was_live with audio does not mark LIVE as replay-available`() {
        val ytDlp = JSONObject(
            """
            {
              "title": "Live Space",
              "uploader": "Host",
              "uploader_id": "host",
              "is_live": true,
              "was_live": true,
              "formats": [ { "url": "https://prod-fastly.video.pscp.tv/live.m3u8" } ]
            }
            """.trimIndent(),
        )
        val space = resolver.fallbackFromYtDlp(
            spaceId = "s_live_yt",
            originalUrl = "https://x.com/i/spaces/s_live_yt",
            ytDlpJson = ytDlp,
        )
        assertEquals(XSpaceState.LIVE, space.state)
        org.junit.Assert.assertFalse(space.recordingAvailable)
        assertEquals("https://prod-fastly.video.pscp.tv/live.m3u8", space.audioStreamUrl)
    }

    @Test
    fun `yt-dlp fallback ended replay sets recordingAvailable only when not live`() {
        val ytDlp = JSONObject(
            """
            {
              "title": "Ended Space",
              "uploader": "Host",
              "uploader_id": "host",
              "is_live": false,
              "was_live": true,
              "formats": [ { "url": "https://prod-fastly.video.pscp.tv/replay.m3u8" } ]
            }
            """.trimIndent(),
        )
        val space = resolver.fallbackFromYtDlp(
            spaceId = "s_ended_yt",
            originalUrl = "https://x.com/i/spaces/s_ended_yt",
            ytDlpJson = ytDlp,
        )
        assertEquals(XSpaceState.ENDED, space.state)
        assertTrue(space.recordingAvailable)
    }
}
