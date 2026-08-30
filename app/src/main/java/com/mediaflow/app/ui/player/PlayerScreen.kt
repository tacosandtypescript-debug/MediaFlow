package com.mediaflow.app.ui.player

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.MediaShare
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaflow.app.ui.common.media.DeleteMediaDialog
import com.mediaflow.app.ui.player.components.AudioNowPlaying
import com.mediaflow.app.ui.player.components.BufferingIndicator
import com.mediaflow.app.ui.player.components.LivePlayerView
import com.mediaflow.app.ui.player.components.PlaybackControls
import com.mediaflow.app.ui.player.components.PlayerHeaderContext
import com.mediaflow.app.ui.player.components.PlayerMetadataSection
import com.mediaflow.app.ui.player.components.PlayerSecondaryActions
import com.mediaflow.app.ui.player.components.PlayerSurface
import com.mediaflow.app.ui.player.components.PlayerTimeline
import com.mediaflow.app.ui.player.components.SeekFeedback
import com.mediaflow.app.ui.player.gestures.playerGestures
import com.mediaflow.app.ui.playlists.components.AddToPlaylistSheet
import com.mediaflow.app.ui.queue.PlayerQueueSheet

/**
 * Modern, decoupled native multimedia player powered by libmpv and Material 3 Jetpack Compose.
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

    var showQueueSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mediaUri) {
        if (mediaUri.isNotBlank()) {
            viewModel.open(mediaUri)
        }
    }

    // Handle full screen system bar insets for videos
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (uiState.isFullscreen) PaddingValues(0.dp) else innerPadding)
                .playerGestures(
                    onSingleTap = viewModel::toggleControlsVisibility,
                    onDoubleTapLeft = {
                        if (!uiState.isLiveSession) viewModel.seekRelative(-10_000L)
                    },
                    onDoubleTapRight = {
                        if (!uiState.isLiveSession) viewModel.seekRelative(10_000L)
                    },
                    onDoubleTapCenter = viewModel::togglePlayPause,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.isLiveSession) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    PlayerHeaderContext(
                        contextText = stringResource(R.string.space_default_title),
                        isLive = uiState.isBroadcastLive,
                        isLiveSession = true,
                        onBack = onBack,
                    )

                    LivePlayerView(
                        space = uiState.spaceMetadata,
                        playbackState = uiState.serviceState.playbackState,
                        liveEndState = uiState.liveEndState,
                        isAutoDownloadEnabled = uiState.isAutoDownloadEnabled,
                        isBroadcastLive = uiState.isBroadcastLive,
                        isError = uiState.isError,
                        errorMessage = uiState.errorMessage,
                        artworkUrl = preferredArtworkUrl(
                            uiState.serviceState.artworkUrl,
                            uiState.spaceMetadata?.host?.avatarUrl,
                        ),
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onToggleAutoDownload = viewModel::toggleAutoDownload,
                        onDownloadReplay = viewModel::downloadSpaceReplay,
                        onCheckReplayAgain = viewModel::checkReplayAgain,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(Modifier.height(16.dp))
                }
            } else if (uiState.isAudioOnly) {
                val vizVm: com.mediaflow.app.ui.player.visualizer.settings.VisualizerSettingsViewModel =
                    viewModel(
                        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
                            context.applicationContext as android.app.Application,
                        ),
                    )
                val viz by vizVm.settings.collectAsState()
                val artwork = preferredArtworkUrl(
                    uiState.artworkUri,
                    uiState.serviceState.artworkUrl,
                )
                val palette = com.mediaflow.app.ui.player.palette.rememberPlayerPalette(artwork)
                val hostActivity = context as? Activity
                DisposableEffect(palette.background, uiState.isAudioOnly) {
                    val window = hostActivity?.window
                    if (window != null) {
                        com.mediaflow.app.ui.player.palette.SystemBarColorMapper.apply(
                            window,
                            com.mediaflow.app.ui.player.palette.SystemBarColorMapper.fromPalette(palette),
                        )
                    }
                    onDispose {
                        if (window != null) {
                            com.mediaflow.app.ui.player.palette.SystemBarColorMapper.apply(
                                window,
                                com.mediaflow.app.ui.player.palette.SystemBarColorMapper.restoreScheme(),
                            )
                        }
                    }
                }
                AudioNowPlaying(
                    title = displayTitle,
                    artist = uiState.artist
                        ?: uiState.serviceState.artistOrHost
                        ?: stringResource(R.string.player_media_audio),
                    album = uiState.album
                        ?: uiState.spaceMetadata?.let { "X SPACE" }
                        ?: uiState.playbackContext?.takeIf { it.isNotBlank() },
                    artworkUrl = preferredArtworkUrl(
                        uiState.artworkUri,
                        uiState.serviceState.artworkUrl,
                    ),
                    space = uiState.spaceMetadata,
                    isFavorite = uiState.isFavorite,
                    playbackState = uiState.serviceState.playbackState,
                    currentPositionMs = uiState.currentPositionMs,
                    durationMs = uiState.durationMs,
                    speed = uiState.speed,
                    queueCount = uiState.queue.size,
                    isBuffering = uiState.isBuffering,
                    onBack = onBack,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onSeekTo = viewModel::seekTo,
                    onScrubbingChanged = { viewModel.setScrubbing(it) },
                    onScrubPositionChange = viewModel::updateScrubPosition,
                    onPlayPause = viewModel::togglePlayPause,
                    onPrevious = viewModel::playPrevious,
                    onNext = viewModel::playNext,
                    onSpeedChange = viewModel::setSpeed,
                    onAddToPlaylist = { showAddToPlaylistSheet = true },
                    onOpenQueue = { showQueueSheet = true },
                    onShare = {
                        val path = uiState.serviceState.filePath ?: uiState.mediaUri
                        MediaShare.share(
                            context = context,
                            uriString = path,
                            title = displayTitle,
                            isAudio = true,
                        )
                    },
                    onDelete = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxSize(),
                    visualizerSettings = viz,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    PlayerHeaderContext(
                        contextText = uiState.playbackContext ?: "Reproduciendo",
                        isLive = false,
                        onBack = onBack,
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayerSurface(
                            onSurfaceCreated = viewModel::onSurfaceAvailable,
                            onSurfaceDestroyed = viewModel::onSurfaceDestroyed,
                            isFullscreen = uiState.isFullscreen,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    PlayerMetadataSection(
                        title = displayTitle,
                        subtitle = uiState.artist
                            ?: uiState.serviceState.artistOrHost
                            ?: stringResource(R.string.player_media_video),
                        album = uiState.album,
                        isFavorite = uiState.isFavorite,
                        onToggleFavorite = viewModel::toggleFavorite,
                    )

                    PlayerTimeline(
                        currentPositionMs = uiState.currentPositionMs,
                        durationMs = uiState.durationMs,
                        onSeekTo = viewModel::seekTo,
                        onScrubbingChanged = { scrubbing ->
                            viewModel.setScrubbing(scrubbing)
                        },
                        onScrubPositionChange = viewModel::updateScrubPosition,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )

                    PlaybackControls(
                        playbackState = uiState.serviceState.playbackState,
                        isPlaying = uiState.isPlaying,
                        isBuffering = uiState.isBuffering,
                        hasNext = uiState.hasNext,
                        hasPrevious = uiState.hasPrevious,
                        isLive = false,
                        onPlayPause = viewModel::togglePlayPause,
                        onPrevious = viewModel::playPrevious,
                        onNext = viewModel::playNext,
                        onRewind10 = { viewModel.seekRelative(-10_000L) },
                        onForward10 = { viewModel.seekRelative(10_000L) },
                    )

                    PlayerSecondaryActions(
                        speed = uiState.speed,
                        queueCount = uiState.queue.size,
                        isLive = false,
                        onSpeedChange = viewModel::setSpeed,
                        onAddToPlaylist = { showAddToPlaylistSheet = true },
                        onOpenQueue = { showQueueSheet = true },
                        onShare = {
                            val path = uiState.serviceState.filePath ?: uiState.mediaUri
                            MediaShare.share(
                                context = context,
                                uriString = path,
                                title = displayTitle,
                                isAudio = false,
                            )
                        },
                        onDelete = { showDeleteDialog = true },
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }

            BufferingIndicator(
                visible = uiState.isBuffering && !uiState.isLiveSession && !uiState.isAudioOnly,
                modifier = Modifier.align(Alignment.Center),
                label = stringResource(R.string.player_buffering),
            )

            // Double-tap ±10s Seek Feedback Overlay
            SeekFeedback(
                event = uiState.seekFeedback,
                modifier = Modifier.fillMaxSize(),
            )

            // Error Display Card
            AnimatedVisibility(
                visible = uiState.isError && !uiState.isLiveSession,
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
                            text = stringResource(R.string.player_error_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = uiState.errorMessage ?: stringResource(R.string.player_error_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Button(
                            onClick = onBack,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .height(52.dp),
                        ) {
                            Text(stringResource(R.string.back))
                        }
                    }
                }
            }
        }
    }

    // Queue ModalBottomSheet
    if (showQueueSheet) {
        PlayerQueueSheet(
            queue = uiState.queue,
            currentIndex = uiState.queueIndex,
            isPlaying = uiState.isPlaying,
            onSkipToIndex = { index -> viewModel.skipToIndex(index) },
            onRemoveFromQueue = { index -> viewModel.removeFromQueue(index) },
            onDismiss = { showQueueSheet = false },
        )
    }

    // Add To Playlist ModalBottomSheet
    if (showAddToPlaylistSheet) {
        val uri = uiState.mediaUri
        AddToPlaylistSheet(
            targetMediaUri = uri,
            playlists = uiState.playlists,
            onToggleMediaInPlaylist = { playlistId, isIn ->
                viewModel.toggleMediaInPlaylist(playlistId, isIn)
            },
            onCreateNewPlaylist = { name ->
                viewModel.createPlaylist(name)
            },
            onDismiss = { showAddToPlaylistSheet = false },
        )
    }

    // Delete Media Dialog
    if (showDeleteDialog) {
        DeleteMediaDialog(
            title = displayTitle,
            artworkUrl = preferredArtworkUrl(
                uiState.serviceState.artworkUrl,
                uiState.spaceMetadata?.host?.avatarUrl,
            ),
            onConfirm = {
                viewModel.deleteCurrentMedia(onDeleted = onBack)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
