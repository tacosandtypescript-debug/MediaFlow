package com.mediaflow.app.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.components.EmptyState
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.XSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    downloads: List<DownloadItem> = emptyList(),
    progressMap: Map<String, PlaybackProgress> = emptyMap(),
    spacesMap: Map<String, XSpace> = emptyMap(),
    viewMode: DownloadsViewMode = DownloadsViewMode.LIST,
    onViewModeChange: (DownloadsViewMode) -> Unit = {},
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
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_downloads)) },
                actions = {
                    IconButton(
                        onClick = {
                            onViewModeChange(
                                if (viewMode == DownloadsViewMode.LIST) {
                                    DownloadsViewMode.GRID
                                } else {
                                    DownloadsViewMode.LIST
                                },
                            )
                        },
                    ) {
                        Icon(
                            imageVector = if (viewMode == DownloadsViewMode.LIST) {
                                Icons.Outlined.GridView
                            } else {
                                Icons.AutoMirrored.Outlined.ViewList
                            },
                            contentDescription = if (viewMode == DownloadsViewMode.LIST) {
                                stringResource(R.string.gallery_view_grid)
                            } else {
                                stringResource(R.string.gallery_view_list)
                            },
                        )
                    }
                },
            )
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
                EmptyState(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.downloads_empty_title),
                    subtitle = stringResource(R.string.downloads_empty_subtitle),
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onBackToHome,
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(stringResource(R.string.go_to_home_cta))
                }
            }
        } else {
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            if (viewMode == DownloadsViewMode.GRID) {
                DownloadsGrid(
                    downloads = downloads,
                    progressMap = progressMap,
                    spacesMap = spacesMap,
                    onOpen = onOpen,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onRemove = onRemove,
                    modifier = contentModifier,
                )
            } else {
                DownloadsList(
                    downloads = downloads,
                    progressMap = progressMap,
                    spacesMap = spacesMap,
                    onOpen = onOpen,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onRemove = onRemove,
                    modifier = contentModifier,
                )
            }
        }
    }
}
