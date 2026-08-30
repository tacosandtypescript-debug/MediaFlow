package com.mediaflow.data.provider.x.spaces

import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState

enum class XSpaceFieldAvailability {
    AVAILABLE,
    UNAVAILABLE,
    APPROXIMATE,
    REQUIRES_EXTRA_QUERY,
    DYNAMIC,
}

enum class XSpaceStreamProtocol {
    HLS,
    UNKNOWN,
}

/**
 * Playback facts that GraphQL metadata does not include.
 * [remoteDvrWindowSeconds] stays null unless a playlist/API actually reports a window.
 */
data class XSpaceStreamInfo(
    val protocol: XSpaceStreamProtocol = XSpaceStreamProtocol.UNKNOWN,
    val seekSupported: Boolean = false,
    val remoteDvrWindowSeconds: Long? = null,
) {
    val hasRemoteDvr: Boolean
        get() = remoteDvrWindowSeconds != null && remoteDvrWindowSeconds > 0L

    companion object {
        fun fromPlaybackUrl(url: String?, spaceEnded: Boolean): XSpaceStreamInfo {
            if (url.isNullOrBlank()) return XSpaceStreamInfo()
            val hls = url.contains(".m3u8", ignoreCase = true)
            val protocol = if (hls) XSpaceStreamProtocol.HLS else XSpaceStreamProtocol.UNKNOWN
            return XSpaceStreamInfo(
                protocol = protocol,
                seekSupported = spaceEnded && hls,
                remoteDvrWindowSeconds = null,
            )
        }
    }
}

data class XSpaceCapabilities(
    val state: XSpaceState,
    val fields: Map<String, XSpaceFieldAvailability>,
    val stream: XSpaceStreamInfo,
) {
    fun availability(field: String): XSpaceFieldAvailability =
        fields[field] ?: XSpaceFieldAvailability.UNAVAILABLE

    val liveSeekAllowed: Boolean
        get() = state == XSpaceState.LIVE && stream.hasRemoteDvr && stream.seekSupported

    companion object {
        const val FIELD_TITLE = "title"
        const val FIELD_HOST = "host"
        const val FIELD_STATE = "state"
        const val FIELD_SPACE_ID = "id"
        const val FIELD_LIVE_LISTENERS = "liveListenersCount"
        const val FIELD_REPLAY_COUNT = "replayCount"
        const val FIELD_DURATION = "durationSeconds"
        const val FIELD_RECORDING = "recordingAvailable"
        const val FIELD_AUDIO_STREAM = "audioStreamUrl"
        const val FIELD_MEDIA_KEY = "media_key"
        const val FIELD_REMOTE_DVR = "remoteDvrWindow"
        const val FIELD_SEEK = "seek"
        const val FIELD_SPEAKER_SEGMENTS = "speakerSegments"
        const val FIELD_PARTICIPANTS = "participants"

        fun from(
            space: XSpace,
            stream: XSpaceStreamInfo = XSpaceStreamInfo.fromPlaybackUrl(
                space.audioStreamUrl,
                spaceEnded = space.isEnded,
            ),
            graphqlHadLiveListeners: Boolean = space.liveListenersCount > 0,
            graphqlHadReplayCount: Boolean = space.replayCount > 0,
        ): XSpaceCapabilities {
            val live = space.state == XSpaceState.LIVE
            val ended = space.isEnded
            val fields = linkedMapOf(
                FIELD_SPACE_ID to present(space.id.isNotBlank()),
                FIELD_TITLE to present(space.title.isNotBlank()),
                FIELD_HOST to present(
                    space.host.username.isNotBlank() || space.host.displayName.isNotBlank(),
                ),
                FIELD_STATE to XSpaceFieldAvailability.AVAILABLE,
                FIELD_LIVE_LISTENERS to when {
                    live && graphqlHadLiveListeners -> XSpaceFieldAvailability.DYNAMIC
                    ended && graphqlHadLiveListeners -> XSpaceFieldAvailability.APPROXIMATE
                    else -> XSpaceFieldAvailability.UNAVAILABLE
                },
                FIELD_REPLAY_COUNT to when {
                    graphqlHadReplayCount -> XSpaceFieldAvailability.AVAILABLE
                    else -> XSpaceFieldAvailability.UNAVAILABLE
                },
                FIELD_DURATION to when {
                    live -> XSpaceFieldAvailability.UNAVAILABLE
                    ended && space.durationSeconds > 0L -> XSpaceFieldAvailability.AVAILABLE
                    else -> XSpaceFieldAvailability.UNAVAILABLE
                },
                FIELD_RECORDING to when {
                    live -> XSpaceFieldAvailability.UNAVAILABLE
                    ended && space.recordingAvailable -> XSpaceFieldAvailability.AVAILABLE
                    else -> XSpaceFieldAvailability.UNAVAILABLE
                },
                FIELD_AUDIO_STREAM to when {
                    !space.audioStreamUrl.isNullOrBlank() -> XSpaceFieldAvailability.AVAILABLE
                    else -> XSpaceFieldAvailability.REQUIRES_EXTRA_QUERY
                },
                FIELD_MEDIA_KEY to XSpaceFieldAvailability.REQUIRES_EXTRA_QUERY,
                FIELD_REMOTE_DVR to if (stream.hasRemoteDvr) {
                    XSpaceFieldAvailability.AVAILABLE
                } else {
                    XSpaceFieldAvailability.UNAVAILABLE
                },
                FIELD_SEEK to when {
                    live && stream.hasRemoteDvr -> XSpaceFieldAvailability.AVAILABLE
                    live -> XSpaceFieldAvailability.UNAVAILABLE
                    ended && stream.seekSupported -> XSpaceFieldAvailability.AVAILABLE
                    else -> XSpaceFieldAvailability.UNAVAILABLE
                },
                FIELD_SPEAKER_SEGMENTS to XSpaceFieldAvailability.UNAVAILABLE,
                FIELD_PARTICIPANTS to if (space.participants.isNotEmpty()) {
                    XSpaceFieldAvailability.APPROXIMATE
                } else {
                    XSpaceFieldAvailability.UNAVAILABLE
                },
            )
            return XSpaceCapabilities(space.state, fields, stream)
        }

        private fun present(ok: Boolean): XSpaceFieldAvailability =
            if (ok) XSpaceFieldAvailability.AVAILABLE else XSpaceFieldAvailability.UNAVAILABLE
    }
}
