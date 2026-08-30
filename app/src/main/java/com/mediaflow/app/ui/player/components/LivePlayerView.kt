package com.mediaflow.app.ui.player.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mediaflow.app.ui.player.SpaceRecordingUi
import com.mediaflow.app.ui.player.xspace.XSpaceLivePlayerView
import com.mediaflow.core.model.XSpace
import com.mediaflow.data.provider.x.spaces.XSpaceCapabilities
import com.mediaflow.domain.live.LiveSpaceEndState
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.xspace.XSpaceConnectionState
import com.mediaflow.domain.player.xspace.XSpaceLivePlayerState
import com.mediaflow.domain.player.xspace.XSpacePlaybackMode

/**
 * Compatibility entry into the dedicated X Space player path.
 */
@Composable
fun LivePlayerView(
    space: XSpace?,
    playbackState: EnginePlaybackState,
    liveEndState: LiveSpaceEndState,
    isAutoDownloadEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleAutoDownload: () -> Unit,
    onDownloadReplay: (String) -> Unit,
    onCheckReplayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    isBroadcastLive: Boolean = space?.isLive == true && liveEndState is LiveSpaceEndState.ActiveLive,
    isError: Boolean = false,
    errorMessage: String? = null,
    artworkUrl: String? = null,
    spacePlayerState: XSpaceLivePlayerState? = null,
    capabilities: XSpaceCapabilities? = space?.let { XSpaceCapabilities.from(it) },
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    onJumpToLiveEdge: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    recording: SpaceRecordingUi = SpaceRecordingUi(),
    onToggleRecord: () -> Unit = {},
    onMarkRecording: () -> Unit = {},
) {
    val playerState = spacePlayerState ?: inferredState(
        space = space,
        playbackState = playbackState,
        liveEndState = liveEndState,
        isBroadcastLive = isBroadcastLive,
        capabilities = capabilities,
    )
    XSpaceLivePlayerView(
        space = space,
        capabilities = capabilities,
        playerState = playerState,
        enginePlaybackState = playbackState,
        liveEndState = liveEndState,
        isAutoDownloadEnabled = isAutoDownloadEnabled,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        onTogglePlayPause = onTogglePlayPause,
        onJumpToLiveEdge = onJumpToLiveEdge,
        onSeekTo = onSeekTo,
        onToggleAutoDownload = onToggleAutoDownload,
        onDownloadReplay = onDownloadReplay,
        onCheckReplayAgain = onCheckReplayAgain,
        recording = recording,
        onToggleRecord = onToggleRecord,
        onMarkRecording = onMarkRecording,
        modifier = modifier,
        isError = isError,
        errorMessage = errorMessage,
        artworkUrl = artworkUrl,
    )
}

private fun inferredState(
    space: XSpace?,
    playbackState: EnginePlaybackState,
    liveEndState: LiveSpaceEndState,
    isBroadcastLive: Boolean,
    capabilities: XSpaceCapabilities?,
): XSpaceLivePlayerState {
    val ended = liveEndState !is LiveSpaceEndState.ActiveLive || space?.isEnded == true
    val seekReplay = capabilities?.stream?.seekSupported == true
    return when {
        ended && space?.audioStreamUrl != null && space.isEnded -> XSpaceLivePlayerState(
            connection = XSpaceConnectionState.ENDED,
            playback = XSpacePlaybackMode.REPLAY,
            liveControlActive = false,
            replaySeekAllowed = seekReplay,
        )
        ended -> XSpaceLivePlayerState(
            connection = XSpaceConnectionState.ENDED,
            playback = XSpacePlaybackMode.PAUSED,
            liveControlActive = false,
        )
        playbackState == EnginePlaybackState.PREPARING -> XSpaceLivePlayerState(
            connection = XSpaceConnectionState.CONNECTING,
            playback = XSpacePlaybackMode.BUFFERING,
            liveControlActive = true,
            liveSeekAllowed = capabilities?.liveSeekAllowed == true,
        )
        playbackState == EnginePlaybackState.PAUSED && isBroadcastLive -> XSpaceLivePlayerState(
            connection = XSpaceConnectionState.CONNECTED,
            playback = XSpacePlaybackMode.BEHIND_LIVE,
            liveLagMs = 0L,
            liveControlActive = true,
            liveSeekAllowed = capabilities?.liveSeekAllowed == true,
        )
        playbackState == EnginePlaybackState.ERROR -> XSpaceLivePlayerState(
            connection = XSpaceConnectionState.ERROR,
            playback = XSpacePlaybackMode.PAUSED,
            liveControlActive = true,
        )
        else -> XSpaceLivePlayerState(
            connection = XSpaceConnectionState.CONNECTED,
            playback = XSpacePlaybackMode.LIVE,
            liveControlActive = true,
            liveSeekAllowed = capabilities?.liveSeekAllowed == true,
        )
    }
}
