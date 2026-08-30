package com.mediaflow.app.ui.downloads

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.MediaShare
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus

@Composable
fun DownloadOverflowItems(
    item: DownloadItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    if (item.status == DownloadStatus.COMPLETED) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.gallery_open)) },
            onClick = { onDismiss(); onOpen() },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.share)) },
            onClick = { onDismiss(); MediaShare.share(context, item) },
            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
        )
    }
    when (item.status) {
        DownloadStatus.DOWNLOADING -> {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_pause)) },
                onClick = { onDismiss(); onPause() },
                leadingIcon = { Icon(Icons.Outlined.Pause, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_cancel)) },
                onClick = { onDismiss(); onCancel() },
                leadingIcon = { Icon(Icons.Outlined.Stop, contentDescription = null) },
            )
        }
        DownloadStatus.PAUSED -> {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_resume)) },
                onClick = { onDismiss(); onResume() },
                leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_cancel)) },
                onClick = { onDismiss(); onCancel() },
                leadingIcon = { Icon(Icons.Outlined.Stop, contentDescription = null) },
            )
        }
        DownloadStatus.FAILED -> {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_retry)) },
                onClick = { onDismiss(); onRetry() },
                leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
            )
        }
        DownloadStatus.PREPARING, DownloadStatus.QUEUED -> {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_cancel)) },
                onClick = { onDismiss(); onCancel() },
                leadingIcon = { Icon(Icons.Outlined.Stop, contentDescription = null) },
            )
        }
        else -> {}
    }
    if (item.status == DownloadStatus.COMPLETED || item.status == DownloadStatus.FAILED) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.download_remove)) },
            onClick = { onDismiss(); onRemove() },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        )
    }
}

@Composable
fun downloadStatusLabel(status: DownloadStatus): String = when (status) {
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
