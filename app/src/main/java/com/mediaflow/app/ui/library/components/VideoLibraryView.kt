package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mediaflow.app.R
import com.mediaflow.core.model.DownloadItem

/**
 * Grid view displaying downloaded video media items.
 */
@Composable
fun VideoLibraryView(
    items: List<DownloadItem>,
    playingMediaId: String?,
    favoriteUris: Set<String>,
    onPlayItem: (item: DownloadItem) -> Unit,
    onToggleFavorite: (mediaUri: String) -> Unit,
    onDeleteMedia: (item: DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (DownloadItem) -> Unit = {},
    isSelected: (DownloadItem) -> Boolean = { false },
    selectionMode: Boolean = false,
) {
    if (items.isEmpty()) {
        EmptyLibraryState(
            icon = Icons.Outlined.VideoLibrary,
            title = stringResource(R.string.library_video_empty_title),
            subtitle = stringResource(R.string.library_video_empty_subtitle),
            actionLabel = null,
            onAction = null,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        VideoMosaic(
            items = items,
            playingMediaId = playingMediaId,
            favoriteUris = favoriteUris,
            onPlayItem = onPlayItem,
            onToggleFavorite = onToggleFavorite,
            onDeleteMedia = onDeleteMedia,
            onLongPress = onLongPress,
            isSelected = isSelected,
            selectionMode = selectionMode,
            modifier = modifier,
        )
    }
}
