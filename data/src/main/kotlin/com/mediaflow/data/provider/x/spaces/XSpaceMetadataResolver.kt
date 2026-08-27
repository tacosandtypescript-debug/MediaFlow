package com.mediaflow.data.provider.x.spaces

import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.XUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Resolves comprehensive X Space public metadata by combining public X GraphQL
 * endpoints (AudioSpaceById, TweetResultByRestId, live_video_stream) with yt-dlp extracted media formats.
 */
class XSpaceMetadataResolver(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) {

    companion object {
        private const val PUBLIC_BEARER_TOKEN =
            "Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"
        private const val GUEST_ACTIVATE_URL = "https://api.x.com/1.1/guest/activate.json"
        private const val AUDIO_SPACE_GQL_HASH = "HPEisOmj1epUNLCWTYhUWw"
        private const val AUDIO_SPACE_GQL_BASE = "https://x.com/i/api/graphql/$AUDIO_SPACE_GQL_HASH/AudioSpaceById"
        private const val TWEET_DETAIL_GQL_HASH = "2ICDjqPd81tulZcYrtpTuQ"
        private const val TWEET_DETAIL_GQL_BASE = "https://x.com/i/api/graphql/$TWEET_DETAIL_GQL_HASH/TweetResultByRestId"
        private const val LIVE_STREAM_API_BASE = "https://api.x.com/1.1/live_video_stream/status"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /**
     * Resolves an [XSpace] from any X URL (direct Space URL or Status Tweet URL).
     */
    suspend fun resolveFromUrl(
        url: String,
        ytDlpJson: JSONObject? = null,
    ): XSpace? = withContext(Dispatchers.IO) {
        val directSpaceId = XUrlParser.extractDirectSpaceId(url)
        val spaceId = if (directSpaceId != null) {
            directSpaceId
        } else {
            val statusId = XUrlParser.extractStatusId(url) ?: return@withContext null
            extractSpaceIdFromTweetStatus(statusId)
        } ?: return@withContext null

        resolve(spaceId, url, ytDlpJson)
    }

    /**
     * Resolves an [XSpace] from a Space ID, original URL, and optional yt-dlp JSON.
     */
    suspend fun resolve(
        spaceId: String,
        originalUrl: String,
        ytDlpJson: JSONObject? = null,
    ): XSpace = withContext(Dispatchers.IO) {
        val gqlResult = fetchAudioSpaceGraphql(spaceId)
        if (gqlResult != null) {
            val space = parseGraphqlAudioSpace(spaceId, originalUrl, gqlResult, ytDlpJson)
            val mediaKey = gqlResult.optJSONObject("data")
                ?.optJSONObject("audioSpace")
                ?.optJSONObject("metadata")
                ?.optString("media_key")
            if (space.audioStreamUrl == null && !mediaKey.isNullOrBlank()) {
                val streamUrl = fetchLiveStreamPlaybackUrl(mediaKey)
                if (streamUrl != null) {
                    return@withContext space.copy(
                        audioStreamUrl = streamUrl,
                        recordingAvailable = true,
                    )
                }
            }
            space
        } else {
            fallbackFromYtDlp(spaceId, originalUrl, ytDlpJson)
        }
    }

    /**
     * Creates normalized MediaFormat list for downloading audio from an [XSpace].
     */
    fun createMediaFormats(space: XSpace): List<MediaFormat> {
        val streamUrl = space.audioStreamUrl ?: return emptyList()
        return listOf(
            MediaFormat(
                formatId = "space_audio_m4a",
                extension = "m4a",
                mimeType = "audio/mp4",
                mediaType = MediaType.AUDIO,
                qualityLabel = "Audio Original (AAC)",
                container = "m4a",
                audioCodec = "aac",
                durationSeconds = space.durationSeconds.takeIf { it > 0 },
                isProgressive = false,
                requiresMuxing = false,
            ),
        )
    }

    /**
     * Parses the X GraphQL AudioSpace response into a normalized [XSpace] model.
     */
    fun parseGraphqlAudioSpace(
        spaceId: String,
        originalUrl: String,
        json: JSONObject,
        ytDlpJson: JSONObject? = null,
    ): XSpace {
        val data = json.optJSONObject("data")
        val audioSpace = data?.optJSONObject("audioSpace") ?: JSONObject()
        val metadata = audioSpace.optJSONObject("metadata") ?: JSONObject()
        val participantsNode = audioSpace.optJSONObject("participants")

        val title = metadata.optString("title").ifBlank {
            ytDlpJson?.optString("title", "X Space").orEmpty().ifBlank { "X Space" }
        }

        val stateStr = metadata.optString("state", "Ended")
        val spaceState = XSpaceState.fromString(stateStr)

        val createdAtMs = metadata.optLong("created_at", 0L).takeIf { it > 0L }
        val startedAtMs = metadata.optLong("started_at", 0L).takeIf { it > 0L }
        val endedAtMs = metadata.optString("ended_at").toLongOrNull()?.takeIf { it > 0L }

        val durationSeconds = if (startedAtMs != null && endedAtMs != null && endedAtMs > startedAtMs) {
            (endedAtMs - startedAtMs) / 1000L
        } else {
            ytDlpJson?.optLong("duration", 0L) ?: 0L
        }

        val recordingAvailable = metadata.optBoolean("is_space_available_for_replay", false)
        val liveListeners = metadata.optInt("total_live_listeners", 0)
        val replayCount = metadata.optInt("total_replay_watched", 0)

        // Parse Host / Creator
        val creatorLegacy = metadata.optJSONObject("creator_results")
            ?.optJSONObject("result")
            ?.optJSONObject("legacy")
        val creatorRestId = metadata.optJSONObject("creator_results")
            ?.optJSONObject("result")
            ?.optString("rest_id")

        val hostDisplayName = creatorLegacy?.optString("name")
            ?: ytDlpJson?.optString("uploader", "Host").orEmpty()
        val hostUsername = creatorLegacy?.optString("screen_name")
            ?: ytDlpJson?.optString("uploader_id", "host").orEmpty()
        val hostAvatar = creatorLegacy?.optString("profile_image_url_https")
            ?.replace("_normal", "_400x400")
            ?: ytDlpJson?.optString("thumbnail")

        val host = XParticipant(
            displayName = hostDisplayName,
            username = hostUsername,
            userId = creatorRestId,
            avatarUrl = hostAvatar,
            role = ParticipantRole.HOST,
        )

        // Parse participants collections
        val allParticipants = mutableListOf<XParticipant>()
        val cohosts = mutableListOf<XParticipant>()
        val speakers = mutableListOf<XParticipant>()

        allParticipants.add(host)

        val adminsArray = participantsNode?.optJSONArray("admins")
        if (adminsArray != null) {
            for (i in 0 until adminsArray.length()) {
                val adminObj = adminsArray.optJSONObject(i) ?: continue
                val p = parseParticipant(adminObj, ParticipantRole.HOST) ?: continue
                if (p.cleanUsername.equals(host.cleanUsername, ignoreCase = true)) {
                    continue
                }
                val cohost = p.copy(role = ParticipantRole.COHOST)
                cohosts.add(cohost)
                allParticipants.add(cohost)
            }
        }

        val speakersArray = participantsNode?.optJSONArray("speakers")
        if (speakersArray != null) {
            for (i in 0 until speakersArray.length()) {
                val speakerObj = speakersArray.optJSONObject(i) ?: continue
                val p = parseParticipant(speakerObj, ParticipantRole.SPEAKER) ?: continue
                speakers.add(p)
                allParticipants.add(p)
            }
        }

        val listenersArray = participantsNode?.optJSONArray("listeners")
        if (listenersArray != null) {
            for (i in 0 until listenersArray.length()) {
                val listenerObj = listenersArray.optJSONObject(i) ?: continue
                val p = parseParticipant(listenerObj, ParticipantRole.LISTENER) ?: continue
                allParticipants.add(p)
            }
        }

        // Deduplicate participants while preserving the highest privilege role
        val deduplicated = allParticipants.distinctBy { it.userId ?: it.cleanUsername.lowercase() }

        // Find audio stream URL from yt-dlp formats if available
        val audioUrl = ytDlpJson?.optJSONArray("formats")?.let { formats ->
            (0 until formats.length()).firstNotNullOfOrNull { i ->
                val f = formats.optJSONObject(i)
                f?.optString("url")?.takeIf { it.isNotBlank() }
            }
        }

        return XSpace(
            id = spaceId,
            url = originalUrl,
            title = title,
            state = spaceState,
            host = host,
            cohosts = cohosts.distinctBy { it.userId ?: it.cleanUsername.lowercase() },
            speakers = speakers.distinctBy { it.userId ?: it.cleanUsername.lowercase() },
            participants = deduplicated,
            createdAtMs = createdAtMs,
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            durationSeconds = durationSeconds,
            recordingAvailable = recordingAvailable || audioUrl != null,
            liveListenersCount = liveListeners,
            replayCount = replayCount,
            audioStreamUrl = audioUrl,
            rawMetadata = json.toString(),
        )
    }

    private fun parseParticipant(obj: JSONObject, defaultRole: ParticipantRole): XParticipant? {
        val displayName = obj.optString("display_name").ifBlank {
            obj.optString("name")
        }
        val username = obj.optString("twitter_screen_name").ifBlank {
            obj.optString("screen_name")
        }
        if (displayName.isBlank() && username.isBlank()) return null

        val userId = obj.optJSONObject("user")?.optString("rest_id")
            ?: obj.optString("periscope_user_id").takeIf { it.isNotBlank() }

        val avatar = obj.optString("avatar_url").takeIf { it.isNotBlank() }
            ?: obj.optString("profile_image_url_https").takeIf { it.isNotBlank() }

        return XParticipant(
            displayName = displayName.ifBlank { username },
            username = username.ifBlank { displayName },
            userId = userId,
            avatarUrl = avatar?.replace("_normal", "_400x400"),
            role = defaultRole,
        )
    }

    private fun fallbackFromYtDlp(
        spaceId: String,
        originalUrl: String,
        ytDlpJson: JSONObject?,
    ): XSpace {
        val title = ytDlpJson?.optString("title", "X Space").orEmpty().ifBlank { "X Space" }
        val uploader = ytDlpJson?.optString("uploader", "Host").orEmpty()
        val uploaderId = ytDlpJson?.optString("uploader_id", "host").orEmpty()
        val thumbnail = ytDlpJson?.optString("thumbnail")
        val duration = ytDlpJson?.optLong("duration", 0L) ?: 0L
        val wasLive = ytDlpJson?.optBoolean("was_live", true) ?: true

        val host = XParticipant(
            displayName = uploader,
            username = uploaderId,
            avatarUrl = thumbnail,
            role = ParticipantRole.HOST,
        )

        val audioUrl = ytDlpJson?.optJSONArray("formats")?.let { formats ->
            (0 until formats.length()).firstNotNullOfOrNull { i ->
                val f = formats.optJSONObject(i)
                f?.optString("url")?.takeIf { it.isNotBlank() }
            }
        }

        return XSpace(
            id = spaceId,
            url = originalUrl,
            title = title,
            state = if (wasLive) XSpaceState.ENDED else XSpaceState.UNKNOWN,
            host = host,
            cohosts = emptyList(),
            speakers = emptyList(),
            participants = listOf(host),
            durationSeconds = duration,
            recordingAvailable = audioUrl != null,
            audioStreamUrl = audioUrl,
            rawMetadata = ytDlpJson?.toString(),
        )
    }

    private fun extractSpaceIdFromTweetStatus(statusId: String): String? {
        return runCatching {
            val guestToken = obtainGuestToken() ?: return@runCatching null
            val variables = JSONObject().apply {
                put("tweetId", statusId)
                put("withCommunity", false)
                put("includePromotedContent", false)
                put("withVoice", true)
            }.toString()

            val features = JSONObject().apply {
                put("creator_subscriptions_tweet_preview_api_enabled", true)
                put("communities_web_enable_tweet_community_results_fetch", true)
                put("c9s_tweet_anatomy_moderator_badge_enabled", true)
                put("tweetypie_unmention_optimization_enabled", true)
                put("responsive_web_edit_tweet_api_enabled", true)
                put("graphql_is_translatable_rweb_tweet_is_translatable_enabled", true)
                put("view_counts_everywhere_api_enabled", true)
                put("longform_notetweets_consumption_enabled", true)
                put("responsive_web_twitter_article_tweet_consumption_enabled", false)
                put("tweet_awards_web_tipping_enabled", false)
                put("freedom_of_speech_not_reach_fetch_enabled", true)
                put("standardized_nudges_misinfo", true)
                put("tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled", true)
                put("rweb_video_timestamps_enabled", true)
                put("longform_notetweets_rich_text_read_enabled", true)
                put("longform_notetweets_inline_media_enabled", true)
                put("responsive_web_media_download_video_enabled", false)
                put("responsive_web_enhance_cards_enabled", false)
            }.toString()

            val encodedVars = URLEncoder.encode(variables, "UTF-8")
            val encodedFeatures = URLEncoder.encode(features, "UTF-8")
            val urlString = "$TWEET_DETAIL_GQL_BASE?variables=$encodedVars&features=$encodedFeatures"

            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Authorization", PUBLIC_BEARER_TOKEN)
                setRequestProperty("x-guest-token", guestToken)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val body = reader.readText()
                reader.close()
                val root = JSONObject(body)
                val card = root.optJSONObject("data")
                    ?.optJSONObject("tweetResult")
                    ?.optJSONObject("result")
                    ?.optJSONObject("card")
                    ?.optJSONObject("legacy")
                val bindingValues = card?.optJSONArray("binding_values")
                if (bindingValues != null) {
                    for (i in 0 until bindingValues.length()) {
                        val item = bindingValues.optJSONObject(i) ?: continue
                        if (item.optString("key") == "id" || item.optString("key") == "audio_space_id") {
                            val id = item.optJSONObject("value")?.optString("string_value")
                            if (!id.isNullOrBlank()) return@runCatching id
                        }
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun fetchLiveStreamPlaybackUrl(mediaKey: String): String? {
        return runCatching {
            val guestToken = obtainGuestToken() ?: return@runCatching null
            val urlString = "$LIVE_STREAM_API_BASE/$mediaKey"
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Authorization", PUBLIC_BEARER_TOKEN)
                setRequestProperty("x-guest-token", guestToken)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val body = reader.readText()
                reader.close()
                val root = JSONObject(body)
                val source = root.optJSONObject("source")
                source?.optString("location")?.takeIf { it.isNotBlank() }
                    ?: source?.optString("noRedirectPlaybackUrl")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun fetchAudioSpaceGraphql(spaceId: String): JSONObject? {
        return runCatching {
            val guestToken = obtainGuestToken() ?: return@runCatching null
            val variables = JSONObject().apply {
                put("id", spaceId)
                put("isMetatagsQuery", true)
                put("withDownvotePerspective", false)
                put("withReactionsMetadata", false)
                put("withReactionsPerspective", false)
                put("withReplays", true)
                put("withSuperFollowsUserFields", true)
                put("withSuperFollowsTweetFields", true)
            }.toString()

            val features = JSONObject().apply {
                put("dont_mention_me_view_api_enabled", true)
                put("interactive_text_enabled", true)
                put("responsive_web_edit_tweet_api_enabled", true)
                put("responsive_web_enhance_cards_enabled", true)
                put("responsive_web_uc_gql_enabled", true)
                put("spaces_2022_h2_clipping", true)
                put("spaces_2022_h2_spaces_communities", false)
                put("standardized_nudges_misinfo", true)
                put("tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled", false)
                put("vibe_api_enabled", true)
            }.toString()

            val encodedVars = URLEncoder.encode(variables, "UTF-8")
            val encodedFeatures = URLEncoder.encode(features, "UTF-8")
            val urlString = "$AUDIO_SPACE_GQL_BASE?variables=$encodedVars&features=$encodedFeatures"

            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Authorization", PUBLIC_BEARER_TOKEN)
                setRequestProperty("x-guest-token", guestToken)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val body = reader.readText()
                reader.close()
                JSONObject(body)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun obtainGuestToken(): String? {
        return runCatching {
            val connection = (URL(GUEST_ACTIVATE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Authorization", PUBLIC_BEARER_TOKEN)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                doOutput = true
            }

            if (connection.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val body = reader.readText()
                reader.close()
                JSONObject(body).optString("guest_token").takeIf { it.isNotBlank() }
            } else {
                null
            }
        }.getOrNull()
    }
}
