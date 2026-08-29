package com.mediaflow.app.ui.player.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.mediaflow.app.ui.player.live.AutoDownloadToggle
import com.mediaflow.app.ui.player.live.LiveEndedContent
import com.mediaflow.app.ui.theme.customColors
import com.mediaflow.core.model.XSpace
import com.mediaflow.domain.live.LiveSpaceEndState
import com.mediaflow.domain.player.EnginePlaybackState
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Dedicated Live player composition for real-time X Spaces.
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
) {
    val isActiveLive = liveEndState is LiveSpaceEndState.ActiveLive
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
                if (isBroadcastLive) MaterialTheme.customColors.live else MaterialTheme.customColors.outlineSoft,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .background(
                            if (isBroadcastLive) MaterialTheme.customColors.live
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
                        isLive = isBroadcastLive,
                        liveListenersCount = if (isBroadcastLive) space?.liveListenersCount ?: 0 else 0,
                    )
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
                title = space?.title ?: stringResource(R.string.space_default_title),
                space = space,
                subtitle = space?.host?.formattedHandle?.let { stringResource(R.string.space_host_format, it) },
                isPlaying = playbackState == EnginePlaybackState.PLAYING,
                artworkUrl = artworkUrl,
                modifier = Modifier.fillMaxWidth(0.85f),
            )

            ElapsedOnAirLabel(startedAtMs = space?.startedAtMs?.takeIf { it > 0L })

            ConnectionStatusLabel(
                playbackState = playbackState,
                isBroadcastLive = isBroadcastLive,
                isError = isError,
                errorMessage = errorMessage,
            )

            Spacer(Modifier.height(12.dp))
        }

        if (isActiveLive) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PlaybackControls(
                    playbackState = playbackState,
                    hasNext = false,
                    hasPrevious = false,
                    isLive = true,
                    onPlayPause = onTogglePlayPause,
                    onPrevious = {},
                    onNext = {},
                    onRewind10 = {},
                    onForward10 = {},
                )

                Spacer(Modifier.height(12.dp))

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
        } else {
            LiveEndedContent(
                endState = liveEndState,
                onDownloadReplay = onDownloadReplay,
                onCheckReplayAgain = onCheckReplayAgain,
                isAutoDownloadEnabled = isAutoDownloadEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ElapsedOnAirLabel(startedAtMs: Long?) {
    if (startedAtMs == null) return
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

@Composable
private fun ConnectionStatusLabel(
    playbackState: EnginePlaybackState,
    isBroadcastLive: Boolean,
    isError: Boolean,
    errorMessage: String?,
) {
    val label = when {
        playbackState == EnginePlaybackState.PREPARING -> stringResource(R.string.player_buffering)
        isError && isBroadcastLive -> stringResource(R.string.player_reconnecting)
        isError -> errorMessage
        else -> null
    } ?: return
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
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
