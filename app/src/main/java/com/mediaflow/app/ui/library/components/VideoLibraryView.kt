package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            title = "No tienes videos todavía",
            subtitle = "Descarga un video para reproducirlo aquí.",
            actionLabel = null,
            onAction = null,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxSize()
                .testTag("video_library_grid"),
        ) {
            items(items, key = { it.id }) { item ->
                val uri = item.localUri ?: item.id
                val isFavorite = favoriteUris.contains(uri)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayItem(item) },
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                        ) {
                            MediaArtwork(
                                artworkUrl = item.thumbnailUri ?: item.localUri,
                                size = 200.dp,
                                mediaType = MediaType.VIDEO,
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                                modifier = Modifier.fillMaxSize(),
                            )

                            item.durationSeconds?.let { s ->
                                val m = s / 60
                                val sec = s % 60
                                val dur = String.format("%02d:%02d", m, sec)
                                Surface(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp),
                                ) {
                                    Text(
                                        text = dur,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            MediaOverflowMenu(
                                isFavorite = isFavorite,
                                onPlay = { onPlayItem(item) },
                                onAddToPlaylist = { },
                                onToggleFavorite = { onToggleFavorite(uri) },
                                onAddToQueue = null,
                                onDelete = { onDeleteMedia(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}
