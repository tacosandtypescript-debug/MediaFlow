package com.mediaflow.app.ui.player.xspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.player.components.AudioPlayerView
import com.mediaflow.app.ui.player.components.LiveStatusBadge
import com.mediaflow.app.ui.player.components.PlaybackControls
import com.mediaflow.app.ui.player.components.PlayerTimeline
import com.mediaflow.app.ui.player.live.AutoDownloadToggle
import com.mediaflow.app.ui.player.live.LiveEndedContent
import com.mediaflow.app.ui.player.SpaceRecordingUi
import com.mediaflow.app.ui.theme.customColors
import com.mediaflow.core.model.XSpace
import com.mediaflow.data.provider.x.recording.RecordingPhase
import com.mediaflow.data.provider.x.spaces.XSpaceCapabilities
import com.mediaflow.data.provider.x.spaces.XSpaceFieldAvailability
import com.mediaflow.domain.live.LiveSpaceEndState
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.xspace.XSpaceConnectionState
import com.mediaflow.domain.player.xspace.XSpaceLivePlayerState
import com.mediaflow.domain.player.xspace.LiveLagMath
import com.mediaflow.domain.player.xspace.XSpacePlaybackMode
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Dedicated X Space player. Not the audio/video Now Playing path.
 * Renders only fields confirmed by [XSpaceCapabilities].
 */
@Composable
fun XSpaceLivePlayerView(
    space: XSpace?,
    capabilities: XSpaceCapabilities?,
    playerState: XSpaceLivePlayerState,
    enginePlaybackState: EnginePlaybackState,
    liveEndState: LiveSpaceEndState,
    isAutoDownloadEnabled: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onTogglePlayPause: () -> Unit,
    onJumpToLiveEdge: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleAutoDownload: () -> Unit,
    onDownloadReplay: (String) -> Unit,
    onCheckReplayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    artworkUrl: String? = null,
    recording: SpaceRecordingUi = SpaceRecordingUi(),
    onToggleRecord: () -> Unit = {},
    onMarkRecording: () -> Unit = {},
) {
    val isBroadcastLive = playerState.playback == XSpacePlaybackMode.LIVE ||
        playerState.playback == XSpacePlaybackMode.BEHIND_LIVE ||
        (playerState.liveControlActive && playerState.connection != XSpaceConnectionState.ENDED)
    val showTitle = fieldShown(capabilities, XSpaceCapabilities.FIELD_TITLE) &&
        !space?.title.isNullOrBlank()
    val showHost = fieldShown(capabilities, XSpaceCapabilities.FIELD_HOST)
    val showListeners = capabilities?.availability(XSpaceCapabilities.FIELD_LIVE_LISTENERS) ==
        XSpaceFieldAvailability.DYNAMIC &&
        (space?.liveListenersCount ?: 0) > 0
    val showDuration = fieldShown(capabilities, XSpaceCapabilities.FIELD_DURATION) &&
        (space?.durationSeconds ?: 0L) > 0L
    val seekAllowed = when (playerState.playback) {
        XSpacePlaybackMode.REPLAY, XSpacePlaybackMode.PAUSED ->
            playerState.replaySeekAllowed &&
                fieldShown(capabilities, XSpaceCapabilities.FIELD_SEEK)
        else -> playerState.liveSeekAllowed
    }
    val liveButtonActive = playerState.liveControlActive &&
        playerState.playback == XSpacePlaybackMode.BEHIND_LIVE
    val showEndedOverlay = playerState.connection == XSpaceConnectionState.ENDED &&
        playerState.playback != XSpacePlaybackMode.REPLAY &&
        liveEndState !is LiveSpaceEndState.ActiveLive
    val isReplay = playerState.playback == XSpacePlaybackMode.REPLAY

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .testTag("live_player_view"),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                if (isBroadcastLive && !isReplay) MaterialTheme.customColors.live
                else MaterialTheme.customColors.outlineSoft,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .background(
                            if (isBroadcastLive && !isReplay) MaterialTheme.customColors.live
                            else MaterialTheme.colorScheme.outline,
                        )
                        .height(56.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    LiveStatusBadge(
                        isLive = isBroadcastLive && !isReplay,
                        liveListenersCount = if (showListeners) space?.liveListenersCount ?: 0 else 0,
                    )
                    ConnectionChip(playerState.connection)
                    PlaybackChip(playerState.playback)
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))

            AudioPlayerView(
                title = if (showTitle) {
                    space?.title ?: stringResource(R.string.space_default_title)
                } else {
                    stringResource(R.string.space_default_title)
                },
                space = space,
                subtitle = if (showHost) {
                    space?.host?.formattedHandle?.let { stringResource(R.string.space_host_format, it) }
                } else {
                    null
                },
                isPlaying = enginePlaybackState == EnginePlaybackState.PLAYING,
                artworkUrl = artworkUrl,
                modifier = Modifier.fillMaxWidth(0.85f),
            )

            val startedAt = space?.startedAtMs?.takeIf { it > 0L }
            if (playerState.liveControlActive && startedAt != null) {
                ElapsedOnAirLabel(startedAtMs = startedAt)
            }

            if (showDuration) {
                Text(
                    text = space?.formattedDuration.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (playerState.playback == XSpacePlaybackMode.BEHIND_LIVE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("xspace_behind_live"),
                ) {
                    Text(
                        text = stringResource(R.string.space_live_badge),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.customColors.live,
                    )
                    Text(
                        text = LiveLagMath.format(playerState.liveLagMs),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("xspace_live_lag"),
                    )
                }
            }

            XSpaceConnectionStatusLabel(
                connection = playerState.connection,
                playback = playerState.playback,
                isError = isError,
                errorMessage = errorMessage,
            )

            Spacer(Modifier.height(12.dp))
        }

        if (showEndedOverlay) {
            if (recording.phase == RecordingPhase.SAVED || recording.savedPath != null) {
                Text(
                    text = stringResource(R.string.space_record_saved),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag("xspace_record_saved"),
                )
            }
            LiveEndedContent(
                endState = liveEndState,
                onDownloadReplay = onDownloadReplay,
                onCheckReplayAgain = onCheckReplayAgain,
                isAutoDownloadEnabled = isAutoDownloadEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (seekAllowed && durationMs > 0L) {
                    PlayerTimeline(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        onSeekTo = onSeekTo,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                PlaybackControls(
                    playbackState = enginePlaybackState,
                    hasNext = false,
                    hasPrevious = false,
                    isLive = playerState.liveControlActive,
                    onPlayPause = onTogglePlayPause,
                    onPrevious = {},
                    onNext = {},
                    onRewind10 = {},
                    onForward10 = {},
                )

                if (liveButtonActive) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onJumpToLiveEdge,
                        modifier = Modifier.testTag("xspace_jump_live"),
                    ) {
                        Text(stringResource(R.string.space_jump_live))
                    }
                }

                if (playerState.playback == XSpacePlaybackMode.REPLAY) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.space_replay_mode),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("xspace_replay_mode"),
                    )
                }

                if (playerState.liveControlActive) {
                    Spacer(Modifier.height(12.dp))
                    SpaceRecordControls(
                        recording = recording,
                        onToggleRecord = onToggleRecord,
                        onMark = onMarkRecording,
                    )
                    Spacer(Modifier.height(8.dp))
                    AutoDownloadToggle(
                        enabled = isAutoDownloadEnabled,
                        onToggle = { onToggleAutoDownload() },
                    )
                    if (isAutoDownloadEnabled) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.space_auto_download_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceRecordControls(
    recording: SpaceRecordingUi,
    onToggleRecord: () -> Unit,
    onMark: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onToggleRecord,
                modifier = Modifier.testTag("xspace_record_toggle"),
            ) {
                Text(
                    if (recording.recordEnabled) {
                        stringResource(R.string.space_record_on)
                    } else {
                        stringResource(R.string.space_record_off)
                    },
                )
            }
            TextButton(
                onClick = onMark,
                enabled = recording.recordEnabled,
                modifier = Modifier.testTag("xspace_record_mark"),
            ) {
                Text(stringResource(R.string.space_record_mark))
            }
        }
        if (recording.recordEnabled) {
            Text(
                text = stringResource(R.string.space_record_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("xspace_record_hint"),
            )
        }
        if (recording.phase == RecordingPhase.SAVED) {
            Text(
                text = stringResource(R.string.space_record_saved),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("xspace_record_saved"),
            )
        }
    }
}

private fun fieldShown(caps: XSpaceCapabilities?, field: String): Boolean {
    val availability = caps?.availability(field) ?: return true
    return availability == XSpaceFieldAvailability.AVAILABLE ||
        availability == XSpaceFieldAvailability.DYNAMIC ||
        availability == XSpaceFieldAvailability.APPROXIMATE
}

@Composable
private fun ConnectionChip(connection: XSpaceConnectionState) {
    val label = when (connection) {
        XSpaceConnectionState.CONNECTING -> stringResource(R.string.space_connection_connecting)
        XSpaceConnectionState.CONNECTED -> stringResource(R.string.space_connection_connected)
        XSpaceConnectionState.RECONNECTING -> stringResource(R.string.player_reconnecting)
        XSpaceConnectionState.ENDED -> stringResource(R.string.space_ended_badge)
        XSpaceConnectionState.ERROR -> stringResource(R.string.player_error_title)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("xspace_connection"),
    )
}

@Composable
private fun PlaybackChip(playback: XSpacePlaybackMode) {
    val label = when (playback) {
        XSpacePlaybackMode.LIVE -> stringResource(R.string.space_live_badge)
        XSpacePlaybackMode.BEHIND_LIVE -> stringResource(R.string.space_behind_live)
        XSpacePlaybackMode.PAUSED -> stringResource(R.string.space_playback_paused)
        XSpacePlaybackMode.BUFFERING -> stringResource(R.string.player_buffering)
        XSpacePlaybackMode.REPLAY -> stringResource(R.string.space_replay_mode)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.testTag("xspace_playback_mode"),
    )
}

@Composable
private fun XSpaceConnectionStatusLabel(
    connection: XSpaceConnectionState,
    playback: XSpacePlaybackMode,
    isError: Boolean,
    errorMessage: String?,
) {
    val label = when {
        playback == XSpacePlaybackMode.BUFFERING -> stringResource(R.string.player_buffering)
        connection == XSpaceConnectionState.CONNECTING -> stringResource(R.string.space_connection_connecting)
        connection == XSpaceConnectionState.RECONNECTING -> stringResource(R.string.player_reconnecting)
        connection == XSpaceConnectionState.ERROR || isError ->
            errorMessage ?: stringResource(R.string.player_error_title)
        else -> null
    } ?: return
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ElapsedOnAirLabel(startedAtMs: Long) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed = (nowMs - startedAtMs).coerceAtLeast(0L)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.space_on_air),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatElapsed(elapsed),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1_000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
