package com.mediaflow.app.ui.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.player.palette.rememberPlayerPalette
import com.mediaflow.app.ui.player.visualizer.VisualizerLayer
import com.mediaflow.app.ui.player.visualizer.settings.VisualizerSettings
import com.mediaflow.app.ui.player.controls.AudioPrimaryControls
import com.mediaflow.app.ui.player.gestures.ArtworkSwipePager
import com.mediaflow.core.model.XSpace
import com.mediaflow.domain.player.EnginePlaybackState

/**
 * Spotify-style audio Now Playing: compact chrome, hero cover, title/heart, seek, transport.
 */
@Composable
fun AudioNowPlaying(
    title: String,
    artist: String,
    album: String?,
    artworkUrl: String?,
    space: XSpace?,
    isFavorite: Boolean,
    playbackState: EnginePlaybackState,
    currentPositionMs: Long,
    durationMs: Long,
    speed: Float,
    queueCount: Int,
    isBuffering: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onScrubPositionChange: (Long) -> Unit = {},
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    visualizerSettings: VisualizerSettings = VisualizerSettings(),
) {
    val isPlaying = playbackState == EnginePlaybackState.PLAYING
    val palette = rememberPlayerPalette(artworkUrl)

    Box(modifier = modifier.fillMaxSize()) {
        VisualizerLayer(
            settings = visualizerSettings,
            palette = palette,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize(),
        )
        Column(modifier = Modifier.fillMaxSize()) {
        AudioNowPlayingTopBar(
            onBack = onBack,
            onShare = onShare,
        )

        ArtworkSwipePager(
            onNext = onNext,
            onPrevious = onPrevious,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            AudioPlayerView(
                title = title,
                isPlaying = playbackState == EnginePlaybackState.PLAYING,
                space = space,
                artworkUrl = artworkUrl,
                heroCover = true,
                showDetails = false,
                modifier = Modifier.fillMaxSize(),
            )
        }

        PlayerMetadataSection(
            title = title,
            subtitle = artist,
            album = album,
            isSpace = space != null,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            nowPlaying = true,
        )

        PlayerTimeline(
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            onSeekTo = onSeekTo,
            onScrubbingChanged = onScrubbingChanged,
            onScrubPositionChange = onScrubPositionChange,
            nowPlaying = true,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        AudioPrimaryControls(
            playbackState = playbackState,
            isPlaying = playbackState == EnginePlaybackState.PLAYING,
            isBuffering = isBuffering,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
        )

        PlayerSecondaryActions(
            speed = speed,
            queueCount = queueCount,
            isLive = false,
            onSpeedChange = onSpeedChange,
            onAddToPlaylist = onAddToPlaylist,
            onOpenQueue = onOpenQueue,
            onShare = onShare,
            onDelete = onDelete,
            nowPlaying = true,
        )

        Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AudioNowPlayingTopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("player_header_back_btn"),
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = onShare,
            modifier = Modifier
                .size(48.dp)
                .testTag("player_share_btn"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = stringResource(R.string.share),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
