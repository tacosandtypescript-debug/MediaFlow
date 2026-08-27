package com.mediaflow.app.ui.player.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaflow.domain.player.EnginePlaybackState

/**
 * Primary central playback action button with scale micro-interactions and smooth icon transitions.
 */
@Composable
fun PlayPauseButton(
    playbackState: EnginePlaybackState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    iconSize: Dp = 38.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "playPauseScale",
    )

    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Crossfade(
                targetState = playbackState,
                animationSpec = tween(durationMillis = 180),
                label = "playPauseIconTransition",
            ) { state ->
                val icon = when (state) {
                    EnginePlaybackState.PLAYING -> Icons.Outlined.Pause
                    EnginePlaybackState.ENDED -> Icons.Outlined.Replay
                    else -> Icons.Outlined.PlayArrow
                }
                val contentDesc = when (state) {
                    EnginePlaybackState.PLAYING -> "Pausa"
                    EnginePlaybackState.ENDED -> "Reiniciar"
                    else -> "Reproducir"
                }

                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
