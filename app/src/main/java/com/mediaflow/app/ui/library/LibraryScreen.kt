package com.mediaflow.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

    var activePlaylistForDetail by remember { mutableStateOf<Playlist?>(null) }
    var itemForAddToPlaylist by remember { mutableStateOf<DownloadItem?>(null) }
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGrid by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    val query = searchQuery.trim()
    fun matchesQuery(text: String?): Boolean =
        query.isEmpty() || (text?.contains(query, ignoreCase = true) == true)

    val visibleAudio = remember(uiState.audioItems, query) {
        uiState.audioItems.filter { matchesQuery(it.title) || matchesQuery(it.fileName) }
    }
    val visibleVideo = remember(uiState.videoItems, query) {
        uiState.videoItems.filter { matchesQuery(it.title) || matchesQuery(it.fileName) }
    }
    val visibleFavorites = remember(uiState.favoriteItems, query) {
        uiState.favoriteItems.filter { matchesQuery(it.title) || matchesQuery(it.fileName) }
    }
    val visiblePlaylists = remember(uiState.playlists, query) {
        uiState.playlists.filter { matchesQuery(it.name) }
    }
    val visibleAll = remember(uiState.allItems, query) {
        uiState.allItems
            .sortedByDescending { it.createdAt }
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
                )
            }
            LibraryFilter.AUDIO -> AudioLibraryView(
                items = visibleAudio,
                spacesMap = uiState.spacesMap,
                progressMap = uiState.progressMap,
                playingMediaId = uiState.playingMediaId,
                isPlayerPlaying = uiState.isPlayerPlaying,
                favoriteUris = uiState.favoriteUris,
                onPlayItem = { item, index ->
                    viewModel.playQueue(visibleAudio, index, "Biblioteca")
                    onOpenItem(item)
                },
                onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                onAddToPlaylist = { item -> itemForAddToPlaylist = item },
                onAddToQueue = { item -> viewModel.addToQueue(item) },
                onDeleteMedia = { item -> itemToDelete = item },
            )
            LibraryFilter.VIDEO -> VideoLibraryView(
                items = visibleVideo,
                playingMediaId = uiState.playingMediaId,
                favoriteUris = uiState.favoriteUris,
                onPlayItem = { item -> onOpenItem(item) },
                onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                onDeleteMedia = { item -> itemToDelete = item },
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
}
