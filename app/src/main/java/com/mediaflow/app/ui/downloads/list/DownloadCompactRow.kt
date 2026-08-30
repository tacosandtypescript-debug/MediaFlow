package com.mediaflow.app.ui.downloads.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.common.media.MediaArtwork
import com.mediaflow.app.ui.common.media.MediaOverflowMenu
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaType

@Composable
fun DownloadCompactRow(
    item: DownloadItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = item.status == DownloadStatus.COMPLETED, onClick = onOpen)
            .padding(horizontal = 4.dp)
            .testTag("download_compact_row"),
    ) {
        MediaArtwork(
            artworkUrl = preferredArtworkUrl(item.thumbnailUri),
            size = 36.dp,
            mediaType = item.mediaType,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = item.title ?: item.fileName ?: item.sourceUrl,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val kind = if (item.mediaType == MediaType.AUDIO) "Audio" else "Vídeo"
            val size = item.totalBytes?.let { bytes ->
                if (bytes >= 1_000_000) "${bytes / 1_000_000} MB" else "${bytes / 1_000} KB"
            }
            Text(
                text = listOfNotNull(kind, size).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        MediaOverflowMenu(
            isFavorite = false,
            onPlay = onOpen,
            onAddToPlaylist = {},
            onToggleFavorite = {},
            shareUri = item.localUri,
            shareTitle = item.title ?: item.fileName,
            shareIsAudio = item.mediaType == MediaType.AUDIO,
            onDelete = onRemove,
        )
    }
}
