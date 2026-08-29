package com.mediaflow.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.app.ui.library.components.AudioLibraryTab
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.PlaybackQueueItem
import com.mediaflow.data.player.background.PlayerSessionHolder
import com.mediaflow.app.ui.common.media.isLoadableArtworkUrl
import com.mediaflow.data.repository.FavoritesRepositoryImpl
import com.mediaflow.data.repository.Media3DownloadRepository
import com.mediaflow.data.repository.MediaStoreGalleryRepository
import com.mediaflow.data.repository.PlaylistRepositoryImpl
import com.mediaflow.data.repository.ProgressRepositoryImpl
import com.mediaflow.data.repository.XSpaceRepositoryImpl
import com.mediaflow.domain.player.PlayerService
import com.mediaflow.domain.repository.DownloadRepository
import com.mediaflow.domain.repository.FavoritesRepository
import com.mediaflow.domain.repository.GalleryRepository
import com.mediaflow.domain.repository.PlaylistRepository
import com.mediaflow.domain.repository.ProgressRepository
import com.mediaflow.domain.repository.XSpaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating library collections (Audio, Video, Playlists, Favorites).
 */
class LibraryViewModel(
    application: Application,
    private val galleryRepository: GalleryRepository = MediaStoreGalleryRepository(application),
    private val progressRepository: ProgressRepository = ProgressRepositoryImpl(application),
    private val spaceRepository: XSpaceRepository = XSpaceRepositoryImpl(application),
    private val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(application),
    private val favoritesRepository: FavoritesRepository = FavoritesRepositoryImpl(application),
    private val playerService: PlayerService = PlayerSessionHolder.get(application),
    downloadRepository: DownloadRepository? = null,
) : AndroidViewModel(application) {

    private val selectedMediaType = MutableStateFlow(MediaType.AUDIO)
    private val selectedAudioTab = MutableStateFlow(AudioLibraryTab.ALL)
    private val downloadsFlow: Flow<List<DownloadItem>> = downloadRepository?.observeDownloads()
        ?: runCatching { Media3DownloadRepository.get(application).observeDownloads() }
            .getOrElse { flowOf(emptyList()) }

    val uiState: StateFlow<LibraryUiState> = combine(
        galleryRepository.observeGallery()
            .map { Result.success(it) }
            .catch { emit(Result.failure(it)) }
            .flowOn(Dispatchers.IO),
        progressRepository.observeAllProgress().flowOn(Dispatchers.IO),
        spaceRepository.observeAllSpaces().flowOn(Dispatchers.IO),
        playlistRepository.observePlaylists().flowOn(Dispatchers.IO),
        favoritesRepository.observeFavoriteMediaUris().flowOn(Dispatchers.IO),
        playerService.uiState,
        selectedMediaType,
        selectedAudioTab,
        downloadsFlow.flowOn(Dispatchers.IO),
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val galleryResult = args[0] as Result<List<DownloadItem>>
        @Suppress("UNCHECKED_CAST")
        val progressMap = args[1] as Map<String, com.mediaflow.core.model.PlaybackProgress>
        @Suppress("UNCHECKED_CAST")
        val spacesMap = args[2] as Map<String, com.mediaflow.core.model.XSpace>
        @Suppress("UNCHECKED_CAST")
        val playlists = args[3] as List<com.mediaflow.core.model.Playlist>
        @Suppress("UNCHECKED_CAST")
        val favoriteUris = args[4] as Set<String>
        val playerState = args[5] as com.mediaflow.domain.player.PlayerServiceState
        val mediaType = args[6] as MediaType
        val audioTab = args[7] as AudioLibraryTab
        @Suppress("UNCHECKED_CAST")
        val downloads = args[8] as List<DownloadItem>

        val allItems = overlayThumbnails(galleryResult.getOrDefault(emptyList()), downloads)
        val audioItems = allItems.filter { it.mediaType == MediaType.AUDIO }
        val videoItems = allItems.filter { it.mediaType == MediaType.VIDEO }
        val favoriteItems = allItems.filter { item ->
            val uri = item.localUri ?: item.id
            favoriteUris.contains(uri)
        }

        LibraryUiState(
            selectedMediaType = mediaType,
            selectedAudioTab = audioTab,
            allItems = allItems,
            audioItems = audioItems,
            videoItems = videoItems,
            favoriteItems = favoriteItems,
            favoriteUris = favoriteUris,
            playlists = playlists,
            spacesMap = spacesMap,
            progressMap = progressMap,
            playingMediaId = playerState.mediaId,
            isPlayerPlaying = playerState.isPlaying,
            isLoading = false,
            errorMessage = galleryResult.exceptionOrNull()?.message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    fun setMediaType(mediaType: MediaType) {
        selectedMediaType.value = mediaType
    }

    fun setAudioTab(tab: AudioLibraryTab) {
        selectedAudioTab.value = tab
    }

    fun toggleFavorite(mediaUri: String) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(mediaUri)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun renamePlaylist(id: String, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(id, newName)
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(id)
        }
    }

    fun toggleMediaInPlaylist(playlistId: String, mediaUri: String, currentlyIn: Boolean) {
        viewModelScope.launch {
            if (currentlyIn) {
                playlistRepository.removeMediaFromPlaylist(playlistId, mediaUri)
            } else {
                playlistRepository.addMediaToPlaylist(playlistId, mediaUri)
            }
        }
    }

    fun removeMediaFromPlaylist(playlistId: String, mediaUri: String) {
        viewModelScope.launch {
            playlistRepository.removeMediaFromPlaylist(playlistId, mediaUri)
        }
    }

    fun deleteMediaItem(id: String) {
        viewModelScope.launch {
            galleryRepository.deleteItem(id)
        }
    }

    fun playQueue(items: List<DownloadItem>, startIndex: Int, context: String? = null) {
        val queueItems = items.map { item ->
            val uri = item.localUri ?: item.id
            val space = uiState.value.spacesMap[item.sourceUrl] ?: uiState.value.spacesMap[item.id]
            PlaybackQueueItem(
                mediaUri = uri,
                title = space?.title ?: item.title ?: item.fileName ?: "Audio",
                artistOrHost = space?.let { "Host: ${it.host.formattedHandle}" } ?: item.fileName,
                durationMs = (item.durationSeconds ?: 0L) * 1000L,
                artworkUrl = preferredArtworkUrl(item.thumbnailUri, space?.host?.avatarUrl),
                isLive = false,
            )
        }
        playerService.playQueue(queueItems, startIndex, context)
    }

    fun addToQueue(item: DownloadItem) {
        val uri = item.localUri ?: item.id
        val space = uiState.value.spacesMap[item.sourceUrl] ?: uiState.value.spacesMap[item.id]
        val queueItem = PlaybackQueueItem(
            mediaUri = uri,
            title = space?.title ?: item.title ?: item.fileName ?: "Audio",
            artistOrHost = space?.let { "Host: ${it.host.formattedHandle}" } ?: item.fileName,
            durationMs = (item.durationSeconds ?: 0L) * 1000L,
            artworkUrl = preferredArtworkUrl(item.thumbnailUri, space?.host?.avatarUrl),
            isLive = false,
        )
        playerService.addToQueue(queueItem)
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(application) as T
        }
    }
}

internal fun overlayThumbnails(
    items: List<DownloadItem>,
    downloads: List<DownloadItem>,
): List<DownloadItem> {
    if (items.isEmpty() || downloads.isEmpty()) return items
    val thumbs = HashMap<String, String>()
    downloads.forEach { item ->
        val uri = item.thumbnailUri?.takeIf(::isLoadableArtworkUrl) ?: return@forEach
        thumbs[item.id] = uri
        item.localUri?.let { thumbs[it] = uri }
        thumbs[item.sourceUrl] = uri
    }
    if (thumbs.isEmpty()) return items
    return items.map { item ->
        if (isLoadableArtworkUrl(item.thumbnailUri)) item
        else {
            val overlay = sequenceOf(item.localUri, item.id, item.sourceUrl)
                .mapNotNull { key -> key?.let(thumbs::get) }
                .firstOrNull()
            if (overlay != null) item.copy(thumbnailUri = overlay) else item
        }
    }
}
