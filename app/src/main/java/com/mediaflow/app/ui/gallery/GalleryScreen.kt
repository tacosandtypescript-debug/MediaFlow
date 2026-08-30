package com.mediaflow.app.ui.gallery

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaflow.app.R
import com.mediaflow.app.ui.components.EmptyState
import com.mediaflow.app.ui.components.PlaybackBadge
import com.mediaflow.app.ui.components.PlaybackProgressBar
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.PlaybackProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenItem: ((DownloadItem) -> Unit)? = null,
    viewModel: GalleryViewModel = viewModel(
        factory = GalleryViewModel.Factory(LocalContext.current.applicationContext as android.app.Application),
    ),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val selectedItem = state.items.firstOrNull { it.id == selectedId }
    val hasPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    val openAction: (DownloadItem) -> Unit = { item ->
        if (onOpenItem != null) {
            onOpenItem(item)
        } else {
            context.openGalleryItem(item)
        }
    }

    LaunchedEffect(state.items, selectedId) {
        if (selectedId != null && selectedItem == null) selectedId = null
    }

    if (showDeleteConfirmation && selectedItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.gallery_delete_title)) },
            text = { Text(stringResource(R.string.gallery_delete_message, selectedItem.fileName ?: selectedItem.title ?: stringResource(R.string.gallery_unknown_file))) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(selectedItem.id)
                    selectedId = null
                    showDeleteConfirmation = false
                }) { Text(stringResource(R.string.gallery_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text(stringResource(R.string.gallery_cancel)) }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_gallery)) },
                actions = {
                    Icon(
                        imageVector = Icons.Outlined.Collections,
                        contentDescription = stringResource(R.string.nav_gallery),
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            GalleryControls(state, viewModel)
            selectedItem?.let { item ->
                GallerySelectionBar(
                    item = item,
                    onOpen = { openAction(item) },
                    onDelete = { showDeleteConfirmation = true },
                    onClear = { selectedId = null },
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                val items = state.items
                when {
                    !hasPermission -> GalleryPermissionState { }
                    state.isLoading -> LoadingGalleryState()
                    state.errorMessage != null -> GalleryErrorState(onRetry = { viewModel.refresh() })
                    items.isEmpty() -> EmptyGalleryState(onBackToHome)
                    state.viewMode == GalleryViewMode.GRID -> GalleryGrid(
                        items = items,
                        progressMap = state.progressMap,
                        selectedId = selectedId,
                        onSelect = { id ->
                            if (selectedId == id) {
                                val item = items.firstOrNull { it.id == id }
                                if (item != null) openAction(item)
                            } else {
                                selectedId = id
                            }
                        },
                    )
                    else -> GalleryList(
                        items = items,
                        progressMap = state.progressMap,
                        selectedId = selectedId,
                        onSelect = { id ->
                            if (selectedId == id) {
                                val item = items.firstOrNull { it.id == id }
                                if (item != null) openAction(item)
                            } else {
                                selectedId = id
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GallerySelectionBar(
    item: DownloadItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.fileName ?: item.title ?: stringResource(R.string.gallery_unknown_file),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = onOpen) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.gallery_open))
                Text(stringResource(R.string.gallery_open), modifier = Modifier.padding(start = 4.dp))
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.gallery_delete))
                Text(stringResource(R.string.gallery_delete), modifier = Modifier.padding(start = 4.dp))
            }
            TextButton(onClick = onClear) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.gallery_clear_selection))
            }
        }
    }
}

@Composable
private fun GalleryControls(state: GalleryUiState, viewModel: GalleryViewModel) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(GalleryFilter.entries.toList()) { filter ->
            FilterChip(
                selected = state.filter == filter,
                onClick = { viewModel.setFilter(filter) },
                label = { Text(filter.label()) },
            )
        }
    }
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        GalleryViewMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = state.viewMode == mode,
                onClick = { viewModel.setViewMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, GalleryViewMode.entries.size),
                icon = {
                    Icon(
                        imageVector = if (mode == GalleryViewMode.GRID) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            ) {
                Text(if (mode == GalleryViewMode.GRID) stringResource(R.string.gallery_view_grid) else stringResource(R.string.gallery_view_list))
            }
        }
    }
}

@Composable
private fun GalleryGrid(
    items: List<DownloadItem>,
    progressMap: Map<String, PlaybackProgress>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val progress = progressMap[item.id] ?: progressMap[item.localUri.orEmpty()]
            GalleryGridCard(
                item = item,
                progress = progress,
                selected = selectedId == item.id,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun GalleryList(
    items: List<DownloadItem>,
    progressMap: Map<String, PlaybackProgress>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val progress = progressMap[item.id] ?: progressMap[item.localUri.orEmpty()]
            GalleryListCard(
                item = item,
                progress = progress,
                selected = selectedId == item.id,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun GalleryGridCard(
    item: DownloadItem,
    progress: PlaybackProgress?,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(item.id) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column {
            MediaThumbnail(item, Modifier.fillMaxWidth().aspectRatio(1.25f))
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = item.fileName ?: item.title ?: stringResource(R.string.gallery_unknown_file),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                PlaybackBadge(progress = progress, mediaType = item.mediaType)
                Spacer(Modifier.height(4.dp))
                PlaybackProgressBar(progress = progress)
            }
        }
    }
}

@Composable
private fun GalleryListCard(
    item: DownloadItem,
    progress: PlaybackProgress?,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(item.id) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaThumbnail(item, Modifier.size(76.dp).clip(MaterialTheme.shapes.medium))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = item.fileName ?: item.title ?: stringResource(R.string.gallery_unknown_file),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (item.mediaType == MediaType.VIDEO) stringResource(R.string.gallery_filter_videos) else stringResource(R.string.gallery_filter_audio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    )
                    PlaybackBadge(progress = progress, mediaType = item.mediaType)
                }
            }
            PlaybackProgressBar(progress = progress, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun MediaThumbnail(item: DownloadItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.localUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(Uri.parse(item.localUri), Size(480, 360), null)
                } else null
            }.getOrNull()?.asImageBitmap()
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Outlined.VideoLibrary else Icons.Outlined.MusicNote,
                contentDescription = item.title,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            )
        }
    }
}

@Composable
private fun GalleryPermissionState(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            icon = Icons.Outlined.Lock,
            title = stringResource(R.string.gallery_permission_title),
            subtitle = stringResource(R.string.gallery_permission_subtitle),
        )
        OutlinedButton(onClick = onRequest) {
            Text(stringResource(R.string.gallery_permission_action))
        }
    }
}

@Composable
private fun LoadingGalleryState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun GalleryErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            icon = Icons.Outlined.Collections,
            title = stringResource(R.string.gallery_error_title),
            subtitle = stringResource(R.string.gallery_error_subtitle),
        )
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.gallery_permission_action)) }
    }
}

@Composable
private fun EmptyGalleryState(onBackToHome: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            EmptyState(
                icon = Icons.Outlined.Image,
                title = stringResource(R.string.gallery_empty_title),
                subtitle = stringResource(R.string.gallery_empty_subtitle),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBackToHome) { Text(stringResource(R.string.go_to_home)) }
    }
}

private fun GalleryFilter.label(): String = when (this) {
    GalleryFilter.ALL -> "Todos"
    GalleryFilter.VIDEOS -> "Vídeos"
    GalleryFilter.AUDIO -> "Audio"
}

private fun android.content.Context.openGalleryItem(item: DownloadItem) {
    val uri = item.localUri?.let(Uri::parse) ?: return
    val mimeType = item.selectedFormat?.mimeType
        ?: if (item.mediaType == MediaType.VIDEO) "video/*" else "audio/*"
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }.onFailure { error ->
        if (error is ActivityNotFoundException) {
            android.widget.Toast.makeText(this, getString(R.string.gallery_open_unavailable), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
