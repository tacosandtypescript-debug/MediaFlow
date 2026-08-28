package com.mediaflow.app.ui.player.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Forward10
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
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("playback_controls"),
    ) {
        // Previous Track
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious || !isLive,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Pista anterior",
                tint = if (hasPrevious || !isLive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(32.dp),
            )
        }

        // Rewind 10s (only if not live)
        if (!isLive) {
            IconButton(
                onClick = onRewind10,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Retroceder 10 segundos",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Dominant Play / Pause Button
        val isPlaying = playbackState == EnginePlaybackState.PLAYING
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
            Crossfade(targetState = isPlaying, label = "play_pause_crossfade") { playing ->
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pausar" else "Reproducir",
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // Forward 10s (only if not live)
        if (!isLive) {
            IconButton(
                onClick = onForward10,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Adelantar 10 segundos",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Next Track
        IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Siguiente pista",
                tint = if (hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
