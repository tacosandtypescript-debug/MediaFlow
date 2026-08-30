package com.mediaflow.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.MediaArtwork
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.app.ui.downloads.DownloadStartResult
import com.mediaflow.app.ui.home.components.DownloadButton
import com.mediaflow.app.ui.home.components.FileNameField
import com.mediaflow.app.ui.home.components.MediaTypeSelector
import com.mediaflow.app.ui.home.components.QualitySelector
import com.mediaflow.app.ui.home.components.SourceAnalysisCard
import com.mediaflow.app.ui.home.components.UrlInputField
import com.mediaflow.app.ui.home.components.XSpaceCard
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.domain.repository.SourceResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    sourceResolver: SourceResolver? = null,
    onDownloadRequested: ((HomeUiState) -> DownloadStartResult)? = null,
    onPlayLive: ((String) -> Unit)? = null,
    recentDownloads: List<DownloadItem> = emptyList(),
    onRecentClick: (DownloadItem) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pendingDownloadMessage = stringResource(R.string.home_info_download_pending)
    val downloadAcceptedMessage = stringResource(R.string.home_info_download_started)
    val analyzingMessage = stringResource(R.string.analysis_loading)
    val completedRecents = remember(recentDownloads) {
        recentDownloads.filter { it.status == DownloadStatus.COMPLETED }.take(8)
    }
    val greetingRes = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> R.string.home_greeting_morning
            in 12..19 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        }
    }

    LaunchedEffect(state.url, sourceResolver) {
        if (
            sourceResolver != null &&
            state.validationState == ValidationState.Valid &&
            state.analysisState == AnalysisState.IDLE
        ) {
            delay(500)
            viewModel.analyze(sourceResolver)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(greetingRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))

            UrlInputField(
                url = state.url,
                onUrlChange = viewModel::onUrlChanged,
                onClear = viewModel::onClearUrl,
                errorMessage = state.errorMessage,
                infoMessage = state.infoMessage,
            )

            Spacer(Modifier.height(16.dp))

            val spaceMetadata = state.sourceInfo?.spaceMetadata
            if (spaceMetadata != null) {
                XSpaceCard(
                    space = spaceMetadata,
                    modifier = Modifier.padding(bottom = 16.dp),
                    onPlayLive = { space ->
                        val streamUrl = space.audioStreamUrl
                        if (streamUrl != null) {
                            onPlayLive?.invoke(streamUrl)
                        }
                    },
                )
            }

            MediaTypeSelector(
                selected = state.mediaType,
                onSelect = viewModel::onMediaTypeSelected,
                videoEnabled = state.sourceInfo?.spaceMetadata == null,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            SourceAnalysisCard(
                state = state.analysisState,
                sourceInfo = state.sourceInfo,
                selectedType = state.mediaType,
                selectedFormat = state.availableFormats.firstOrNull { it.formatId == state.selectedFormatId },
                errorMessage = state.analysisError,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (sourceResolver == null || state.analysisState == AnalysisState.READY) {
                QualitySelector(
                    options = state.qualityOptions,
                    selected = state.quality,
                    onSelect = viewModel::onQualitySelected,
                )
            }

            if (state.sourceInfo?.playlistEntries.isNullOrEmpty()) {
                FileNameField(
                    fileName = state.fileName,
                    suggestedFileName = state.suggestedFileName,
                    onFileNameChange = viewModel::onFileNameChanged,
                )
            }

            Spacer(Modifier.height(16.dp))

            DownloadButton(
                enabled = state.isDownloadButtonEnabled &&
                    (sourceResolver == null || state.analysisState == AnalysisState.READY),
                label = playlistDownloadLabel(state) ?: stringResource(
                    if (state.mediaType == ContentType.VIDEO) {
                        R.string.home_download_video
                    } else {
                        R.string.home_download_audio
                    },
                ),
                onClick = {
                    if (sourceResolver != null && state.analysisState != AnalysisState.READY) {
                        viewModel.analyze(sourceResolver)
                        scope.launch { snackbarHostState.showSnackbar(analyzingMessage) }
                    } else {
                        val result = onDownloadRequested?.invoke(state)
                        when (result) {
                            null -> scope.launch { snackbarHostState.showSnackbar(pendingDownloadMessage) }
                            is DownloadStartResult.Accepted -> scope.launch {
                                snackbarHostState.showSnackbar(downloadAcceptedMessage)
                            }
                            DownloadStartResult.AwaitingNotificationPermission -> Unit
                            is DownloadStartResult.Rejected -> scope.launch {
                                snackbarHostState.showSnackbar(result.message)
                            }
                        }
                    }
                },
            )

            if (completedRecents.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.home_recent_downloads),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                completedRecents.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { item ->
                            RecentShortcutTile(
                                item = item,
                                onClick = { onRecentClick(item) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun RecentShortcutTile(
    item: DownloadItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(64.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaArtwork(
                artworkUrl = preferredArtworkUrl(item.thumbnailUri),
                size = 64.dp,
                shape = RoundedCornerShape(0.dp),
                mediaType = item.mediaType,
            )
            Text(
                text = item.title ?: item.fileName ?: item.sourceUrl,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
        }
    }
}

@Composable
private fun playlistDownloadLabel(state: HomeUiState): String? {
    val count = state.sourceInfo?.playlistEntries?.size ?: 0
    if (count <= 0) return null
    val type = stringResource(
        if (state.mediaType == ContentType.VIDEO) {
            R.string.media_type_video
        } else {
            R.string.media_type_audio
        },
    )
    return stringResource(R.string.home_download_playlist, count, type.lowercase())
}
