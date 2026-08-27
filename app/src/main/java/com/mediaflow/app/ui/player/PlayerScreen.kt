package com.mediaflow.app.ui.player

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaflow.app.ui.player.components.AudioPlayerView
import com.mediaflow.app.ui.player.components.BufferingIndicator
import com.mediaflow.app.ui.player.components.PlayerControls
import com.mediaflow.app.ui.player.components.PlayerSurface
import com.mediaflow.app.ui.player.components.SeekFeedback
import com.mediaflow.app.ui.player.gestures.playerGestures
import com.mediaflow.app.ui.player.live.AutoDownloadToggle
import com.mediaflow.app.ui.player.live.LiveEndedContent
import com.mediaflow.domain.live.LiveSpaceEndState

/**
 * Native, embedded multimedia player powered by libmpv and modern Jetpack Compose UI/UX.
 */
@Composable
fun PlayerScreen(
    mediaUri: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.Factory(
            LocalContext.current.applicationContext as android.app.Application,
        ),
    ),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val activity = context as? Activity

    LaunchedEffect(mediaUri) {
        if (mediaUri.isNotBlank()) {
            val title = mediaUri.substringAfterLast('/').ifBlank { null }
            viewModel.open(mediaUri, title)
        }
    }

    // Handle full screen system bar insets and translucent navigation
    DisposableEffect(uiState.isFullscreen) {
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (uiState.isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (activity != null) {
                val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        if (uiState.isFullscreen) {
            viewModel.toggleFullscreen()
        } else {
            onBack()
        }
    }

    val displayTitle = uiState.title.ifBlank { mediaUri.substringAfterLast('/').ifBlank { "Media" } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (uiState.isFullscreen) PaddingValues(0.dp) else innerPadding)
                .playerGestures(
                    onSingleTap = viewModel::toggleControlsVisibility,
                    onDoubleTapLeft = { viewModel.seekRelative(-10_000L) },
                    onDoubleTapRight = { viewModel.seekRelative(10_000L) },
                    onDoubleTapCenter = viewModel::togglePlayPause,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // 1. Media Surface (Audio Disc or Native Video Surface)
            if (uiState.isAudioOnly) {
                val spaceSubtitle = uiState.spaceMetadata?.let { space ->
                    val hostText = "Host: ${space.host.formattedHandle}"
                    val otherSpeakers = space.allSpeakers.filter { !it.cleanUsername.equals(space.host.cleanUsername, ignoreCase = true) }
                    if (otherSpeakers.isNotEmpty()) {
                        "$hostText · Speakers: ${otherSpeakers.joinToString(", ") { it.formattedHandle }}"
                    } else {
                        hostText
                    }
                }
                AudioPlayerView(
                    title = displayTitle,
                    space = uiState.spaceMetadata,
                    subtitle = spaceSubtitle,
                    isPlaying = uiState.isPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PlayerSurface(
                    onSurfaceCreated = viewModel::onSurfaceAvailable,
                    onSurfaceDestroyed = viewModel::onSurfaceDestroyed,
                    isFullscreen = uiState.isFullscreen,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 2. Buffering / Loading Indicator
            BufferingIndicator(
                visible = uiState.isBuffering,
                modifier = Modifier.align(Alignment.Center),
                label = if (uiState.isPreparing) "Cargando..." else "Buffering...",
            )

            // 3. Double-tap ±10s Seek Feedback Overlay
            SeekFeedback(
                event = uiState.seekFeedback,
                modifier = Modifier.fillMaxSize(),
            )

            // 4. Auto Download Toggle when listening Live
            if (uiState.isLive && uiState.liveEndState is LiveSpaceEndState.ActiveLive && uiState.isControlsVisible) {
                AutoDownloadToggle(
                    enabled = uiState.isAutoDownloadEnabled,
                    onToggle = { viewModel.toggleAutoDownload() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp),
                )
            }

            // 5. Post-Live Ended Replay Overlay Card
            if (uiState.liveEndState !is LiveSpaceEndState.ActiveLive) {
                LiveEndedContent(
                    endState = uiState.liveEndState,
                    onDownloadReplay = viewModel::downloadSpaceReplay,
                    onCheckReplayAgain = viewModel::checkReplayAgain,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // 6. Error Display Card
            AnimatedVisibility(
                visible = uiState.isError,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Error en la reproducción",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Button(
                            onClick = onBack,
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text("Volver")
                        }
                    }
                }
            }

            // 7. Player Controls Overlay
            PlayerControls(
                visible = uiState.isControlsVisible,
                title = displayTitle,
                playbackState = uiState.serviceState.playbackState,
                currentPositionMs = uiState.currentPositionMs,
                durationMs = uiState.durationMs,
                speed = uiState.speed,
                volume = uiState.volume,
                isMuted = uiState.isMuted,
                isFullscreen = uiState.isFullscreen,
                isAudioOnly = uiState.isAudioOnly,
                isLive = uiState.isLive,
                spaceMetadata = uiState.spaceMetadata,
                onPlayPause = viewModel::togglePlayPause,
                onSeekTo = viewModel::seekTo,
                onSpeedChange = viewModel::setSpeed,
                onVolumeChange = viewModel::setVolume,
                onToggleMute = viewModel::toggleMute,
                onToggleFullscreen = viewModel::toggleFullscreen,
                onRestart = viewModel::restart,
                onBack = {
                    if (uiState.isFullscreen) viewModel.toggleFullscreen() else onBack()
                },
                onScrubbingChanged = { isScrubbing ->
                    viewModel.setScrubbing(isScrubbing)
                },
            )
        }
    }
}
