package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.AudioMediaRow
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.app.ui.playlists.components.PlaylistCard
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.Playlist
import com.mediaflow.core.model.XSpace

@Composable
fun LibraryAllView(
    items: List<DownloadItem>,
    playlists: List<Playlist>,
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
    onOpenPlaylist: (Playlist) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    selectedIds: Set<String> = emptySet(),
    inSelectionMode: Boolean = false,
    onLongPressItem: (DownloadItem) -> Unit = {},
    onToggleSelect: (DownloadItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty() && playlists.isEmpty()) {
        EmptyLibraryState(
            icon = Icons.Outlined.LibraryMusic,
            title = stringResource(R.string.library_all_empty_title),
            subtitle = stringResource(R.string.library_all_empty_subtitle),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val mediaMap = remember(items) { items.associateBy { it.localUri ?: it.id } }

    LazyColumn(
        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("library_all_list"),
    ) {
        items(playlists, key = { "pl_${it.id}" }, contentType = { "playlist" }) { playlist ->
            val artworks = playlist.mediaUris.take(4).map { uri ->
                preferredArtworkUrl(mediaMap[uri]?.thumbnailUri)
            }
            PlaylistCard(
                playlist = playlist,
                artworks = artworks,
                onClick = { onOpenPlaylist(playlist) },
                onPlay = { onPlayPlaylist(playlist) },
                onRename = { onRenamePlaylist(playlist) },
                onDelete = { onDeletePlaylist(playlist.id) },
            )
        }
        itemsIndexed(
            items,
            key = { _, item -> item.id },
            contentType = { _, item -> item.mediaType },
        ) { index, item ->
            val uri = item.localUri ?: item.id
            val space = spacesMap[item.sourceUrl] ?: spacesMap[item.id]
            val title = space?.title ?: item.title ?: item.fileName ?: "Media"
            val subtitle = space?.let { "Host: ${it.host.formattedHandle}" }
                ?: if (item.mediaType == MediaType.VIDEO) stringResource(R.string.player_media_video)
                else stringResource(R.string.player_media_audio)
            val durationStr = item.durationSeconds?.let { s -> "%02d:%02d".format(s / 60, s % 60) }
            val progress = progressMap[item.id] ?: progressMap[uri]
            val progressFraction = if (progress != null && progress.totalDurationMs > 0) {
                (progress.currentPositionMs.toFloat() / progress.totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            AudioMediaRow(
                title = title,
                subtitle = subtitle,
                artworkUrl = preferredArtworkUrl(item.thumbnailUri, space?.host?.avatarUrl),
                durationText = durationStr,
                isSpace = space != null,
                mediaType = item.mediaType,
                isPlaying = playingMediaId == uri && isPlayerPlaying,
                isFavorite = favoriteUris.contains(uri),
                progressFraction = progressFraction,
                selected = item.id in selectedIds,
                onClick = {
                    if (inSelectionMode) onToggleSelect(item)
                    else onPlayItem(item, index.coerceAtLeast(0))
                },
                onLongClick = { onLongPressItem(item) },
                onToggleFavorite = { onToggleFavorite(uri) },
                onAddToPlaylist = { onAddToPlaylist(item) },
                onAddToQueue = { onAddToQueue(item) },
                shareUri = item.localUri,
                shareMimeType = item.selectedFormat?.mimeType,
                shareTitle = title,
                shareIsAudio = item.mediaType == MediaType.AUDIO,
                onDelete = { onDeleteMedia(item) },
            )
        }
    }
}
