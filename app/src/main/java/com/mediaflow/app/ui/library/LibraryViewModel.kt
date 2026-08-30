package com.mediaflow.app.ui.library

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediaflow.app.ui.common.media.MediaShare
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.app.ui.library.components.LibraryFilter
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.PlaybackQueueItem
import com.mediaflow.data.player.background.PlayerSessionHolder
import com.mediaflow.app.ui.common.media.isLoadableArtworkUrl
import com.mediaflow.data.repository.FavoritesRepositoryImpl
import com.mediaflow.data.repository.Media3DownloadRepository
import com.mediaflow.data.repository.MediaStoreGalleryRepository
import com.mediaflow.data.repository.PlaylistRepositoryImpl
import com.mediaflow.data.repository.ProgressRepositoryImpl
import com.mediaflow.data.media.LibraryMediaMerge
import com.mediaflow.data.media.VideoFrameThumbnail
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    private val selectionState = MutableStateFlow(LibrarySelection())
    val selection: StateFlow<LibrarySelection> = selectionState
    private val selectedSort = MutableStateFlow(LibrarySort.NEWEST)
    private val videoThumbs = MutableStateFlow<Map<String, String>>(emptyMap())
    private val thumbHarvestMutex = Mutex()
    private val harvestedSources = mutableSetOf<String>()
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
        selectedFilter,
        selectedSort,
        downloadsFlow.flowOn(Dispatchers.IO),
        videoThumbs,
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
        val filter = args[6] as LibraryFilter
        val sort = args[7] as LibrarySort
        @Suppress("UNCHECKED_CAST")
        val downloads = args[8] as List<DownloadItem>
        @Suppress("UNCHECKED_CAST")
        val thumbs = args[9] as Map<String, String>

        val merged = LibraryMediaMerge.merge(galleryResult.getOrDefault(emptyList()), downloads)
        val withFrames = attachCachedVideoThumbs(merged, thumbs)
        scheduleVideoThumbHarvest(withFrames)
        val allItems = LibrarySorter.apply(withFrames, sort)
        val audioItems = allItems.filter { it.mediaType == MediaType.AUDIO }
        val videoItems = allItems.filter { it.mediaType == MediaType.VIDEO }
        val favoriteItems = allItems.filter { item ->
            val uri = item.localUri ?: item.id
            favoriteUris.contains(uri)
        }

        LibraryUiState(
            selectedFilter = filter,
            selectedSort = sort,
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

    fun setFilter(filter: LibraryFilter) {
        selectedFilter.value = filter
    }

    fun setSort(sort: LibrarySort) {
        selectedSort.value = sort
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
            val item = uiState.value.allItems.firstOrNull { it.id == id }
            galleryRepository.deleteItem(id)
            cleanupAfterDelete(listOfNotNull(item))
        }
    }

    fun enterSelection(id: String) {
        selectionState.value = selectionState.value.enter(id)
    }

    fun toggleSelection(id: String) {
        selectionState.value = selectionState.value.toggle(id)
    }

    fun selectAll(ids: Collection<String>) {
        selectionState.value = selectionState.value.selectAll(ids)
    }

    fun clearSelection() {
        selectionState.value = selectionState.value.clear()
    }

    fun shareSelected(context: Context) {
        val selected = selectedItems()
        if (selected.isEmpty()) return
        val uris = selected.mapNotNull { it.localUri ?: it.id.takeIf { id -> id.contains("://") || id.startsWith("/") } }
        val allAudio = selected.all { it.mediaType == MediaType.AUDIO }
        MediaShare.shareMultiple(context, uris, isAudio = allAudio)
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val items = selectedItems()
            if (items.isEmpty()) return@launch
            items.forEach { galleryRepository.deleteItem(it.id) }
            cleanupAfterDelete(items)
            selectionState.value = LibrarySelection()
        }
    }

    private fun selectedItems(): List<DownloadItem> {
        val ids = selectionState.value.selectedIds
        return uiState.value.allItems.filter { it.id in ids }
    }

    private suspend fun cleanupAfterDelete(items: List<DownloadItem>) {
        if (items.isEmpty()) return
        val keys = items.flatMap { item ->
            listOfNotNull(item.id, item.localUri)
        }.toSet()
        keys.forEach { key ->
            favoritesRepository.setFavorite(key, false)
        }
        val playlists = uiState.value.playlists
        playlists.forEach { playlist ->
            keys.forEach { key ->
                if (key in playlist.mediaUris) {
                    playlistRepository.removeMediaFromPlaylist(playlist.id, key)
                }
            }
        }
        val player = playerService.uiState.value
        val current = setOfNotNull(player.mediaId, player.filePath, player.currentQueueItem?.mediaUri)
        if (current.any { it in keys }) {
            playerService.stop()
        }
    }

    fun playQueue(
        items: List<DownloadItem>,
        startIndex: Int,
        context: String? = null,
        shuffle: Boolean = false,
    ) {
        val queue = LibraryAudioQueueBuilder.tapIndex(items, startIndex, shuffle)
        playLibraryAudioQueue(queue, context)
    }

    fun playAllAudio(visible: List<DownloadItem>, context: String = LIBRARY_AUDIO_QUEUE_CONTEXT) {
        playLibraryAudioQueue(LibraryAudioQueueBuilder.playAll(visible), context)
    }

    fun shuffleAllAudio(visible: List<DownloadItem>, context: String = LIBRARY_AUDIO_QUEUE_CONTEXT) {
        playLibraryAudioQueue(LibraryAudioQueueBuilder.shuffleAll(visible), context)
    }

    fun reorderAudioQueueIfActive(
        visible: List<DownloadItem>,
        fromIndex: Int,
        toIndex: Int,
        context: String = LIBRARY_AUDIO_QUEUE_CONTEXT,
    ): List<DownloadItem> {
        val player = playerService.uiState.value
        val isActiveContext = player.playbackContext == context
        val result = LibraryAudioQueueBuilder.reorder(
            items = visible,
            fromIndex = fromIndex,
            toIndex = toIndex,
            currentIndex = if (isActiveContext) player.queueIndex else 0,
            shuffle = player.isShuffle,
        )
        if (isActiveContext) {
            playerService.reorderQueue(fromIndex, toIndex)
        }
        return result.items
    }

    fun playLibraryAudioQueue(
        queue: LibraryAudioQueue<DownloadItem>,
        context: String? = LIBRARY_AUDIO_QUEUE_CONTEXT,
    ) {
        val queueItems = queue.items.map { item ->
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
        playerService.playQueue(queueItems, queue.currentIndex, context, queue.shuffle)
        playerService.setShuffle(queue.shuffle)
    }

    private fun attachCachedVideoThumbs(
        items: List<DownloadItem>,
        thumbs: Map<String, String>,
    ): List<DownloadItem> {
        val app = getApplication<Application>()
        return items.map { item ->
            if (item.mediaType != MediaType.VIDEO) return@map item
            if (!VideoFrameThumbnail.needsFrame(item.thumbnailUri)) return@map item
            val source = item.localUri ?: item.id
            val found = thumbs[source]
                ?: thumbs[item.id]
                ?: VideoFrameThumbnail.cachedUri(app, source)
                ?: item.localUri?.let { VideoFrameThumbnail.cachedUri(app, it) }
            if (found != null) {
                item.copy(thumbnailUri = found)
            } else {
                item
            }
        }
    }

    private fun scheduleVideoThumbHarvest(items: List<DownloadItem>) {
        val pending = items.filter { item ->
            item.mediaType == MediaType.VIDEO &&
                VideoFrameThumbnail.needsFrame(item.thumbnailUri)
        }
        if (pending.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            thumbHarvestMutex.withLock {
                val app = getApplication<Application>()
                val extras = LinkedHashMap<String, String>()
                pending.forEach { item ->
                    val source = item.localUri ?: item.id
                    if (!harvestedSources.add(source)) return@forEach
                    val info = VideoFrameThumbnail.extract(app, source)
                    val uri = info.thumbnailUri ?: return@forEach
                    extras[source] = uri
                    extras[item.id] = uri
                }
                if (extras.isNotEmpty()) {
                    videoThumbs.value = videoThumbs.value + extras
                }
            }
        }
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

    companion object {
        const val LIBRARY_AUDIO_QUEUE_CONTEXT = "Biblioteca"
    }
}

internal fun overlayThumbnails(
    items: List<DownloadItem>,
    downloads: List<DownloadItem>,
): List<DownloadItem> {
    val merged = LibraryMediaMerge.merge(items, downloads)
    val thumbs = HashMap<String, String>()
    downloads.forEach { item ->
        val uri = item.thumbnailUri?.takeIf(::isLoadableArtworkUrl) ?: return@forEach
        thumbs[item.id] = uri
        item.localUri?.let { thumbs[it] = uri }
        thumbs[item.sourceUrl] = uri
    }
    if (thumbs.isEmpty()) return merged
    return merged.map { item ->
        if (isLoadableArtworkUrl(item.thumbnailUri)) item
        else {
            val overlay = sequenceOf(item.localUri, item.id, item.sourceUrl)
                .mapNotNull { key -> key?.let(thumbs::get) }
                .firstOrNull()
            if (overlay != null) item.copy(thumbnailUri = overlay) else item
        }
    }
}
