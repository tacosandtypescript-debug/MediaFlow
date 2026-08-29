package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.AudioMediaRow
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.XSpace

/**
 * List view of all downloaded audio files and X Spaces.
 */
@Composable
fun AudioLibraryView(
    items: List<DownloadItem>,
    spacesMap: Map<String, XSpace>,
    progressMap: Map<String, PlaybackProgress>,
    playingMediaId: String?,
    isPlayerPlaying: Boolean,
    favoriteUris: Set<String>,
    onPlayItem: (item: DownloadItem, index: Int) -> Unit,
    onToggleFavorite: (mediaUri: String) -> Unit,
    onAddToPlaylist: (item: DownloadItem) -> Unit,
    onAddToQueue: (item: DownloadItem) -> Unit,
    onDeleteMedia: (item: DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyLibraryState(
            icon = Icons.Outlined.Audiotrack,
            title = stringResource(R.string.library_audio_empty_title),
            subtitle = stringResource(R.string.library_audio_empty_subtitle),
            actionLabel = null,
            onAction = null,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
            modifier = modifier
                .fillMaxSize()
                .testTag("audio_library_list"),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val uri = item.localUri ?: item.id
                val space = spacesMap[item.sourceUrl] ?: spacesMap[item.id]
                val progress = progressMap[uri] ?: progressMap[item.id]
                val isPlaying = playingMediaId == uri && isPlayerPlaying

                val title = space?.title ?: item.title ?: item.fileName ?: "Audio"
                val subtitle = space?.let { "Host: ${it.host.formattedHandle}" } ?: item.fileName ?: "Local"
                val artwork = preferredArtworkUrl(item.thumbnailUri, space?.host?.avatarUrl)

                val durationStr = item.durationSeconds?.let { s ->
                    val m = s / 60
                    val sec = s % 60
                    String.format("%02d:%02d", m, sec)
                }

                val progressFraction = if (progress != null && progress.totalDurationMs > 0) {
                    (progress.currentPositionMs.toFloat() / progress.totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                AudioMediaRow(
                    title = title,
                    subtitle = subtitle,
                    artworkUrl = artwork,
                    durationText = durationStr,
                    isSpace = space != null,
                    isPlaying = isPlaying,
                    isFavorite = favoriteUris.contains(uri),
                    progressFraction = progressFraction,
                    onClick = { onPlayItem(item, index) },
                    onToggleFavorite = { onToggleFavorite(uri) },
                    onAddToPlaylist = { onAddToPlaylist(item) },
                    onAddToQueue = { onAddToQueue(item) },
                    onDelete = { onDeleteMedia(item) },
                )
            }
        }
    }
}
