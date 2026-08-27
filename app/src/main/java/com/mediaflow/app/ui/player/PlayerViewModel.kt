package com.mediaflow.app.ui.player

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.player.MpvPlaybackEngine
import com.mediaflow.data.player.background.MediaPlaybackService
import com.mediaflow.data.player.background.PlayerSessionHolder
import com.mediaflow.data.provider.x.live.LiveSpaceEndMonitor
import com.mediaflow.data.provider.x.live.PendingLiveDownloadRepositoryImpl
import com.mediaflow.data.provider.x.live.XSpaceReplayResolver
import com.mediaflow.data.repository.Media3DownloadRepository
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
import com.mediaflow.domain.repository.ProgressRepository
import com.mediaflow.domain.repository.XSpaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating player UI states, gestures, seek feedbacks, and service bridge.
 */
class PlayerViewModel(
    private val app: Application,
    private val playerService: PlayerService,
    private val spaceRepository: XSpaceRepository = XSpaceRepositoryImpl(app),
) : AndroidViewModel(app) {

    constructor(application: Application) : this(
        app = application,
        playerService = PlayerSessionHolder.get(application),
        spaceRepository = XSpaceRepositoryImpl(application),
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

    val uiState: StateFlow<PlayerUiState> = combine(
        playerService.uiState,
        isControlsVisible,
        isFullscreen,
        currentSpace,
        seekFeedback,
        isScrubbing,
        liveEndState,
        isAutoDownloadEnabled,
    ) { args: Array<Any?> ->
        val service = args[0] as PlayerServiceState
        val controls = args[1] as Boolean
        val fullscreen = args[2] as Boolean
        val space = args[3] as? XSpace
        val feedback = args[4] as? SeekFeedbackEvent
        val scrubbing = args[5] as Boolean
        val endState = args[6] as LiveSpaceEndState
        val autoDownload = args[7] as Boolean

        PlayerUiState(
            mediaUri = service.filePath.orEmpty(),
            title = space?.title ?: service.title ?: service.filePath?.substringAfterLast('/').orEmpty(),
            serviceState = service,
            isControlsVisible = controls,
            isFullscreen = fullscreen,
            spaceMetadata = space,
            seekFeedback = feedback,
            isScrubbing = scrubbing,
            scrubPositionMs = scrubPositionMs.value,
            liveEndState = endState,
            isAutoDownloadEnabled = autoDownload,
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
                    is PlaybackEvent.PlaybackFinished, is PlaybackEvent.PlaybackError -> {
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
        viewModelScope.launch {
            val space = spaceRepository.getSpaceForMedia(mediaUri)
            currentSpace.value = space
            val effectiveLive = isLive || space?.isLive == true || (mediaUri.startsWith("http", ignoreCase = true) && space?.isEnded != true && !mediaUri.endsWith(".mp4", ignoreCase = true) && !mediaUri.endsWith(".m4a", ignoreCase = true))

            space?.id?.let { spaceId ->
                isAutoDownloadEnabled.value = pendingDownloadRepo.isAutoDownloadEnabled(spaceId)
            }

            // Check if this media is already open and playing/paused
            val activeState = playerService.uiState.value
            if (activeState.mediaId == mediaUri && activeState.playbackState != EnginePlaybackState.IDLE) {
                // Reconnecting to already active playback session
                return@launch
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
        showControlsTemporarily()
        val current = playerService.uiState.value.currentPositionMs
        val duration = playerService.uiState.value.durationMs
        val maxPos = if (duration > 0L) duration else Long.MAX_VALUE
        val target = (current + offsetMs).coerceIn(0L, maxPos)
        
        // Trigger Seek Feedback animation
        if (offsetMs > 0) {
            triggerSeekFeedback(SeekFeedbackEvent.Forward())
        } else {
            triggerSeekFeedback(SeekFeedbackEvent.Rewind())
        }

        playerService.seekTo(target)
    }

    fun setScrubbing(scrubbing: Boolean, positionMs: Long = 0L) {
        isScrubbing.value = scrubbing
        if (scrubbing) {
            scrubPositionMs.value = positionMs
            cancelHideControls()
            isControlsVisible.value = true
        } else {
            if (uiState.value.isPlaying) {
                scheduleHideControls()
            }
        }
    }

    fun setSpeed(speed: Float) {
        showControlsTemporarily()
        playerService.setSpeed(speed)
    }

    fun setVolume(volume: Int) {
        showControlsTemporarily()
        playerService.setVolume(volume)
    }

    fun toggleMute() {
        showControlsTemporarily()
        playerService.setMute(!uiState.value.serviceState.isMuted)
    }

    fun toggleFullscreen() {
        isFullscreen.value = !isFullscreen.value
        showControlsTemporarily()
    }

    fun restart() {
        showControlsTemporarily()
        playerService.restartFromBeginning()
    }

    fun toggleControlsVisibility() {
        if (isControlsVisible.value) {
            cancelHideControls()
            isControlsVisible.value = false
        } else {
            showControlsTemporarily()
        }
    }

    fun onSurfaceAvailable(surface: Surface) {
        playerService.attachSurface(surface)
    }

    fun onSurfaceDestroyed() {
        playerService.detachSurface()
    }

    fun showControlsTemporarily() {
        isControlsVisible.value = true
        if (uiState.value.serviceState.isPlaying && !isScrubbing.value) {
            scheduleHideControls()
        }
    }

    private fun triggerSeekFeedback(event: SeekFeedbackEvent) {
        seekFeedbackJob?.cancel()
        seekFeedback.value = event
        seekFeedbackJob = viewModelScope.launch {
            delay(650L)
            seekFeedback.value = null
        }
    }

    private fun scheduleHideControls() {
        cancelHideControls()
        hideControlsJob = viewModelScope.launch {
            delay(3_500L)
            if (uiState.value.serviceState.isPlaying && !isScrubbing.value) {
                isControlsVisible.value = false
            }
        }
    }

    private fun cancelHideControls() {
        hideControlsJob?.cancel()
        hideControlsJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelHideControls()
        seekFeedbackJob?.cancel()
        playerService.release()
    }

    class Factory(
        private val application: Application,
        private val playerService: PlayerService? = null,
        private val spaceRepository: XSpaceRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return if (playerService != null && spaceRepository != null) {
                PlayerViewModel(application, playerService, spaceRepository) as T
            } else if (playerService != null) {
                PlayerViewModel(application, playerService) as T
            } else {
                PlayerViewModel(application) as T
            }
        }
    }
}
