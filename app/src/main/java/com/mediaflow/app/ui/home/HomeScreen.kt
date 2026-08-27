package com.mediaflow.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaflow.app.R
import com.mediaflow.app.ui.components.EmptyState
import com.mediaflow.app.ui.home.components.DownloadButton
import com.mediaflow.app.ui.home.components.FileNameField
import com.mediaflow.app.ui.home.components.MediaTypeSelector
import com.mediaflow.app.ui.home.components.QualitySelector
import com.mediaflow.app.ui.home.components.SourceAnalysisCard
import com.mediaflow.app.ui.home.components.UrlInputField
import com.mediaflow.app.ui.home.components.XSpaceCard
import com.mediaflow.app.ui.downloads.DownloadStartResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.mediaflow.domain.repository.SourceResolver

/** Staggered, test-safe entrance transition reused across Home blocks. */
private fun staggeredEnter(delayMillis: Int = 0): EnterTransition =
    fadeIn(tween(durationMillis = 500, delayMillis = delayMillis)) +
        slideInVertically(tween(durationMillis = 500, delayMillis = delayMillis)) { it / 20 } +
        scaleIn(tween(durationMillis = 500, delayMillis = delayMillis), initialScale = 0.97f)

/**
 * Modern Home screen of MediaFlow.
 *
 * Keeps local validation while the navigation layer owns the real download
 * request. Adds staggered entrance animations and an expressive layout.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    sourceResolver: SourceResolver? = null,
    onDownloadRequested: ((HomeUiState) -> DownloadStartResult)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pendingDownloadMessage = stringResource(R.string.home_info_download_pending)
    val downloadAcceptedMessage = stringResource(R.string.home_info_download_started)

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(state.url, state.mediaType, sourceResolver) {
        if (sourceResolver != null && state.validationState == ValidationState.Valid) {
            delay(500)
            viewModel.analyze(sourceResolver)
        }
    }

    val linkCardColor by animateColorAsState(
        targetValue = when {
            state.errorMessage != null -> MaterialTheme.colorScheme.errorContainer
            state.validationState == ValidationState.Valid -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "linkCardColor",
    )

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
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            AnimatedVisibility(entered, enter = staggeredEnter(30)) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            AnimatedVisibility(entered, enter = staggeredEnter(120)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = linkCardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        UrlInputField(
                            url = state.url,
                            onUrlChange = viewModel::onUrlChanged,
                            onClear = viewModel::onClearUrl,
                            errorMessage = state.errorMessage,
                            infoMessage = state.infoMessage,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            val spaceMetadata = state.sourceInfo?.spaceMetadata
            if (spaceMetadata != null) {
                XSpaceCard(
                    space = spaceMetadata,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }

            SourceAnalysisCard(
                state = state.analysisState,
                sourceInfo = state.sourceInfo,
                formats = state.availableFormats,
                selectedFormatId = state.selectedFormatId,
                onFormatSelected = viewModel::onFormatSelected,
                errorMessage = state.analysisError,
                modifier = Modifier.padding(bottom = 18.dp),
            )

            AnimatedVisibility(sourceResolver == null || state.analysisState == AnalysisState.IDLE, enter = staggeredEnter(240)) {
                MediaTypeSelector(
                    selected = state.mediaType,
                    onSelect = viewModel::onMediaTypeSelected,
                )
            }

            Spacer(Modifier.height(18.dp))

            AnimatedVisibility(sourceResolver == null, enter = staggeredEnter(340)) {
                QualitySelector(
                    options = state.qualityOptions,
                    selected = state.quality,
                    onSelect = viewModel::onQualitySelected,
                )
            }

            Spacer(Modifier.height(12.dp))

            AnimatedVisibility(entered, enter = staggeredEnter(440)) {
                FileNameField(
                    fileName = state.fileName,
                    onFileNameChange = viewModel::onFileNameChanged,
                )
            }

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(entered, enter = staggeredEnter(540)) {
                DownloadButton(
                    enabled = state.isDownloadButtonEnabled &&
                        (sourceResolver == null || state.analysisState == AnalysisState.READY),
                    onClick = {
                        android.util.Log.d("MediaFlow", "DownloadButton clicked! state.analysisState=${state.analysisState}")
                        if (sourceResolver != null && state.analysisState != AnalysisState.READY) {
                            viewModel.analyze(sourceResolver)
                            scope.launch { snackbarHostState.showSnackbar("Analizando la fuente…") }
                        } else {
                            val result = onDownloadRequested?.invoke(state)
                            android.util.Log.d("MediaFlow", "onDownloadRequested result=$result")
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
            }

            Spacer(Modifier.height(32.dp))

            AnimatedVisibility(entered, enter = staggeredEnter(660)) {
                Column {
                    Text(
                        text = stringResource(R.string.home_recent_downloads),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.Download,
                            title = stringResource(R.string.home_recent_empty_title),
                            subtitle = stringResource(R.string.home_recent_empty_subtitle),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
