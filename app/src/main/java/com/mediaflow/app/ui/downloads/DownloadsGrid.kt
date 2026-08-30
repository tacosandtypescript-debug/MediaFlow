package com.mediaflow.app.ui.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
fun DownloadsGrid(
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
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("downloads_grid"),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(downloads, key = { it.id }) { item ->
            val space = spacesMap[item.sourceUrl] ?: spacesMap[item.id] ?: spacesMap[item.localUri.orEmpty()]
            DownloadGridTile(
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
private fun DownloadGridTile(
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.status == DownloadStatus.COMPLETED, onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                MediaArtwork(
                    artworkUrl = preferredArtworkUrl(item.thumbnailUri, space?.host?.avatarUrl),
                    size = 48.dp,
                    mediaType = item.mediaType,
                    isSpace = space != null,
                    fillMax = true,
                    modifier = Modifier.fillMaxSize(),
                )
                Icon(
                    imageVector = if (item.mediaType == MediaType.AUDIO) {
                        Icons.Outlined.Audiotrack
                    } else {
                        Icons.Outlined.Videocam
                    },
                    contentDescription = if (item.mediaType == MediaType.AUDIO) {
                        stringResource(R.string.media_type_audio)
                    } else {
                        stringResource(R.string.media_type_video)
                    },
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                )
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.download_more))
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            if (item.status == DownloadStatus.DOWNLOADING ||
                item.status == DownloadStatus.PREPARING ||
                item.status == DownloadStatus.QUEUED
            ) {
                LinearProgressIndicator(
                    progress = { if (item.isProgressKnown) item.progress else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
