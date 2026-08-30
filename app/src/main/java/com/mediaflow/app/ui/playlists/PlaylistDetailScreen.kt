package com.mediaflow.app.ui.playlists

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.common.media.AudioMediaRow
import com.mediaflow.app.ui.library.components.EmptyLibraryState
import com.mediaflow.app.ui.playlists.components.PlaylistCover
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.Playlist

/**
 * Detailed view of a playlist displaying its composite cover, duration,
 * tracks list, and immediate play/remove actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    items: List<DownloadItem>,
    playingMediaId: String?,
    isPlayerPlaying: Boolean,
    favoriteUris: Set<String>,
    onBack: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onPlayItem: (item: DownloadItem, index: Int) -> Unit,
    onToggleFavorite: (mediaUri: String) -> Unit,
    onRemoveFromPlaylist: (mediaUri: String) -> Unit,
    onDeleteMedia: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = remember(items) {
        items.sumOf { it.durationSeconds ?: 0L }
    }

    val durationText = remember(totalSeconds) {
        if (totalSeconds <= 0L) ""
        else {
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            if (hours > 0) {
                "${hours}h ${minutes % 60}m"
            } else {
                "${minutes} min"
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Header Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                PlaylistCover(
                    artworks = items.take(4).map {
                        com.mediaflow.app.ui.common.media.preferredArtworkUrl(it.thumbnailUri)
                    },
                    size = 110.dp,
                    shape = RoundedCornerShape(20.dp),
                )

                Spacer(Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PLAYLIST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    val countLabel = if (playlist.itemCount == 1) "1 audio" else "${playlist.itemCount} audios"
                    val fullSubText = if (durationText.isNotBlank()) "$countLabel · $durationText" else countLabel
                    Text(
                        text = fullSubText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Action Buttons
            if (items.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = onPlayPlaylist,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reproducir", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (items.isEmpty()) {
                EmptyLibraryState(
                    icon = Icons.Outlined.MusicNote,
                    title = "Esta playlist está vacía",
                    subtitle = "Añade audios desde tu biblioteca tocando el menú de opciones.",
                    actionLabel = null,
                    onAction = null,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        val uri = item.localUri ?: item.id
                        val isPlaying = playingMediaId == uri && isPlayerPlaying

                        val durationStr = item.durationSeconds?.let { s ->
                            val m = s / 60
                            val sec = s % 60
                            String.format("%02d:%02d", m, sec)
                        }

                        AudioMediaRow(
                            title = item.title ?: item.fileName ?: "Audio",
                            subtitle = item.fileName ?: "Local",
                            artworkUrl = com.mediaflow.app.ui.common.media.preferredArtworkUrl(item.thumbnailUri),
                            durationText = durationStr,
                            isSpace = false,
                            isPlaying = isPlaying,
                            isFavorite = favoriteUris.contains(uri),
                            onClick = { onPlayItem(item, index) },
                            onToggleFavorite = { onToggleFavorite(uri) },
                            onAddToPlaylist = { },
                            onAddToQueue = null,
                            shareUri = item.localUri,
                            shareMimeType = item.selectedFormat?.mimeType,
                            shareTitle = item.title ?: item.fileName,
                            shareIsAudio = true,
                            onDelete = { onRemoveFromPlaylist(uri) },
                        )
                    }
                }
            }
        }
    }
}
