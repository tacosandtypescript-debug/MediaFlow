package com.mediaflow.app.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.MediaArtwork
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.XSpace

@Composable
fun DownloadsList(
    downloads: List<DownloadItem>,
    progressMap: Map<String, PlaybackProgress>,
    spacesMap: Map<String, XSpace>,
    onOpen: (DownloadItem) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("downloads_list"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(downloads, key = { it.id }) { item ->
            val space = spacesMap[item.sourceUrl] ?: spacesMap[item.id] ?: spacesMap[item.localUri.orEmpty()]
            DownloadListRow(
                item = item,
                space = space,
                onOpen = { onOpen(item) },
                onPause = { onPause(item.id) },
                onResume = { onResume(item.id) },
                onCancel = { onCancel(item.id) },
                onRetry = { onRetry(item.id) },
                onRemove = { onRemove(item.id) },
            )
        }
    }
}

@Composable
private fun DownloadListRow(
    item: DownloadItem,
    space: XSpace?,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val title = space?.title ?: item.fileName ?: item.title ?: item.sourceUrl
    val meta = listOfNotNull(
        downloadStatusLabel(item.status),
        if (item.status == DownloadStatus.COMPLETED) {
            if (item.mediaType == MediaType.VIDEO) "Vídeo" else "Audio"
        } else null,
        item.durationSeconds?.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) },
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = item.status == DownloadStatus.COMPLETED, onClick = onOpen)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaArtwork(
            artworkUrl = preferredArtworkUrl(item.thumbnailUri, space?.host?.avatarUrl),
            size = 40.dp,
            mediaType = item.mediaType,
            isSpace = space != null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.status == DownloadStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.status == DownloadStatus.DOWNLOADING ||
                item.status == DownloadStatus.PREPARING ||
                item.status == DownloadStatus.QUEUED
            ) {
                LinearProgressIndicator(
                    progress = { if (item.isProgressKnown) item.progress else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(2.dp),
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("download_overflow")) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.download_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DownloadOverflowItems(
                    item = item,
                    onDismiss = { menuOpen = false },
                    onOpen = onOpen,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onRemove = onRemove,
                )
            }
        }
    }
}
