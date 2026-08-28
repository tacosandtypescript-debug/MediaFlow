package com.mediaflow.app.ui.player

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.Playlist
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.player.MpvPlaybackEngine
import com.mediaflow.data.player.background.MediaPlaybackService
import com.mediaflow.data.player.background.PlayerSessionHolder
import com.mediaflow.data.provider.x.live.LiveSpaceEndMonitor
import com.mediaflow.data.provider.x.live.PendingLiveDownloadRepositoryImpl
import com.mediaflow.data.provider.x.live.XSpaceReplayResolver
import com.mediaflow.data.repository.FavoritesRepositoryImpl
import com.mediaflow.data.repository.Media3DownloadRepository
import com.mediaflow.data.repository.MediaStoreGalleryRepository
import com.mediaflow.data.repository.PlaylistRepositoryImpl
import com.mediaflow.data.repository.ProgressRepositoryImpl
import com.mediaflow.data.repository.XSpaceRepositoryImpl
import com.mediaflow.domain.live.LiveSpaceEndState
import com.mediaflow.domain.live.PendingLiveDownload
import com.mediaflow.domain.live.PendingLiveDownloadStatus
import com.mediaflow.domain.live.ReplayResolutionResult
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlaybackEngine
import com.mediaflow.domain.player.PlaybackEvent
import com.mediaflow.domain.player.PlayerService
import com.mediaflow.domain.player.PlayerServiceState
import com.mediaflow.domain.repository.DownloadRequest
import com.mediaflow.domain.repository.FavoritesRepository
import com.mediaflow.domain.repository.GalleryRepository
import com.mediaflow.domain.repository.PlaylistRepository
import com.mediaflow.domain.repository.ProgressRepository
import com.mediaflow.domain.repository.XSpaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating player UI states, gestures, seek feedbacks, and service bridge.
 */
class PlayerViewModel(
    private val app: Application,
    private val playerService: PlayerService,
    private val spaceRepository: XSpaceRepository = XSpaceRepositoryImpl(app),
    private val favoritesRepository: FavoritesRepository = FavoritesRepositoryImpl(app),
    private val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(app),
    private val galleryRepository: GalleryRepository = MediaStoreGalleryRepository(app),
) : AndroidViewModel(app) {

    constructor(application: Application) : this(
        app = application,
        playerService = PlayerSessionHolder.get(application),
        spaceRepository = XSpaceRepositoryImpl(application),
        favoritesRepository = FavoritesRepositoryImpl(application),
        playlistRepository = PlaylistRepositoryImpl(application),
        galleryRepository = MediaStoreGalleryRepository(application),
    )

    private val isControlsVisible = MutableStateFlow(true)
    private val isFullscreen = MutableStateFlow(false)
    private val currentSpace = MutableStateFlow<XSpace?>(null)
    private val seekFeedback = MutableStateFlow<SeekFeedbackEvent?>(null)
    private val isScrubbing = MutableStateFlow(false)
    private val scrubPositionMs = MutableStateFlow(0L)
    private val liveEndState = MutableStateFlow<LiveSpaceEndState>(LiveSpaceEndState.ActiveLive)
    private val isAutoDownloadEnabled = MutableStateFlow(false)

    private val liveEndMonitor by lazy { LiveSpaceEndMonitor() }
    private val pendingDownloadRepo by lazy { PendingLiveDownloadRepositoryImpl(app) }
    private val downloadRepo by lazy { Media3DownloadRepository.get(app) }

    private var hideControlsJob: Job? = null
    private var seekFeedbackJob: Job? = null
    private var openJob: Job? = null

    val uiState: StateFlow<PlayerUiState> = combine(
        playerService.uiState,
        isControlsVisible,
        isFullscreen,
        currentSpace,
        seekFeedback,
        isScrubbing,
        liveEndState,
        isAutoDownloadEnabled,
        favoritesRepository.observeFavoriteMediaUris().flowOn(Dispatchers.IO),
        playlistRepository.observePlaylists().flowOn(Dispatchers.IO),
    ) { args: Array<Any?> ->
        val service = args[0] as PlayerServiceState
        val controls = args[1] as Boolean
        val fullscreen = args[2] as Boolean
        val space = args[3] as? XSpace
        val feedback = args[4] as? SeekFeedbackEvent
        val scrubbing = args[5] as Boolean
        val endState = args[6] as LiveSpaceEndState
        val autoDownload = args[7] as Boolean
        @Suppress("UNCHECKED_CAST")
        val favoriteUris = args[8] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val playlists = args[9] as List<Playlist>

        val uri = service.filePath.orEmpty()
        val isFav = favoriteUris.contains(uri) || (service.mediaId != null && favoriteUris.contains(service.mediaId))

        PlayerUiState(
            mediaUri = uri,
            title = space?.title ?: service.title ?: uri.substringAfterLast('/'),
            serviceState = service,
            isControlsVisible = controls,
            isFullscreen = fullscreen,
            spaceMetadata = space,
            seekFeedback = feedback,
            isScrubbing = scrubbing,
            scrubPositionMs = scrubPositionMs.value,
            liveEndState = endState,
            isAutoDownloadEnabled = autoDownload,
            isFavorite = isFav,
            playlists = playlists,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    init {
        viewModelScope.launch {
            playerService.uiState.collect { state ->
                if (state.isPlaying && isControlsVisible.value && !isScrubbing.value) {
                    scheduleHideControls()
                } else if (!state.isPlaying) {
                    cancelHideControls()
                    isControlsVisible.value = true
                }
            }
        }

        viewModelScope.launch {
            playerService.events.collect { event ->
                when (event) {
                    is PlaybackEvent.PlaybackError -> {
                        val state = playerService.uiState.value
                        val space = currentSpace.value
                        if (state.isLive || space?.isLive == true) {
                            checkSpaceEnded()
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun open(mediaUri: String, title: String? = null, isLive: Boolean = false) {
        openJob?.cancel()
        openJob = viewModelScope.launch {
            val space = spaceRepository.getSpaceForMedia(mediaUri)
            currentSpace.value = space
            val effectiveLive = isLive || space?.isLive == true || (mediaUri.startsWith("http", ignoreCase = true) && space?.isEnded != true && !mediaUri.endsWith(".mp4", ignoreCase = true) && !mediaUri.endsWith(".m4a", ignoreCase = true))

            space?.id?.let { spaceId ->
                isAutoDownloadEnabled.value = pendingDownloadRepo.isAutoDownloadEnabled(spaceId)
            }

            // Check if this media is already open and playing/paused
            val activeState = playerService.uiState.value
            if (activeState.mediaId == mediaUri && activeState.playbackState != EnginePlaybackState.IDLE) {
                return@launch
            }

            if (activeState.mediaId != null && activeState.mediaId != mediaUri) {
                playerService.stop()
            }

            playerService.openMedia(
                mediaId = mediaUri,
                filePath = mediaUri,
                title = space?.title ?: title,
                autoPlay = true,
                isLive = effectiveLive,
            )

            // Start foreground playback service
            MediaPlaybackService.start(
                context = app,
                mediaUri = mediaUri,
                title = space?.title ?: title,
                isLive = effectiveLive,
                spaceId = space?.id,
                spaceUrl = space?.url,
                autoDownloadAfterEnd = isAutoDownloadEnabled.value,
            )
        }
    }

    fun togglePlayPause() {
        showControlsTemporarily()
        if (uiState.value.serviceState.isPlaying) {
            playerService.pause()
        } else {
            playerService.play()
        }
    }

    fun seekTo(positionMs: Long) {
        showControlsTemporarily()
        playerService.seekTo(positionMs)
    }

    fun seekRelative(offsetMs: Long) {
        val current = playerService.uiState.value.currentPositionMs
        val duration = playerService.uiState.value.durationMs
        val target = if (duration > 0L) {
            (current + offsetMs).coerceIn(0L, duration)
        } else {
            (current + offsetMs).coerceAtLeast(0L)
        }
        seekTo(target)

        val event = if (offsetMs >= 0) {
            SeekFeedbackEvent.Forward((offsetMs / 1000).toInt())
        } else {
            SeekFeedbackEvent.Rewind((-offsetMs / 1000).toInt())
        }
        seekFeedback.value = event

        seekFeedbackJob?.cancel()
        seekFeedbackJob = viewModelScope.launch {
            delay(700)
            seekFeedback.value = null
        }
    }

    fun setSpeed(speed: Float) {
        playerService.setSpeed(speed)
    }

    fun setVolume(volume: Int) {
        playerService.setVolume(volume)
    }

    fun toggleMute() {
        val muted = playerService.uiState.value.isMuted
        playerService.setMute(!muted)
    }

    fun setScrubbing(scrubbing: Boolean, positionMs: Long = 0L) {
        isScrubbing.value = scrubbing
        if (scrubbing) {
            scrubPositionMs.value = positionMs
            cancelHideControls()
        } else {
            seekTo(positionMs)
            if (uiState.value.isPlaying) scheduleHideControls()
        }
    }

    fun toggleFullscreen() {
        isFullscreen.value = !isFullscreen.value
    }

    fun toggleControlsVisibility() {
        if (isControlsVisible.value) {
            cancelHideControls()
            isControlsVisible.value = false
        } else {
            showControlsTemporarily()
        }
    }

    fun restart() {
        showControlsTemporarily()
        playerService.restartFromBeginning()
    }

    fun onSurfaceAvailable(surface: Surface) {
        playerService.attachSurface(surface)
    }

    fun onSurfaceDestroyed() {
        playerService.detachSurface()
    }

    fun toggleFavorite() {
        val mediaUri = uiState.value.mediaUri
        if (mediaUri.isNotBlank()) {
            viewModelScope.launch {
                favoritesRepository.toggleFavorite(mediaUri)
            }
        }
    }

    fun playNext() {
        playerService.playNext()
    }

    fun playPrevious() {
        playerService.playPrevious()
    }

    fun skipToIndex(index: Int) {
        playerService.skipToIndex(index)
    }

    fun removeFromQueue(index: Int) {
        playerService.removeFromQueue(index)
    }

    fun toggleMediaInPlaylist(playlistId: String, isIn: Boolean) {
        val uri = uiState.value.mediaUri
        if (uri.isNotBlank()) {
            viewModelScope.launch {
                if (isIn) {
                    playlistRepository.removeMediaFromPlaylist(playlistId, uri)
                } else {
                    playlistRepository.addMediaToPlaylist(playlistId, uri)
                }
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun deleteCurrentMedia(onDeleted: () -> Unit) {
        val mediaUri = uiState.value.mediaUri
        viewModelScope.launch {
            playerService.stop()
            galleryRepository.deleteItem(mediaUri)
            onDeleted()
        }
    }

    fun toggleAutoDownload() {
        val space = currentSpace.value ?: return
        val newEnabled = !isAutoDownloadEnabled.value
        isAutoDownloadEnabled.value = newEnabled
        viewModelScope.launch {
            pendingDownloadRepo.setAutoDownloadEnabled(
                spaceId = space.id,
                title = space.title,
                hostHandle = space.host.formattedHandle,
                sourceUrl = space.url,
                enabled = newEnabled,
            )
        }
    }

    fun checkSpaceEnded() {
        val space = currentSpace.value ?: return
        viewModelScope.launch {
            liveEndState.value = LiveSpaceEndState.EndedResolvingReplay(attempt = 1)
            val result = liveEndMonitor.verifySpaceEnded(space.id, space.url)
            when (result) {
                is ReplayResolutionResult.Available -> {
                    liveEndState.value = LiveSpaceEndState.EndedReplayAvailable(result.replayUrl)
                    if (isAutoDownloadEnabled.value) {
                        downloadSpaceReplay(result.replayUrl)
                    }
                }
                is ReplayResolutionResult.Processing -> {
                    liveEndState.value = LiveSpaceEndState.EndedReplayProcessing(result.message)
                }
                is ReplayResolutionResult.NotAvailable -> {
                    liveEndState.value = LiveSpaceEndState.EndedNoReplay(result.reason)
                }
                is ReplayResolutionResult.Error -> {
                    liveEndState.value = LiveSpaceEndState.EndedReplayProcessing(result.message)
                }
            }
        }
    }

    fun checkReplayAgain() {
        checkSpaceEnded()
    }

    fun downloadSpaceReplay(replayUrl: String) {
        val space = currentSpace.value ?: return
        viewModelScope.launch {
            val request = DownloadRequest(
                sourceUrl = replayUrl,
                mediaType = MediaType.AUDIO,
                formatId = "space_audio_m4a",
                fileName = "Space_${space.host.cleanUsername}_${space.id}.m4a",
                mimeType = "audio/mp4",
                extension = "m4a",
                durationSeconds = space.durationSeconds.takeIf { it > 0 },
            )
            val downloadId = downloadRepo.startDownload(request)
            liveEndState.value = LiveSpaceEndState.EndedDownloadStarted(downloadId)
            pendingDownloadRepo.savePendingDownload(
                PendingLiveDownload(
                    spaceId = space.id,
                    title = space.title,
                    hostHandle = space.host.formattedHandle,
                    sourceUrl = space.url,
                    autoDownloadAfterEnd = true,
                    status = PendingLiveDownloadStatus.DOWNLOADING,
                    replayStreamUrl = replayUrl,
                    downloadId = downloadId,
                )
            )
        }
    }

    private fun showControlsTemporarily() {
        isControlsVisible.value = true
        if (uiState.value.isPlaying && !isScrubbing.value) {
            scheduleHideControls()
        }
    }

    private fun scheduleHideControls() {
        cancelHideControls()
        hideControlsJob = viewModelScope.launch {
            delay(4000)
            if (uiState.value.isPlaying && !isScrubbing.value) {
                isControlsVisible.value = false
            }
        }
    }

    private fun cancelHideControls() {
        hideControlsJob?.cancel()
        hideControlsJob = null
    }

    override fun onCleared() {
        cancelHideControls()
        seekFeedbackJob?.cancel()
        openJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val application: Application,
        private val playerService: PlayerService? = null,
        private val spaceRepository: XSpaceRepository? = null,
        private val favoritesRepository: FavoritesRepository? = null,
        private val playlistRepository: PlaylistRepository? = null,
        private val galleryRepository: GalleryRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(
                app = application,
                playerService = playerService ?: PlayerSessionHolder.get(application),
                spaceRepository = spaceRepository ?: XSpaceRepositoryImpl(application),
                favoritesRepository = favoritesRepository ?: FavoritesRepositoryImpl(application),
                playlistRepository = playlistRepository ?: PlaylistRepositoryImpl(application),
                galleryRepository = galleryRepository ?: MediaStoreGalleryRepository(application),
            ) as T
        }
    }
}
