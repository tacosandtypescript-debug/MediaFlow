package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.theme.customColors
import com.mediaflow.app.ui.common.media.MediaArtwork
import com.mediaflow.app.ui.common.media.MediaOverflowMenu
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType

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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxSize()
                .testTag("video_library_grid"),
        ) {
            items(items, key = { it.id }) { item ->
                val uri = item.localUri ?: item.id
                val isFavorite = favoriteUris.contains(uri)

                val isPlaying = playingMediaId == uri
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayItem(item) },
                ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            MediaArtwork(
                                artworkUrl = preferredArtworkUrl(item.thumbnailUri),
                                size = 180.dp,
                                mediaType = MediaType.VIDEO,
                                shape = RoundedCornerShape(4.dp),
                            )

                            item.durationSeconds?.let { s ->
                                val m = s / 60
                                val sec = s % 60
                                val dur = String.format("%02d:%02d", m, sec)
                                Surface(
                                    color = MaterialTheme.customColors.dialogBackground.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp),
                                ) {
                                    Text(
                                        text = dur,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title ?: item.fileName ?: "Video",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.player_media_video),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }

                            MediaOverflowMenu(
                                isFavorite = isFavorite,
                                onPlay = { onPlayItem(item) },
                                onAddToPlaylist = { },
                                onToggleFavorite = { onToggleFavorite(uri) },
                                onAddToQueue = null,
                                shareUri = item.localUri,
                                shareMimeType = item.selectedFormat?.mimeType,
                                shareTitle = item.title ?: item.fileName,
                                shareIsAudio = false,
                                onDelete = { onDeleteMedia(item) },
                            )
                        }
                }
            }
        }
    }
}
