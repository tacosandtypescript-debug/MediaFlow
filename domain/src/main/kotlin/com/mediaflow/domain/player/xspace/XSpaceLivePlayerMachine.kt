package com.mediaflow.domain.player.xspace

enum class XSpaceConnectionState {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ENDED,
    ERROR,
}

enum class XSpacePlaybackMode {
    LIVE,
    BEHIND_LIVE,
    PAUSED,
    BUFFERING,
    REPLAY,
}

data class XSpaceLivePlayerState(
    val connection: XSpaceConnectionState = XSpaceConnectionState.CONNECTING,
    val playback: XSpacePlaybackMode = XSpacePlaybackMode.BUFFERING,
    val liveLagMs: Long = 0L,
    val liveControlActive: Boolean = false,
    val liveSeekAllowed: Boolean = false,
    val replaySeekAllowed: Boolean = false,
    val sessionGeneration: Int = 0,
) {
    val atLiveEdge: Boolean
        get() = playback == XSpacePlaybackMode.LIVE && liveLagMs == 0L

    val isReplay: Boolean
        get() = playback == XSpacePlaybackMode.REPLAY ||
            (connection == XSpaceConnectionState.ENDED && playback != XSpacePlaybackMode.LIVE)
}

sealed class XSpaceLivePlayerEvent {
    data class OpenLive(val liveSeekAllowed: Boolean) : XSpaceLivePlayerEvent()
    data class OpenReplay(val seekAllowed: Boolean) : XSpaceLivePlayerEvent()
    data object ConnectedAtLiveEdge : XSpaceLivePlayerEvent()
    data object Reconnecting : XSpaceLivePlayerEvent()
    data object Recovered : XSpaceLivePlayerEvent()
    data class Error(val message: String? = null) : XSpaceLivePlayerEvent()
    data object Pause : XSpaceLivePlayerEvent()
    data object Resume : XSpaceLivePlayerEvent()
    data object JumpToLiveEdge : XSpaceLivePlayerEvent()
    data class LagSample(val lagMs: Long) : XSpaceLivePlayerEvent()
    data object Buffering : XSpaceLivePlayerEvent()
    data object IngestEnded : XSpaceLivePlayerEvent()
    data class StartReplay(val seekAllowed: Boolean) : XSpaceLivePlayerEvent()
}

object XSpaceLivePlayerMachine {
    fun initial(): XSpaceLivePlayerState = XSpaceLivePlayerState()

    fun reduce(state: XSpaceLivePlayerState, event: XSpaceLivePlayerEvent): XSpaceLivePlayerState {
        return when (event) {
            is XSpaceLivePlayerEvent.OpenLive -> state.copy(
                connection = XSpaceConnectionState.CONNECTING,
                playback = XSpacePlaybackMode.BUFFERING,
                liveLagMs = 0L,
                liveControlActive = true,
                liveSeekAllowed = event.liveSeekAllowed,
                replaySeekAllowed = false,
                sessionGeneration = state.sessionGeneration + 1,
            )
            is XSpaceLivePlayerEvent.OpenReplay -> state.copy(
                connection = XSpaceConnectionState.ENDED,
                playback = XSpacePlaybackMode.REPLAY,
                liveLagMs = 0L,
                liveControlActive = false,
                liveSeekAllowed = false,
                replaySeekAllowed = event.seekAllowed,
                sessionGeneration = state.sessionGeneration + 1,
            )
            XSpaceLivePlayerEvent.ConnectedAtLiveEdge -> {
                if (state.connection == XSpaceConnectionState.ENDED) state
                else state.copy(
                    connection = XSpaceConnectionState.CONNECTED,
                    playback = XSpacePlaybackMode.LIVE,
                    liveLagMs = 0L,
                    liveControlActive = true,
                )
            }
            XSpaceLivePlayerEvent.Reconnecting -> {
                if (state.connection == XSpaceConnectionState.ENDED) state
                else state.copy(
                    connection = XSpaceConnectionState.RECONNECTING,
                    playback = XSpacePlaybackMode.BUFFERING,
                )
            }
            XSpaceLivePlayerEvent.Recovered -> {
                if (state.connection == XSpaceConnectionState.ENDED) state
                else state.copy(
                    connection = XSpaceConnectionState.CONNECTED,
                    playback = if (state.liveLagMs < 0L) {
                        XSpacePlaybackMode.BEHIND_LIVE
                    } else {
                        XSpacePlaybackMode.LIVE
                    },
                    liveControlActive = true,
                )
            }
            is XSpaceLivePlayerEvent.Error -> {
                if (state.connection == XSpaceConnectionState.ENDED) state
                else state.copy(connection = XSpaceConnectionState.ERROR)
            }
            XSpaceLivePlayerEvent.Pause -> when {
                state.connection == XSpaceConnectionState.ENDED ||
                    state.playback == XSpacePlaybackMode.REPLAY -> state.copy(
                    playback = XSpacePlaybackMode.PAUSED,
                    liveControlActive = false,
                )
                state.playback == XSpacePlaybackMode.LIVE ||
                    state.playback == XSpacePlaybackMode.BUFFERING &&
                    state.liveControlActive -> state.copy(
                    playback = XSpacePlaybackMode.BEHIND_LIVE,
                    liveLagMs = state.liveLagMs.coerceAtMost(0L),
                    liveControlActive = true,
                )
                state.playback == XSpacePlaybackMode.BEHIND_LIVE -> state.copy(
                    playback = XSpacePlaybackMode.BEHIND_LIVE,
                    liveControlActive = true,
                )
                else -> state.copy(playback = XSpacePlaybackMode.PAUSED)
            }
            XSpaceLivePlayerEvent.Resume -> when {
                state.connection == XSpaceConnectionState.ENDED -> state.copy(
                    playback = XSpacePlaybackMode.REPLAY,
                    liveControlActive = false,
                )
                state.playback == XSpacePlaybackMode.BEHIND_LIVE -> state.copy(
                    connection = XSpaceConnectionState.CONNECTED,
                    playback = XSpacePlaybackMode.BEHIND_LIVE,
                    liveControlActive = true,
                )
                else -> state.copy(
                    connection = XSpaceConnectionState.CONNECTED,
                    playback = XSpacePlaybackMode.LIVE,
                    liveLagMs = 0L,
                    liveControlActive = true,
                )
            }
            is XSpaceLivePlayerEvent.LagSample -> {
                if (state.connection == XSpaceConnectionState.ENDED ||
                    state.playback == XSpacePlaybackMode.REPLAY
                ) {
                    state
                } else if (LiveLagMath.behindLive(event.lagMs)) {
                    state.copy(
                        playback = XSpacePlaybackMode.BEHIND_LIVE,
                        liveLagMs = event.lagMs,
                        liveControlActive = true,
                    )
                } else if (state.playback == XSpacePlaybackMode.BEHIND_LIVE &&
                    !LiveLagMath.behindLive(event.lagMs)
                ) {
                    state.copy(
                        playback = XSpacePlaybackMode.LIVE,
                        liveLagMs = 0L,
                        liveControlActive = true,
                    )
                } else {
                    state.copy(liveLagMs = event.lagMs.coerceAtMost(0L))
                }
            }
            XSpaceLivePlayerEvent.JumpToLiveEdge -> {
                if (state.connection == XSpaceConnectionState.ENDED ||
                    state.playback == XSpacePlaybackMode.REPLAY
                ) {
                    state.copy(liveControlActive = false)
                } else {
                    state.copy(
                        connection = XSpaceConnectionState.CONNECTED,
                        playback = XSpacePlaybackMode.LIVE,
                        liveLagMs = 0L,
                        liveControlActive = true,
                    )
                }
            }
            XSpaceLivePlayerEvent.Buffering -> {
                if (state.connection == XSpaceConnectionState.ENDED ||
                    state.playback == XSpacePlaybackMode.REPLAY
                ) {
                    // Replay stays REPLAY while the engine prepares HLS; do not look live/buffering-only.
                    state.copy(playback = XSpacePlaybackMode.REPLAY, liveControlActive = false)
                } else {
                    state.copy(playback = XSpacePlaybackMode.BUFFERING)
                }
            }
            XSpaceLivePlayerEvent.IngestEnded -> state.copy(
                connection = XSpaceConnectionState.ENDED,
                playback = XSpacePlaybackMode.PAUSED,
                liveControlActive = false,
                liveLagMs = 0L,
                // Same session: ingest end must not remount the player.
                sessionGeneration = state.sessionGeneration,
            )
            is XSpaceLivePlayerEvent.StartReplay -> state.copy(
                connection = XSpaceConnectionState.ENDED,
                playback = XSpacePlaybackMode.REPLAY,
                liveControlActive = false,
                liveSeekAllowed = false,
                replaySeekAllowed = event.seekAllowed,
                sessionGeneration = state.sessionGeneration,
            )
        }
    }
}
