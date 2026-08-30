package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.AudioMediaRow
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.app.ui.library.LibraryAudioDragMath
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.XSpace

/**
 * List view of all downloaded audio files and X Spaces.
 */
@Composable
fun AudioLibraryView(
    items: List<DownloadItem>,
    spacesMap: Map<String, XSpace>,
    progressMap: Map<String, PlaybackProgress>,
    playingMediaId: String?,
    isPlayerPlaying: Boolean,
    favoriteUris: Set<String>,
    onPlayItem: (item: DownloadItem, index: Int) -> Unit,
    onToggleFavorite: (mediaUri: String) -> Unit,
    onAddToPlaylist: (item: DownloadItem) -> Unit,
    onAddToQueue: (item: DownloadItem) -> Unit,
    onDeleteMedia: (item: DownloadItem) -> Unit,
    onPlayAll: () -> Unit = {},
    shuffleEnabled: Boolean = false,
    onShuffleChange: (Boolean) -> Unit = {},
    onShuffleAll: () -> Unit = {},
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    selectedIds: Set<String> = emptySet(),
    inSelectionMode: Boolean = false,
    onLongPressItem: (DownloadItem) -> Unit = {},
    onToggleSelect: (DownloadItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyLibraryState(
            icon = Icons.Outlined.Audiotrack,
            title = stringResource(R.string.library_audio_empty_title),
            subtitle = stringResource(R.string.library_audio_empty_subtitle),
            actionLabel = null,
            onAction = null,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val pill = RoundedCornerShape(24.dp)
                val btnMod = Modifier
                    .weight(1f)
                    .height(48.dp)
                Button(
                    onClick = onPlayAll,
                    shape = pill,
                    modifier = btnMod.testTag("library_play_all_btn"),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.library_play_all),
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (shuffleEnabled) {
                    Button(
                        onClick = { onShuffleChange(false) },
                        shape = pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = btnMod
                            .testTag("library_shuffle_btn")
                            .semantics { selected = true },
                    ) {
                        Icon(Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.library_shuffle),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onShuffleAll,
                        shape = pill,
                        modifier = btnMod
                            .testTag("library_shuffle_btn")
                            .semantics { selected = false },
                    ) {
                        Icon(Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.library_shuffle),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            val density = LocalDensity.current
            val rowHeightPx = with(density) { 72.dp.toPx() }
            var draggingId by remember { mutableStateOf<String?>(null) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("audio_library_list"),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    val uri = item.localUri ?: item.id
                    val space = spacesMap[item.sourceUrl] ?: spacesMap[item.id]
                    val progress = progressMap[uri] ?: progressMap[item.id]
                    val isPlaying = playingMediaId == uri && isPlayerPlaying

                    val title = space?.title ?: item.title ?: item.fileName ?: "Audio"
                    val subtitle = space?.let { "Host: ${it.host.formattedHandle}" } ?: item.fileName ?: "Local"
                    val artwork = preferredArtworkUrl(item.thumbnailUri, space?.host?.avatarUrl)

                    val durationStr = item.durationSeconds?.let { s ->
                        val m = s / 60
                        val sec = s % 60
                        String.format("%02d:%02d", m, sec)
                    }

                    val progressFraction = if (progress != null && progress.totalDurationMs > 0) {
                        (progress.currentPositionMs.toFloat() / progress.totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    AudioMediaRow(
                        title = title,
                        subtitle = subtitle,
                        artworkUrl = artwork,
                        durationText = durationStr,
                        isSpace = space != null,
                        isPlaying = isPlaying,
                        isFavorite = favoriteUris.contains(uri),
                        progressFraction = progressFraction,
                        selected = item.id in selectedIds,
                        modifier = Modifier
                            .testTag("audio_library_row_$index")
                            .zIndex(if (draggingId == item.id) 1f else 0f)
                            .graphicsLayer {
                                if (draggingId == item.id) {
                                    translationY = dragOffsetY
                                    shadowElevation = 18f
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                }
                            }
                            .then(
                                if (inSelectionMode) {
                                    Modifier
                                } else {
                                    Modifier.pointerInput(item.id, items.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingId = item.id
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                draggingId = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                val from = items.indexOfFirst { it.id == item.id }.takeIf { it >= 0 } ?: index
                                                val to = LibraryAudioDragMath.targetIndex(
                                                    from,
                                                    dragOffsetY,
                                                    rowHeightPx,
                                                    items.lastIndex,
                                                )
                                                if (to != from) {
                                                    onReorder(from, to)
                                                    dragOffsetY = LibraryAudioDragMath.leftoverOffset(
                                                        dragOffsetY,
                                                        from,
                                                        to,
                                                        rowHeightPx,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                },
                            ),
                        onClick = {
                            if (inSelectionMode) onToggleSelect(item) else onPlayItem(item, index)
                        },
                        onLongClick = { if (inSelectionMode) onLongPressItem(item) },
                        onToggleFavorite = { onToggleFavorite(uri) },
                        onAddToPlaylist = { onAddToPlaylist(item) },
                        onAddToQueue = { onAddToQueue(item) },
                        shareUri = item.localUri,
                        shareMimeType = item.selectedFormat?.mimeType,
                        shareTitle = title,
                        shareIsAudio = true,
                        onDelete = { onDeleteMedia(item) },
                    )
                }
            }
        }
    }
}
