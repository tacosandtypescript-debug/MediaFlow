package com.mediaflow.app.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.player.live.AutoDownloadToggle
import com.mediaflow.app.ui.player.live.LiveEndedContent
import com.mediaflow.core.model.XSpace
import com.mediaflow.domain.live.LiveSpaceEndState
import com.mediaflow.domain.player.EnginePlaybackState

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
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag("live_player_view"),
    ) {
        // Space Live Status Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            LiveStatusBadge()

            space?.let {
                if (it.liveListenersCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = " ${it.liveListenersCount} oyentes",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Center Rotating Vinyl / Avatar
        AudioPlayerView(
            title = space?.title ?: "X Space en vivo",
            space = space,
            subtitle = space?.host?.formattedHandle?.let { "Host: $it" },
            isPlaying = playbackState == EnginePlaybackState.PLAYING,
            modifier = Modifier.fillMaxWidth(0.85f),
        )

        Spacer(Modifier.height(18.dp))

        // Live Controls and Auto-Download Switch
        if (liveEndState is LiveSpaceEndState.ActiveLive) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Dominant Play/Pause
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

                // Auto-Download Toggle
                AutoDownloadToggle(
                    enabled = isAutoDownloadEnabled,
                    onToggle = { onToggleAutoDownload() },
                )

                if (isAutoDownloadEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "✓ Se descargará al finalizar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            // Space Finished Overlay Card
            LiveEndedContent(
                endState = liveEndState,
                onDownloadReplay = onDownloadReplay,
                onCheckReplayAgain = onCheckReplayAgain,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
