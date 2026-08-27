package com.mediaflow.app.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.components.EmptyState
import com.mediaflow.app.ui.components.PlaybackBadge
import com.mediaflow.app.ui.components.PlaybackProgressBar
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.XSpace

/** Downloads screen backed by Media3 DownloadIndex state with playback progress indicators and Space info. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    downloads: List<DownloadItem> = emptyList(),
    progressMap: Map<String, PlaybackProgress> = emptyMap(),
    spacesMap: Map<String, XSpace> = emptyMap(),
    onOpen: (DownloadItem) -> Unit = {},
    onPause: (String) -> Unit = {},
    onResume: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
    onRemove: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_downloads)) })
        },
    ) { innerPadding ->
        if (downloads.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Download,
                        title = stringResource(R.string.downloads_empty_title),
                        subtitle = stringResource(R.string.downloads_empty_subtitle),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBackToHome) {
                    Text(stringResource(R.string.go_to_home))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                items(downloads, key = { it.id }) { item ->
                    val progress = progressMap[item.id] ?: progressMap[item.localUri.orEmpty()]
                    val space = spacesMap[item.sourceUrl] ?: spacesMap[item.id] ?: spacesMap[item.localUri.orEmpty()]
                    DownloadCard(
                        item = item,
                        progress = progress,
                        space = space,
                        onPause = { onPause(item.id) },
                        onResume = { onResume(item.id) },
                        onCancel = { onCancel(item.id) },
                        onRetry = { onRetry(item.id) },
                        onRemove = { onRemove(item.id) },
                        onOpen = { onOpen(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    progress: PlaybackProgress?,
    space: XSpace? = null,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.status == DownloadStatus.COMPLETED) {
                android.util.Log.d("MediaFlow", "DownloadCard clicked for item ${item.id}")
                onOpen()
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = space?.title ?: item.fileName ?: item.title ?: item.sourceUrl,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )

            if (space != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Radio,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "X Space · Host: ${space.host.formattedHandle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusLabel(item.status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.status == DownloadStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.status == DownloadStatus.COMPLETED) {
                    PlaybackBadge(progress = progress, mediaType = item.mediaType)
                }
            }

            if (item.status == DownloadStatus.COMPLETED) {
                PlaybackProgressBar(progress = progress, modifier = Modifier.padding(top = 8.dp))
            }

            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PREPARING || item.status == DownloadStatus.QUEUED) {
                if (item.isProgressKnown) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    Text(
                        text = "${(item.progress * 100).toInt()} % · ${formatBytes(item.downloadedBytes)}${item.totalBytes?.let { " / ${formatBytes(it)}" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    Text(
                        text = "${formatBytes(item.downloadedBytes)} descargados",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            val errorText = item.errorMessage
            if (item.status == DownloadStatus.FAILED && errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Outlined.Pause, contentDescription = stringResource(R.string.download_pause))
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.download_cancel))
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.download_resume))
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.download_cancel))
                        }
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.download_retry))
                        }
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.download_remove))
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = {
                            android.util.Log.d("MediaFlow", "Open IconButton clicked for item ${item.id}")
                            onOpen()
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.gallery_open))
                        }
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.download_remove))
                        }
                    }
                    DownloadStatus.PREPARING, DownloadStatus.QUEUED -> {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.download_cancel))
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.IDLE -> stringResource(R.string.download_status_idle)
    DownloadStatus.QUEUED -> stringResource(R.string.download_status_queued)
    DownloadStatus.ANALYZING -> stringResource(R.string.download_status_analyzing)
    DownloadStatus.PREPARING -> stringResource(R.string.download_status_preparing)
    DownloadStatus.DOWNLOADING -> stringResource(R.string.download_status_downloading)
    DownloadStatus.PAUSED -> stringResource(R.string.download_status_paused)
    DownloadStatus.COMPLETED -> stringResource(R.string.download_status_completed)
    DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
    DownloadStatus.CANCELED -> stringResource(R.string.download_status_canceled)
}

private fun formatBytes(value: Long): String = when {
    value >= 1_000_000_000L -> "%.1f GB".format(value / 1_000_000_000f)
    value >= 1_000_000L -> "%.1f MB".format(value / 1_000_000f)
    value >= 1_000L -> "%.1f KB".format(value / 1_000f)
    else -> "$value B"
}
