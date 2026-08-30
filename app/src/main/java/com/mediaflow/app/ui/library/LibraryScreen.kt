package com.mediaflow.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaflow.app.ui.common.media.DeleteMediaDialog
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.app.ui.favorites.FavoritesView
import com.mediaflow.app.ui.library.components.AudioLibraryView
import com.mediaflow.app.ui.library.components.LibraryAllView
import com.mediaflow.app.ui.library.components.LibraryFilter
import com.mediaflow.app.ui.library.components.LibraryFilterChips
import com.mediaflow.app.ui.library.components.LibraryHeader
import com.mediaflow.app.ui.library.components.LibraryRecentsBar
import com.mediaflow.app.ui.library.components.VideoLibraryView
import com.mediaflow.app.ui.playlists.PlaylistDetailScreen
import com.mediaflow.app.ui.playlists.PlaylistsView
import com.mediaflow.app.ui.playlists.components.AddToPlaylistSheet
import com.mediaflow.app.ui.playlists.components.CreatePlaylistDialog
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.Playlist

/**
 * Library screen modelled after Spotify's Your Library: chips, recents, list/grid.
 */
@Composable
fun LibraryScreen(
    onOpenItem: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(
        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    )),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val context = LocalContext.current

    var activePlaylistForDetail by remember { mutableStateOf<Playlist?>(null) }
    var itemForAddToPlaylist by remember { mutableStateOf<DownloadItem?>(null) }
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGrid by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    val query = searchQuery.trim()
    fun matchesQuery(text: String?): Boolean =
        query.isEmpty() || (text?.contains(query, ignoreCase = true) == true)

    val visibleAudio = remember(uiState.audioItems, query, uiState.selectedSort) {
        LibrarySorter.apply(uiState.audioItems, uiState.selectedSort)
            .filter { matchesQuery(it.title) || matchesQuery(it.fileName) }
    }
    val visibleVideo = remember(uiState.videoItems, query, uiState.selectedSort) {
        LibrarySorter.apply(uiState.videoItems, uiState.selectedSort)
            .filter { matchesQuery(it.title) || matchesQuery(it.fileName) }
    }
    val visibleFavorites = remember(uiState.favoriteItems, query, uiState.selectedSort) {
        LibrarySorter.apply(uiState.favoriteItems, uiState.selectedSort)
            .filter { matchesQuery(it.title) || matchesQuery(it.fileName) }
    }
    val visiblePlaylists = remember(uiState.playlists, query) {
        uiState.playlists.filter { matchesQuery(it.name) }
    }
    val visibleAll = remember(uiState.allItems, query, uiState.selectedSort) {
        LibrarySorter.apply(uiState.allItems, uiState.selectedSort)
            .filter { matchesQuery(it.title) || matchesQuery(it.fileName) }
    }

    if (activePlaylistForDetail != null) {
        val currentPlaylist = uiState.playlists.firstOrNull { it.id == activePlaylistForDetail?.id }
            ?: activePlaylistForDetail!!

        val mediaMap = remember(uiState.allItems) {
            uiState.allItems.associateBy { it.localUri ?: it.id }
        }
        val playlistItems = currentPlaylist.mediaUris.mapNotNull { mediaMap[it] }

        PlaylistDetailScreen(
            playlist = currentPlaylist,
            items = playlistItems,
            playingMediaId = uiState.playingMediaId,
            isPlayerPlaying = uiState.isPlayerPlaying,
            favoriteUris = uiState.favoriteUris,
            onBack = { activePlaylistForDetail = null },
            onPlayPlaylist = {
                if (playlistItems.isNotEmpty()) {
                    viewModel.playQueue(playlistItems, 0, "Playlist: ${currentPlaylist.name}")
                    onOpenItem(playlistItems.first())
                }
            },
            onPlayItem = { item, index ->
                viewModel.playQueue(playlistItems, index, "Playlist: ${currentPlaylist.name}")
                onOpenItem(item)
            },
            onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
            onRemoveFromPlaylist = { uri ->
                viewModel.removeMediaFromPlaylist(currentPlaylist.id, uri)
            },
            onDeleteMedia = { item -> itemToDelete = item },
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen"),
    ) {
        if (selection.inSelectionMode) {
            LibrarySelectionBar(
                count = selection.count,
                onSelectAll = {
                    val ids = when (uiState.selectedFilter) {
                        LibraryFilter.AUDIO -> visibleAudio.map { it.id }
                        LibraryFilter.VIDEO -> visibleVideo.map { it.id }
                        LibraryFilter.FAVORITES -> visibleFavorites.map { it.id }
                        else -> visibleAll.map { it.id }
                    }
                    viewModel.selectAll(ids)
                },
                onCancel = viewModel::clearSelection,
                onShare = { viewModel.shareSelected(context) },
                onDelete = { confirmDeleteSelected = true },
            )
        } else {
            LibraryHeader(
                searchOpen = searchOpen,
                searchQuery = searchQuery,
                onSearchOpenChange = { open ->
                    searchOpen = open
                    if (!open) searchQuery = ""
                },
                onSearchQueryChange = { searchQuery = it },
                onCreatePlaylist = { showCreatePlaylist = true },
            )
        }
        LibraryFilterChips(
            selected = uiState.selectedFilter,
            onSelect = viewModel::setFilter,
        )
        if (uiState.selectedFilter == LibraryFilter.ALL ||
            uiState.selectedFilter == LibraryFilter.AUDIO ||
            uiState.selectedFilter == LibraryFilter.VIDEO
        ) {
            LibraryRecentsBar(
                isGrid = isGrid,
                onToggleGrid = { isGrid = !isGrid },
                showGridToggle = uiState.selectedFilter == LibraryFilter.ALL,
                selectedSort = uiState.selectedSort,
                onSelectSort = viewModel::setSort,
            )
        }

        when (uiState.selectedFilter) {
            LibraryFilter.ALL -> if (isGrid) {
                VideoLibraryView(
                    items = visibleAll,
                    playingMediaId = uiState.playingMediaId,
                    favoriteUris = uiState.favoriteUris,
                    onPlayItem = { item -> onOpenItem(item) },
                    onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                    onDeleteMedia = { item -> itemToDelete = item },
                    onLongPress = { viewModel.toggleSelection(it.id) },
                    isSelected = { it.id in selection.selectedIds },
                    selectionMode = selection.inSelectionMode,
                )
            } else {
                LibraryAllView(
                    items = visibleAll,
                    playlists = visiblePlaylists,
                    spacesMap = uiState.spacesMap,
                    progressMap = uiState.progressMap,
                    playingMediaId = uiState.playingMediaId,
                    isPlayerPlaying = uiState.isPlayerPlaying,
                    favoriteUris = uiState.favoriteUris,
                    onPlayItem = { item, index ->
                        viewModel.playQueue(visibleAll, index, "Biblioteca")
                        onOpenItem(item)
                    },
                    onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                    onAddToPlaylist = { item -> itemForAddToPlaylist = item },
                    onAddToQueue = { item -> viewModel.addToQueue(item) },
                    onDeleteMedia = { item -> itemToDelete = item },
                    onOpenPlaylist = { playlist -> activePlaylistForDetail = playlist },
                    onPlayPlaylist = { playlist ->
                        val mediaMap = uiState.allItems.associateBy { it.localUri ?: it.id }
                        val playlistItems = playlist.mediaUris.mapNotNull { mediaMap[it] }
                        if (playlistItems.isNotEmpty()) {
                            viewModel.playQueue(playlistItems, 0, "Playlist: ${playlist.name}")
                            onOpenItem(playlistItems.first())
                        }
                    },
                    onRenamePlaylist = { playlistToRename = it },
                    onDeletePlaylist = viewModel::deletePlaylist,
                    selectedIds = selection.selectedIds,
                    inSelectionMode = selection.inSelectionMode,
                    onLongPressItem = { viewModel.enterSelection(it.id) },
                    onToggleSelect = { viewModel.toggleSelection(it.id) },
                )
            }
            LibraryFilter.AUDIO -> AudioLibraryView(
                items = visibleAudio,
                spacesMap = uiState.spacesMap,
                progressMap = uiState.progressMap,
                playingMediaId = uiState.playingMediaId,
                isPlayerPlaying = uiState.isPlayerPlaying,
                favoriteUris = uiState.favoriteUris,
                onPlayAll = {
                    if (visibleAudio.isNotEmpty()) {
                        viewModel.playAllAudio(visibleAudio)
                        onOpenItem(visibleAudio.first())
                    }
                },
                onPlayItem = { item, index ->
                    viewModel.playQueue(visibleAudio, index, LibraryViewModel.LIBRARY_AUDIO_QUEUE_CONTEXT)
                    onOpenItem(item)
                },
                onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                onAddToPlaylist = { item -> itemForAddToPlaylist = item },
                onAddToQueue = { item -> viewModel.addToQueue(item) },
                onDeleteMedia = { item -> itemToDelete = item },
                selectedIds = selection.selectedIds,
                inSelectionMode = selection.inSelectionMode,
                onLongPressItem = { viewModel.enterSelection(it.id) },
                onToggleSelect = { viewModel.toggleSelection(it.id) },
            )
            LibraryFilter.VIDEO -> VideoLibraryView(
                items = visibleVideo,
                playingMediaId = uiState.playingMediaId,
                favoriteUris = uiState.favoriteUris,
                onPlayItem = { item -> onOpenItem(item) },
                onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                onDeleteMedia = { item -> itemToDelete = item },
                onLongPress = { viewModel.toggleSelection(it.id) },
                isSelected = { it.id in selection.selectedIds },
                selectionMode = selection.inSelectionMode,
            )
            LibraryFilter.FAVORITES -> FavoritesView(
                items = visibleFavorites,
                spacesMap = uiState.spacesMap,
                playingMediaId = uiState.playingMediaId,
                isPlayerPlaying = uiState.isPlayerPlaying,
                favoriteUris = uiState.favoriteUris,
                onPlayAllFavorites = {
                    if (visibleFavorites.isNotEmpty()) {
                        viewModel.playQueue(visibleFavorites, 0, "Favoritos")
                        onOpenItem(visibleFavorites.first())
                    }
                },
                onPlayItem = { item, index ->
                    viewModel.playQueue(visibleFavorites, index, "Favoritos")
                    onOpenItem(item)
                },
                onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                onAddToPlaylist = { item -> itemForAddToPlaylist = item },
                onAddToQueue = { item -> viewModel.addToQueue(item) },
                onDeleteMedia = { item -> itemToDelete = item },
            )
            LibraryFilter.PLAYLISTS -> PlaylistsView(
                playlists = visiblePlaylists,
                allMediaItems = uiState.allItems,
                onOpenPlaylist = { playlist -> activePlaylistForDetail = playlist },
                onPlayPlaylist = { playlist ->
                    val mediaMap = uiState.allItems.associateBy { it.localUri ?: it.id }
                    val playlistItems = playlist.mediaUris.mapNotNull { mediaMap[it] }
                    if (playlistItems.isNotEmpty()) {
                        viewModel.playQueue(playlistItems, 0, "Playlist: ${playlist.name}")
                        onOpenItem(playlistItems.first())
                    }
                },
                onCreatePlaylist = viewModel::createPlaylist,
                onRenamePlaylist = viewModel::renamePlaylist,
                onDeletePlaylist = viewModel::deletePlaylist,
            )
        }
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            title = "Nueva playlist",
            confirmLabel = "Crear",
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreatePlaylist = false
            },
            onDismiss = { showCreatePlaylist = false },
        )
    }

    playlistToRename?.let { playlist ->
        CreatePlaylistDialog(
            initialName = playlist.name,
            title = "Renombrar playlist",
            confirmLabel = "Guardar",
            onConfirm = { newName ->
                viewModel.renamePlaylist(playlist.id, newName)
                playlistToRename = null
            },
            onDismiss = { playlistToRename = null },
        )
    }

    // Add to Playlist Sheet
    itemForAddToPlaylist?.let { item ->
        val uri = item.localUri ?: item.id
        AddToPlaylistSheet(
            targetMediaUri = uri,
            playlists = uiState.playlists,
            onToggleMediaInPlaylist = { playlistId, isInPlaylist ->
                viewModel.toggleMediaInPlaylist(playlistId, uri, isInPlaylist)
            },
            onCreateNewPlaylist = { name ->
                viewModel.createPlaylist(name)
            },
            onDismiss = { itemForAddToPlaylist = null },
        )
    }

    // Delete Media Confirmation Dialog
    itemToDelete?.let { item ->
        val space = uiState.spacesMap[item.sourceUrl] ?: uiState.spacesMap[item.id]
        val title = space?.title ?: item.title ?: item.fileName ?: "Audio"
        val artwork = preferredArtworkUrl(item.thumbnailUri, space?.host?.avatarUrl)

        DeleteMediaDialog(
            title = title,
            artworkUrl = artwork,
            onConfirm = {
                viewModel.deleteMediaItem(item.id)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null },
        )
    }

    if (confirmDeleteSelected && selection.count > 0) {
        val first = uiState.allItems.firstOrNull { it.id in selection.selectedIds }
        DeleteMediaDialog(
            title = if (selection.count == 1) {
                first?.title ?: first?.fileName ?: "Media"
            } else {
                "${selection.count} archivos"
            },
            artworkUrl = first?.thumbnailUri,
            itemCount = selection.count,
            onConfirm = {
                viewModel.deleteSelected()
                confirmDeleteSelected = false
            },
            onDismiss = { confirmDeleteSelected = false },
        )
    }
}

@Composable
private fun LibrarySelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)
            .testTag("library_selection_bar"),
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Outlined.Close, contentDescription = "Cancelar")
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSelectAll, modifier = Modifier.testTag("library_select_all")) {
            Icon(Icons.Outlined.SelectAll, contentDescription = "Seleccionar todo")
        }
        IconButton(onClick = onShare, enabled = count > 0) {
            Icon(Icons.Outlined.Share, contentDescription = "Compartir")
        }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Eliminar")
        }
    }
}
