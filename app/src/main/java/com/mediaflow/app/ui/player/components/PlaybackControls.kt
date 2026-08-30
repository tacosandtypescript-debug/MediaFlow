package com.mediaflow.app.ui.player.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mediaflow.domain.player.EnginePlaybackState

/**
 * Primary transport controls with dominant central Play/Pause button.
 */
@Composable
fun PlaybackControls(
    playbackState: EnginePlaybackState,
    hasNext: Boolean,
    hasPrevious: Boolean,
    isLive: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    modifier: Modifier = Modifier,
    nowPlaying: Boolean = false,
    isBuffering: Boolean = false,
    isPlaying: Boolean = playbackState == EnginePlaybackState.PLAYING,
) {
    if (nowPlaying) {
        NowPlayingTransport(
            playbackState = playbackState,
            isPlaying = isPlaying,
            isLive = isLive,
            isBuffering = isBuffering,
            onPlayPause = onPlayPause,
            onRewind10 = onRewind10,
            onForward10 = onForward10,
            modifier = modifier,
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("playback_controls"),
    ) {
        if (hasPrevious) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Pista anterior",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        if (!isLive) {
            IconButton(
                onClick = onRewind10,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("player_skip_back"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Retroceder 10 segundos",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        FilledIconButton(
            onClick = onPlayPause,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .size(72.dp)
                .testTag("dominant_play_pause_btn"),
        ) {
            if (isBuffering || playbackState == EnginePlaybackState.PREPARING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Crossfade(
                    targetState = isPlaying,
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "play_pause_crossfade",
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pausar" else "Reproducir",
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        if (!isLive) {
            IconButton(
                onClick = onForward10,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("player_skip_forward"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Adelantar 10 segundos",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        if (hasNext) {
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Siguiente pista",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingTransport(
    playbackState: EnginePlaybackState,
    isPlaying: Boolean,
    isLive: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("playback_controls"),
    ) {
        if (!isLive) {
            IconButton(
                onClick = onRewind10,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("player_skip_back"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Retroceder 10 segundos",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .testTag("dominant_play_pause_btn"),
            contentAlignment = Alignment.Center,
        ) {
            PlayPauseButton(
                playbackState = playbackState,
                isPlaying = isPlaying,
                onClick = onPlayPause,
                size = 68.dp,
                iconSize = 36.dp,
                isBuffering = isBuffering,
                filled = true,
            )
        }

        if (!isLive) {
            IconButton(
                onClick = onForward10,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("player_skip_forward"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Adelantar 10 segundos",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}
