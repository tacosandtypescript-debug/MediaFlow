package com.mediaflow.app.ui.player

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.Playlist
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.media.metadata.EmbeddedTrackMetadata
import com.mediaflow.data.media.metadata.EmbeddedTrackTags
import com.mediaflow.data.player.background.MediaPlaybackService
import com.mediaflow.data.player.background.PlayerSessionHolder
import com.mediaflow.data.provider.x.XUrlParser
import com.mediaflow.data.provider.x.spaces.XSpaceCapabilities
import com.mediaflow.data.provider.x.live.LiveSpaceEndMonitor
import com.mediaflow.data.provider.x.live.PendingLiveDownloadRepositoryImpl
import com.mediaflow.data.provider.x.live.SpaceDownloadDedup
import com.mediaflow.data.repository.FavoritesRepositoryImpl
import com.mediaflow.data.repository.Media3DownloadRepository
import com.mediaflow.data.repository.MediaStoreGalleryRepository
import com.mediaflow.data.repository.PlaylistRepositoryImpl
import com.mediaflow.data.repository.XSpaceRepositoryImpl
import com.mediaflow.domain.live.LiveSpaceEndState
import com.mediaflow.domain.live.PendingLiveDownload
import com.mediaflow.domain.live.PendingLiveDownloadRepository
import com.mediaflow.domain.live.PendingLiveDownloadStatus
import com.mediaflow.domain.live.ReplayResolutionResult
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlaybackEvent
import com.mediaflow.domain.player.PlayerService
import com.mediaflow.domain.player.PlayerServiceState
import com.mediaflow.domain.player.xspace.XSpaceLivePlayerEvent
import com.mediaflow.domain.player.xspace.XSpaceLivePlayerMachine
import com.mediaflow.domain.player.xspace.XSpaceLivePlayerState
import com.mediaflow.domain.repository.DownloadRepository
import com.mediaflow.domain.repository.DownloadRequest
import com.mediaflow.domain.repository.FavoritesRepository
import com.mediaflow.domain.repository.GalleryRepository
import com.mediaflow.domain.repository.PlaylistRepository
import com.mediaflow.domain.repository.XSpaceRepository
import com.mediaflow.domain.usecase.StartDownloadUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    private val liveEndMonitor: LiveSpaceEndMonitor = LiveSpaceEndMonitor(),
    private val pendingDownloadRepo: PendingLiveDownloadRepository = PendingLiveDownloadRepositoryImpl(app),
    downloadRepository: DownloadRepository? = null,
    private val readEmbeddedMetadata: (String) -> EmbeddedTrackTags = { uri ->
        EmbeddedTrackMetadata.read(app, uri)
    },
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
    private val embeddedTags = MutableStateFlow(EmbeddedTrackTags())
    private val taggedMediaUri = MutableStateFlow<String?>(null)
    private val liveEndState = MutableStateFlow<LiveSpaceEndState>(LiveSpaceEndState.ActiveLive)
    private val isAutoDownloadEnabled = MutableStateFlow(false)
    private val spacePlayer = MutableStateFlow(XSpaceLivePlayerMachine.initial())

    private val downloadRepo: DownloadRepository = downloadRepository ?: Media3DownloadRepository.get(app)

    private var hideControlsJob: Job? = null
    private var seekFeedbackJob: Job? = null
    private var openJob: Job? = null
    private var liveEndJob: Job? = null
    private val downloadMutex = Mutex()

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
        scrubPositionMs,
        embeddedTags,
        taggedMediaUri,
        spacePlayer,
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
        val scrubPos = args[10] as Long
        val rawTags = args[11] as EmbeddedTrackTags
        val tagsUri = args[12] as? String
        val spaceSession = args[13] as XSpaceLivePlayerState
        val uri = service.filePath.orEmpty()
        val tags = if (PlayerDisplayMetadata.tagsBelongToCurrent(tagsUri, service.filePath, service.mediaId)) {
            rawTags
        } else {
            EmbeddedTrackTags()
        }
        val isFav = favoriteUris.contains(uri) || (service.mediaId != null && favoriteUris.contains(service.mediaId))
        val artist = space?.let { "Host: ${it.host.formattedHandle}" }
            ?: PlayerDisplayMetadata.artist(tags.artist, service.artistOrHost)
        val title = PlayerDisplayMetadata.title(
            taggedTitle = tags.title ?: space?.title,
            serviceTitle = service.title,
            fileName = uri.substringAfterLast('/'),
            uri = uri,
        )

        PlayerUiState(
            mediaUri = uri,
            title = title,
            artist = artist,
            album = PlayerDisplayMetadata.album(tags.album),
            artworkUri = preferredArtworkUrl(tags.artworkUri, service.artworkUrl),
            subtitle = artist,
            serviceState = service,
            isControlsVisible = controls,
            isFullscreen = fullscreen,
            spaceMetadata = space,
            seekFeedback = feedback,
            isScrubbing = scrubbing,
            scrubPositionMs = scrubPos,
            fileDurationMs = tags.durationMs,
            liveEndState = endState,
            isAutoDownloadEnabled = autoDownload,
            isFavorite = isFav,
            playlists = playlists,
            spacePlayer = spaceSession,
            spaceCapabilities = space?.let { XSpaceCapabilities.from(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    init {
        viewModelScope.launch {
            playerService.uiState.collect { state ->
                val trackUri = state.filePath ?: state.mediaId
                if (!trackUri.isNullOrBlank() && trackUri != taggedMediaUri.value) {
                    refreshEmbeddedTags(trackUri, state.isLive)
                }
                if (state.isPlaying && isControlsVisible.value && !isScrubbing.value) {
                    scheduleHideControls()
                } else if (!state.isPlaying) {
                    cancelHideControls()
                    isControlsVisible.value = true
                }
                syncSpacePlayerFromEngine(state)
            }
        }

        viewModelScope.launch {
            playerService.events.collect { event ->
                when (event) {
                    is PlaybackEvent.PlaybackError,
                    is PlaybackEvent.PlaybackFinished,
                    -> {
                        val state = playerService.uiState.value
                        val space = currentSpace.value
                        if (state.isLive || space?.isLive == true) {
                            reduceSpacePlayer(XSpaceLivePlayerEvent.IngestEnded)
                            checkSpaceEnded()
                        }
                    }
                    else -> Unit
                }
            }
        }

        viewModelScope.launch {
            pendingDownloadRepo.observePendingDownloads().collect { pendingList ->
                val spaceId = currentSpace.value?.id ?: return@collect
                val pending = pendingList.firstOrNull { it.spaceId == spaceId } ?: return@collect
                isAutoDownloadEnabled.value = pending.autoDownloadAfterEnd
                when (pending.status) {
                    PendingLiveDownloadStatus.DOWNLOADING,
                    PendingLiveDownloadStatus.COMPLETED,
                    -> {
                        val downloadId = pending.downloadId
                        if (!downloadId.isNullOrBlank()) {
                            liveEndState.value = LiveSpaceEndState.EndedDownloadStarted(downloadId)
                        }
                    }
                    PendingLiveDownloadStatus.RESOLVING_REPLAY -> {
                        if (liveEndState.value is LiveSpaceEndState.ActiveLive) {
                            liveEndState.value = LiveSpaceEndState.EndedReplayProcessing(
                                message = "Esperando repetición",
                                attempt = pending.attemptCount.coerceAtLeast(1),
                            )
                        }
                    }
                    PendingLiveDownloadStatus.READY_TO_DOWNLOAD -> {
                        val replay = pending.replayStreamUrl
                        if (!replay.isNullOrBlank() && liveEndState.value !is LiveSpaceEndState.EndedDownloadStarted) {
                            liveEndState.value = LiveSpaceEndState.EndedReplayAvailable(replay)
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
            val space = resolveSpaceForMedia(mediaUri)
            currentSpace.value = space
            val effectiveLive = isLive || space?.isLive == true
            val caps = space?.let { XSpaceCapabilities.from(it) }
            if (space?.isEnded == true) {
                reduceSpacePlayer(
                    XSpaceLivePlayerEvent.OpenReplay(seekAllowed = caps?.stream?.seekSupported == true),
                )
            } else if (effectiveLive || space?.isLive == true) {
                reduceSpacePlayer(
                    XSpaceLivePlayerEvent.OpenLive(liveSeekAllowed = caps?.liveSeekAllowed == true),
                )
            }

            space?.id?.let { spaceId ->
                isAutoDownloadEnabled.value = pendingDownloadRepo.isAutoDownloadEnabled(spaceId)
            }

            val isRemote = mediaUri.startsWith("http://", ignoreCase = true) ||
                mediaUri.startsWith("https://", ignoreCase = true)
            val tags = if (!effectiveLive && !isRemote) {
                runCatching {
                    withContext(Dispatchers.IO) { readEmbeddedMetadata(mediaUri) }
                }.getOrDefault(EmbeddedTrackTags())
            } else {
                EmbeddedTrackTags()
            }
            embeddedTags.value = tags
            taggedMediaUri.value = mediaUri

            val download = withTimeoutOrNull(1_500) {
                val items = downloadRepo.observeDownloads().first()
                items.firstOrNull { item -> matchesDownload(item, mediaUri) }
            }
            val artwork = tags.artworkUri ?: download?.thumbnailUri
            embeddedTags.value = tags.copy(artworkUri = artwork)
            taggedMediaUri.value = mediaUri
            val resolvedTitle = PlayerDisplayMetadata.title(
                taggedTitle = tags.title ?: space?.title ?: title,
                serviceTitle = download?.title,
                fileName = download?.fileName ?: mediaUri.substringAfterLast('/'),
                uri = mediaUri,
            )

            val activeState = playerService.uiState.value
            val queueIndex = activeState.queue.indexOfFirst { it.mediaUri == mediaUri }
            val alreadyThisTrack = activeState.mediaId == mediaUri &&
                activeState.playbackState != EnginePlaybackState.IDLE
            if (alreadyThisTrack || (queueIndex >= 0 && activeState.queueIndex == queueIndex &&
                    activeState.playbackState != EnginePlaybackState.IDLE)
            ) {
                startPlaybackService(
                    mediaUri = mediaUri,
                    resolvedTitle = resolvedTitle,
                    space = space,
                    tags = tags,
                    artwork = artwork,
                    preservedArtwork = activeState.artworkUrl.takeIf {
                        activeState.mediaId == mediaUri || activeState.filePath == mediaUri
                    },
                    effectiveLive = effectiveLive,
                )
                return@launch
            }

            if (queueIndex >= 0) {
                playerService.skipToIndex(queueIndex)
                startPlaybackService(
                    mediaUri = mediaUri,
                    resolvedTitle = resolvedTitle,
                    space = space,
                    tags = tags,
                    artwork = artwork,
                    preservedArtwork = activeState.queue.getOrNull(queueIndex)?.artworkUrl,
                    effectiveLive = effectiveLive,
                )
                return@launch
            }

            if (activeState.mediaId != null && activeState.mediaId != mediaUri) {
                playerService.stop()
            }

            val preservedArtwork = activeState.artworkUrl.takeIf {
                activeState.mediaId == mediaUri || activeState.filePath == mediaUri
            }
            playerService.openMedia(
                mediaId = mediaUri,
                filePath = mediaUri,
                title = resolvedTitle,
                artistOrHost = space?.let { "Host: ${it.host.formattedHandle}" }
                    ?: PlayerDisplayMetadata.artist(tags.artist, null),
                artworkUrl = preferredArtworkUrl(
                    artwork ?: preservedArtwork,
                    space?.host?.avatarUrl,
                ),
                autoPlay = true,
                isLive = effectiveLive,
            )

            startPlaybackService(
                mediaUri = mediaUri,
                resolvedTitle = resolvedTitle,
                space = space,
                tags = tags,
                artwork = artwork,
                preservedArtwork = preservedArtwork,
                effectiveLive = effectiveLive,
            )
        }
    }

    private fun startPlaybackService(
        mediaUri: String,
        resolvedTitle: String,
        space: XSpace?,
        tags: EmbeddedTrackTags,
        artwork: String?,
        preservedArtwork: String?,
        effectiveLive: Boolean,
    ) {
        MediaPlaybackService.start(
            context = app,
            mediaUri = mediaUri,
            title = resolvedTitle,
            artist = space?.let { "Host: ${it.host.formattedHandle}" }
                ?: PlayerDisplayMetadata.artist(tags.artist, null),
            artworkUrl = preferredArtworkUrl(
                artwork ?: preservedArtwork,
                space?.host?.avatarUrl,
            ),
            isLive = effectiveLive,
            spaceId = space?.id,
            spaceUrl = space?.url,
            autoDownloadAfterEnd = isAutoDownloadEnabled.value,
        )
    }

    fun togglePlayPause() {
        showControlsTemporarily()
        val state = uiState.value.serviceState
        when {
            state.isPlaying -> {
                playerService.pause()
                reduceSpacePlayer(XSpaceLivePlayerEvent.Pause)
            }
            state.isEnded -> {
                playerService.seekTo(0L)
                playerService.play()
                reduceSpacePlayer(XSpaceLivePlayerEvent.Resume)
            }
            else -> {
                playerService.play()
                reduceSpacePlayer(XSpaceLivePlayerEvent.Resume)
            }
        }
    }

    fun jumpToLiveEdge() {
        showControlsTemporarily()
        reduceSpacePlayer(XSpaceLivePlayerEvent.JumpToLiveEdge)
        playerService.play()
    }

    fun seekTo(positionMs: Long) {
        showControlsTemporarily()
        playerService.seekTo(positionMs)
    }

    fun seekRelative(offsetMs: Long) {
        val current = uiState.value.currentPositionMs
        val duration = uiState.value.durationMs
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

    fun setScrubbing(scrubbing: Boolean, positionMs: Long? = null) {
        isScrubbing.value = scrubbing
        if (positionMs != null) {
            scrubPositionMs.value = positionMs
        }
        if (scrubbing) {
            cancelHideControls()
        } else if (uiState.value.isPlaying) {
            scheduleHideControls()
        }
    }

    fun updateScrubPosition(positionMs: Long) {
        scrubPositionMs.value = positionMs.coerceAtLeast(0L)
        if (!isScrubbing.value) isScrubbing.value = true
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
        playerService.nextTrack()
    }

    fun playPrevious() {
        playerService.previousTrack()
    }

    private fun refreshEmbeddedTags(trackUri: String, isLive: Boolean) {
        viewModelScope.launch {
            taggedMediaUri.value = trackUri
            val isRemote = trackUri.startsWith("http://", ignoreCase = true) ||
                trackUri.startsWith("https://", ignoreCase = true)
            val tags = if (!isLive && !isRemote) {
                runCatching {
                    withContext(Dispatchers.IO) { readEmbeddedMetadata(trackUri) }
                }.getOrDefault(EmbeddedTrackTags())
            } else {
                EmbeddedTrackTags()
            }
            if (taggedMediaUri.value == trackUri) {
                val download = withTimeoutOrNull(800) {
                    downloadRepo.observeDownloads().first()
                        .firstOrNull { item -> matchesDownload(item, trackUri) }
                }
                embeddedTags.value = tags.copy(
                    artworkUri = tags.artworkUri ?: download?.thumbnailUri,
                )
            }
        }
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
        viewModelScope.launch {
            pendingDownloadRepo.setAutoDownloadEnabled(
                spaceId = space.id,
                title = space.title,
                hostHandle = space.host.formattedHandle,
                sourceUrl = space.url,
                enabled = newEnabled,
            )
            isAutoDownloadEnabled.value = newEnabled
        }
    }

    fun checkSpaceEnded() {
        val space = currentSpace.value ?: return
        if (liveEndJob?.isActive == true) return
        if (liveEndState.value !is LiveSpaceEndState.ActiveLive) return
        liveEndJob = viewModelScope.launch {
            val pending = pendingDownloadRepo.getPendingDownload(space.id)
            when (pending?.status) {
                PendingLiveDownloadStatus.DOWNLOADING,
                PendingLiveDownloadStatus.COMPLETED,
                -> {
                    val downloadId = pending.downloadId
                    if (!downloadId.isNullOrBlank()) {
                        liveEndState.value = LiveSpaceEndState.EndedDownloadStarted(downloadId)
                    }
                    applyEndedSpace(space)
                    return@launch
                }
                PendingLiveDownloadStatus.RESOLVING_REPLAY -> {
                    liveEndState.value = LiveSpaceEndState.EndedReplayProcessing(
                        message = "Esperando repetición",
                        attempt = pending.attemptCount.coerceAtLeast(1),
                    )
                    applyEndedSpace(space)
                    return@launch
                }
                PendingLiveDownloadStatus.READY_TO_DOWNLOAD -> {
                    val replay = pending.replayStreamUrl
                    if (!replay.isNullOrBlank()) {
                        liveEndState.value = LiveSpaceEndState.EndedReplayAvailable(replay)
                    }
                    applyEndedSpace(space)
                    return@launch
                }
                else -> Unit
            }

            // Overlay only. Service / LiveSpaceEndHandler owns wait + auto-download.
            val result = liveEndMonitor.verifySpaceEnded(space.id, space.url)
            applyReplayResult(space, result, startDownloadIfAuto = false)
        }
    }

    fun checkReplayAgain() {
        if (liveEndJob?.isActive == true) return
        liveEndJob = viewModelScope.launch {
            val space = currentSpace.value ?: return@launch
            liveEndState.value = LiveSpaceEndState.EndedResolvingReplay(attempt = 1)
            val result = liveEndMonitor.waitForReplay(space.id, space.url) { attempt, message ->
                liveEndState.value = LiveSpaceEndState.EndedReplayProcessing(
                    message = message,
                    attempt = attempt,
                )
            }
            applyReplayResult(space, result, startDownloadIfAuto = false)
        }
    }

    fun downloadSpaceReplay(replayUrl: String) {
        val space = currentSpace.value ?: return
        viewModelScope.launch {
            downloadMutex.withLock {
                val pending = pendingDownloadRepo.getPendingDownload(space.id)
                val downloads = downloadRepo.observeDownloads().first()
                if (SpaceDownloadDedup.shouldSkipDownload(space.id, pending, downloads)) {
                    val existingId = pending?.downloadId ?: downloads.firstOrNull {
                        SpaceDownloadDedup.matchesSpace(space.id, it)
                    }?.id
                    if (!existingId.isNullOrBlank()) {
                        liveEndState.value = LiveSpaceEndState.EndedDownloadStarted(existingId)
                    }
                    return@withLock
                }
                val request = DownloadRequest(
                    sourceUrl = space.url.ifBlank { replayUrl },
                    mediaType = MediaType.AUDIO,
                    formatId = "space_audio_m4a",
                    fileName = SpaceDownloadDedup.fileName(space.host.cleanUsername, space.id),
                    mimeType = "audio/mp4",
                    extension = "m4a",
                    durationSeconds = space.durationSeconds.takeIf { it > 0 },
                    thumbnailUrl = space.host.avatarUrl,
                )
                val downloadId = StartDownloadUseCase(downloadRepo)(request)
                liveEndState.value = LiveSpaceEndState.EndedDownloadStarted(downloadId)
                pendingDownloadRepo.savePendingDownload(
                    PendingLiveDownload(
                        spaceId = space.id,
                        title = space.title,
                        hostHandle = space.host.formattedHandle,
                        sourceUrl = space.url,
                        autoDownloadAfterEnd = isAutoDownloadEnabled.value,
                        status = PendingLiveDownloadStatus.DOWNLOADING,
                        replayStreamUrl = replayUrl,
                        downloadId = downloadId,
                    ),
                )
            }
        }
    }

    private suspend fun applyReplayResult(
        space: XSpace,
        result: ReplayResolutionResult,
        startDownloadIfAuto: Boolean,
    ) {
        when (result) {
            is ReplayResolutionResult.Available -> {
                val ended = space.copy(
                    state = XSpaceState.ENDED,
                    audioStreamUrl = result.replayUrl,
                    recordingAvailable = result.space.recordingAvailable,
                    durationSeconds = result.space.durationSeconds.takeIf { it > 0 } ?: space.durationSeconds,
                    endedAtMs = result.space.endedAtMs ?: space.endedAtMs,
                )
                applyEndedSpace(ended)
                liveEndState.value = LiveSpaceEndState.EndedReplayAvailable(result.replayUrl)
                if (startDownloadIfAuto && isAutoDownloadEnabled.value) {
                    downloadSpaceReplay(result.replayUrl)
                }
            }
            is ReplayResolutionResult.Processing -> {
                val stillLive = LiveSpaceEndMonitor.isStillBroadcasting(result)
                if (stillLive) {
                    liveEndState.value = LiveSpaceEndState.ActiveLive
                } else {
                    applyEndedSpace(space)
                    liveEndState.value = LiveSpaceEndState.EndedReplayProcessing(result.message)
                }
            }
            is ReplayResolutionResult.NotAvailable -> {
                applyEndedSpace(space)
                val canRetry = result.reason == LiveSpaceEndMonitor.REPLAY_TIMEOUT_MESSAGE
                liveEndState.value = LiveSpaceEndState.EndedNoReplay(result.reason, canCheckAgain = canRetry)
            }
            is ReplayResolutionResult.Error -> {
                liveEndState.value = LiveSpaceEndState.EndedReplayProcessing(result.message)
            }
        }
    }

    private suspend fun applyEndedSpace(space: XSpace) {
        val ended = if (space.isEnded) space else space.copy(state = XSpaceState.ENDED)
        currentSpace.value = ended
        spaceRepository.saveSpace(ended, mediaId = ended.url)
        playerService.markBroadcastEnded()
        val caps = XSpaceCapabilities.from(ended)
        if (!ended.audioStreamUrl.isNullOrBlank()) {
            reduceSpacePlayer(XSpaceLivePlayerEvent.StartReplay(seekAllowed = caps.stream.seekSupported))
        } else {
            reduceSpacePlayer(XSpaceLivePlayerEvent.IngestEnded)
        }
    }

    private fun reduceSpacePlayer(event: XSpaceLivePlayerEvent) {
        spacePlayer.value = XSpaceLivePlayerMachine.reduce(spacePlayer.value, event)
    }

    private fun syncSpacePlayerFromEngine(state: PlayerServiceState) {
        val space = currentSpace.value ?: return
        when (state.playbackState) {
            EnginePlaybackState.PREPARING -> reduceSpacePlayer(XSpaceLivePlayerEvent.Buffering)
            EnginePlaybackState.PLAYING -> {
                if (space.isLive && spacePlayer.value.liveLagMs == 0L) {
                    reduceSpacePlayer(XSpaceLivePlayerEvent.ConnectedAtLiveEdge)
                }
            }
            EnginePlaybackState.ERROR -> reduceSpacePlayer(XSpaceLivePlayerEvent.Error())
            else -> Unit
        }
    }

    private fun matchesDownload(item: DownloadItem, mediaUri: String): Boolean {
        if (item.localUri == mediaUri || item.id == mediaUri || item.sourceUrl == mediaUri) return true
        val needle = mediaUri.substringAfterLast('/').substringBefore('?')
        if (needle.isBlank()) return false
        if (item.id == needle || item.fileName == needle) return true
        val itemLast = item.localUri?.substringAfterLast('/')?.substringBefore('?')
        return itemLast == needle
    }

    private suspend fun resolveSpaceForMedia(mediaUri: String): XSpace? {
        spaceRepository.getSpaceForMedia(mediaUri)?.let { return it }
        val parsedId = XUrlParser.extractDirectSpaceId(mediaUri)
        if (parsedId != null) {
            spaceRepository.getSpace(parsedId)?.let { return it }
        }
        return null
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
        liveEndJob?.cancel()
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
