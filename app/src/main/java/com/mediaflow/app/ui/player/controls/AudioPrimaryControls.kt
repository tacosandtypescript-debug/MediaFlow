package com.mediaflow.app.ui.player.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.player.components.PlayPauseButton
import com.mediaflow.domain.player.EnginePlaybackState

/**
 * Audio Now Playing transport: previous / play-pause / next (not ±10s seek).
 */
@Composable
fun AudioPrimaryControls(
    playbackState: EnginePlaybackState,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
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
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(56.dp)
                .testTag("player_skip_back"),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Pista anterior",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
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

        IconButton(
            onClick = onNext,
            modifier = Modifier
                .size(56.dp)
                .testTag("player_skip_forward"),
            ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Siguiente pista",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
