package com.mediaflow.app.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeMute
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.domain.player.EnginePlaybackState

private val AvailableSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/**
 * Modern, layered overlay controls for the native multimedia player.
 */
@Composable
fun PlayerControls(
    visible: Boolean,
    title: String,
    playbackState: EnginePlaybackState,
    currentPositionMs: Long,
    durationMs: Long,
    speed: Float,
    volume: Int,
    isMuted: Boolean,
    isFullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bufferedMs: Long = 0L,
    onScrubbingChanged: (Boolean) -> Unit = {},
    isAudioOnly: Boolean = false,
    isLive: Boolean = false,
    spaceMetadata: com.mediaflow.core.model.XSpace? = null,
) {
    var showSpeedMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(androidx.compose.animation.core.tween(180)),
        exit = fadeOut(androidx.compose.animation.core.tween(220)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.78f),
                            Color.Black.copy(alpha = 0.15f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        ) {
            // 1. Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Color.Black.copy(alpha = 0.34f),
                        RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isLive) {
                        LiveStatusBadge(
                            liveListenersCount = spaceMetadata?.liveListenersCount ?: 0,
                            isLive = isLive,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                // Restart from start (only for recorded / local files)
                if (!isLive) {
                    IconButton(onClick = onRestart) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = "Reiniciar",
                            tint = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }

                // Speed Selector Pill
                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White,
                    ) {
                        TextButton(
                            onClick = { showSpeedMenu = true },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = "${speed}x",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false },
                    ) {
                        AvailableSpeeds.forEach { speedOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${speedOption}x",
                                        fontWeight = if (speed == speedOption) FontWeight.Bold else FontWeight.Normal,
                                        color = if (speed == speedOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = {
                                    onSpeedChange(speedOption)
                                    showSpeedMenu = false
                                },
                            )
                        }
                    }
                }
            }

            // 2. Center Primary Controls
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isLive) {
                    // Seek backward 10s
                    IconButton(
                        onClick = { onSeekTo((currentPositionMs - 10_000L).coerceAtLeast(0L)) },
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FastRewind,
                            contentDescription = "Retroceder 10s",
                            tint = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }

                // Primary Play/Pause Button with scale microinteraction
                PlayPauseButton(
                    playbackState = playbackState,
                    onClick = onPlayPause,
                )

                if (!isLive) {
                    // Seek forward 10s
                    IconButton(
                        onClick = {
                            val maxPos = if (durationMs > 0L) durationMs else Long.MAX_VALUE
                            onSeekTo((currentPositionMs + 10_000L).coerceAtMost(maxPos))
                        },
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FastForward,
                            contentDescription = "Avanzar 10s",
                            tint = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }

            // 3. Bottom Control Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Color.Black.copy(alpha = 0.78f),
                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (!isLive) {
                    // Custom Timeline with decoupled scrubbing & dynamic thumb
                    PlayerTimeline(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        bufferedMs = bufferedMs,
                        onSeekTo = onSeekTo,
                        onScrubbingChanged = onScrubbingChanged,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "🔴 Transmisión de audio en directo",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Bottom Secondary Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Volume / Mute
                    IconButton(onClick = onToggleMute) {
                        val volumeIcon = when {
                            isMuted || volume == 0 -> Icons.Outlined.VolumeOff
                            volume < 35 -> Icons.Outlined.VolumeMute
                            volume < 70 -> Icons.Outlined.VolumeDown
                            else -> Icons.Outlined.VolumeUp
                        }
                        Icon(
                            imageVector = volumeIcon,
                            contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                            tint = Color.White,
                        )
                    }

                    // Fullscreen Toggle
                    if (!isAudioOnly) {
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                contentDescription = if (isFullscreen) "Salir de pantalla completa" else "Pantalla completa",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
