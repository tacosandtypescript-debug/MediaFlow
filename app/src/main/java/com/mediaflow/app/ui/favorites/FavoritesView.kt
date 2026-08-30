package com.mediaflow.app.ui.favorites

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.theme.customColors
import com.mediaflow.app.ui.common.media.AudioMediaRow
import com.mediaflow.app.ui.library.components.EmptyLibraryState
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.XSpace

/**
 * Dedicated Favorites view displaying all hearted audio tracks with immediate play and playlist options.
 */
@Composable
fun FavoritesView(
    items: List<DownloadItem>,
    spacesMap: Map<String, XSpace>,
    playingMediaId: String?,
    isPlayerPlaying: Boolean,
    favoriteUris: Set<String>,
    onPlayAllFavorites: () -> Unit,
    onPlayItem: (item: DownloadItem, index: Int) -> Unit,
    onToggleFavorite: (mediaUri: String) -> Unit,
    onAddToPlaylist: (item: DownloadItem) -> Unit,
    onAddToQueue: (item: DownloadItem) -> Unit,
    onDeleteMedia: (item: DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            EmptyLibraryState(
                icon = Icons.Outlined.FavoriteBorder,
                title = stringResource(R.string.favorites_empty_title),
                subtitle = stringResource(R.string.favorites_empty_subtitle),
                actionLabel = null,
                onAction = null,
                modifier = Modifier.weight(1f),
            )
        } else {
            val totalSeconds = remember(items) {
                items.sumOf { it.durationSeconds ?: 0L }
            }

            val durationText = remember(totalSeconds) {
                if (totalSeconds <= 0L) ""
                else {
                    val minutes = totalSeconds / 60
                    val hours = minutes / 60
                    if (hours > 0) "${hours}h ${minutes % 60}m" else "$minutes min"
                }
            }

            // Hero Header Card
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                val heartGradient = MaterialTheme.customColors.favoriteGradient

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(heartGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.favorites_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    val countLabel = if (items.size == 1) "1 audio" else "${items.size} audios"
                    val fullLabel = if (durationText.isNotBlank()) "$countLabel · $durationText" else countLabel
                    Text(
                        text = fullLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = onPlayAllFavorites,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("play_all_favorites_btn"),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reproducir", fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("favorites_list"),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    val uri = item.localUri ?: item.id
                    val space = spacesMap[item.sourceUrl] ?: spacesMap[item.id]
                    val isPlaying = playingMediaId == uri && isPlayerPlaying

                    val title = space?.title ?: item.title ?: item.fileName ?: "Audio"
                    val subtitle = space?.let { "Host: ${it.host.formattedHandle}" } ?: item.fileName ?: "Local"
                    val artwork = preferredArtworkUrl(item.thumbnailUri, space?.host?.avatarUrl)

                    val durationStr = item.durationSeconds?.let { s ->
                        val m = s / 60
                        val sec = s % 60
                        String.format("%02d:%02d", m, sec)
                    }

                    AudioMediaRow(
                        title = title,
                        subtitle = subtitle,
                        artworkUrl = artwork,
                        durationText = durationStr,
                        isSpace = space != null,
                        isPlaying = isPlaying,
                        isFavorite = true,
                        onClick = { onPlayItem(item, index) },
                        onToggleFavorite = { onToggleFavorite(uri) },
                        onAddToPlaylist = { onAddToPlaylist(item) },
                        onAddToQueue = { onAddToQueue(item) },
                        shareUri = item.localUri,
                        shareMimeType = item.selectedFormat?.mimeType,
                        shareTitle = title,
                        shareIsAudio = true,
                        onDelete = { onDeleteMedia(item) },
                    )
                }
            }
        }
    }
}
