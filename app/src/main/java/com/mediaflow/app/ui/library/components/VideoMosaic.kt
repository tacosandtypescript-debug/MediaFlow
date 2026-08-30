package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.MediaArtwork
import com.mediaflow.app.ui.common.media.MediaOverflowMenu
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.app.ui.theme.customColors
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType

private val MosaicCorner = RoundedCornerShape(10.dp)

internal fun videoAspectRatio(item: DownloadItem): Float {
    val width = item.width?.takeIf { it > 0 } ?: item.selectedFormat?.width ?: 0
    val height = item.height?.takeIf { it > 0 } ?: item.selectedFormat?.height ?: 0
    return if (width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        16f / 9f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoMosaic(
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
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("video_library_grid"),
    ) {
        items(items, key = { it.id }, contentType = { it.mediaType }) { item ->
            val uri = item.localUri ?: item.id
            val isFavorite = favoriteUris.contains(uri)
            val isPlaying = playingMediaId == uri
            val selected = isSelected(item)

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = MosaicCorner,
                    border = if (selected) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(videoAspectRatio(item))
                            .clip(MosaicCorner)
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) onLongPress(item) else onPlayItem(item)
                                },
                                onLongClick = { onLongPress(item) },
                            ),
                    ) {
                        MediaArtwork(
                            artworkUrl = preferredArtworkUrl(item.thumbnailUri),
                            mediaType = MediaType.VIDEO,
                            shape = MosaicCorner,
                            fillMax = true,
                            modifier = Modifier.fillMaxSize(),
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
                            color = if (isPlaying) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
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
