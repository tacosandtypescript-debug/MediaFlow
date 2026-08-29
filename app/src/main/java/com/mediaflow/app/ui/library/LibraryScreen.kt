package com.mediaflow.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaflow.app.ui.common.media.DeleteMediaDialog
import com.mediaflow.app.ui.favorites.FavoritesView
import com.mediaflow.app.ui.library.components.AudioLibraryTab
import com.mediaflow.app.ui.library.components.AudioLibraryView
import com.mediaflow.app.ui.library.components.LibraryMediaSelector
import com.mediaflow.app.ui.library.components.LibraryTabs
import com.mediaflow.app.ui.library.components.VideoLibraryView
import com.mediaflow.app.ui.playlists.PlaylistDetailScreen
import com.mediaflow.app.ui.playlists.PlaylistsView
import com.mediaflow.app.ui.playlists.components.AddToPlaylistSheet
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.Playlist

/**
 * Main Library screen ("Tu biblioteca") with segmented Audio | Video collections,
 * tabs for Todos | Favoritos | Playlists, and seamless playback integration.
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
        // Header
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        // Media Selector: Audio | Video
        LibraryMediaSelector(
            selectedType = uiState.selectedMediaType,
            onSelectType = viewModel::setMediaType,
        )

        Spacer(Modifier.height(4.dp))

        if (uiState.selectedMediaType == MediaType.AUDIO) {
            // Audio Sub-Tabs: Todos | Favoritos | Playlists
            LibraryTabs(
                selectedTab = uiState.selectedAudioTab,
                onSelectTab = viewModel::setAudioTab,
            )

            Spacer(Modifier.height(4.dp))

            when (uiState.selectedAudioTab) {
                AudioLibraryTab.ALL -> {
                    AudioLibraryView(
                        items = uiState.audioItems,
                        spacesMap = uiState.spacesMap,
                        progressMap = uiState.progressMap,
                        playingMediaId = uiState.playingMediaId,
                        isPlayerPlaying = uiState.isPlayerPlaying,
                        favoriteUris = uiState.favoriteUris,
                        onPlayItem = { item, index ->
                            viewModel.playQueue(uiState.audioItems, index, "Biblioteca")
                            onOpenItem(item)
                        },
                        onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                        onAddToPlaylist = { item -> itemForAddToPlaylist = item },
                        onAddToQueue = { item -> viewModel.addToQueue(item) },
                        onDeleteMedia = { item -> itemToDelete = item },
                    )
                }
                AudioLibraryTab.FAVORITES -> {
                    FavoritesView(
                        items = uiState.favoriteItems,
                        spacesMap = uiState.spacesMap,
                        playingMediaId = uiState.playingMediaId,
                        isPlayerPlaying = uiState.isPlayerPlaying,
                        favoriteUris = uiState.favoriteUris,
                        onPlayAllFavorites = {
                            if (uiState.favoriteItems.isNotEmpty()) {
                                viewModel.playQueue(uiState.favoriteItems, 0, "Favoritos")
                                onOpenItem(uiState.favoriteItems.first())
                            }
                        },
                        onPlayItem = { item, index ->
                            viewModel.playQueue(uiState.favoriteItems, index, "Favoritos")
                            onOpenItem(item)
                        },
                        onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                        onAddToPlaylist = { item -> itemForAddToPlaylist = item },
                        onAddToQueue = { item -> viewModel.addToQueue(item) },
                        onDeleteMedia = { item -> itemToDelete = item },
                    )
                }
                AudioLibraryTab.PLAYLISTS -> {
                    PlaylistsView(
                        playlists = uiState.playlists,
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
        } else {
            // Video Library
            VideoLibraryView(
                items = uiState.videoItems,
                playingMediaId = uiState.playingMediaId,
                favoriteUris = uiState.favoriteUris,
                onPlayItem = { item -> onOpenItem(item) },
                onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                onDeleteMedia = { item -> itemToDelete = item },
            )
        }
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
