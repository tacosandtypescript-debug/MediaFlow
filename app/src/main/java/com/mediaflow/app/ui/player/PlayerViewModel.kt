package com.mediaflow.app.ui.player

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediaflow.core.model.XSpace
import com.mediaflow.data.player.MpvPlaybackEngine
import com.mediaflow.data.repository.ProgressRepositoryImpl
import com.mediaflow.data.repository.XSpaceRepositoryImpl
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlaybackEngine
import com.mediaflow.domain.player.PlayerService
import com.mediaflow.domain.player.PlayerServiceState
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
    application: Application,
    private val playerService: PlayerService,
    private val spaceRepository: XSpaceRepository = XSpaceRepositoryImpl(application),
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        playerService = PlayerService(
            engine = MpvPlaybackEngine(application),
            progressRepository = ProgressRepositoryImpl(application),
        ),
        spaceRepository = XSpaceRepositoryImpl(application),
    )

    private val isControlsVisible = MutableStateFlow(true)
    private val isFullscreen = MutableStateFlow(false)
    private val currentSpace = MutableStateFlow<XSpace?>(null)
    private val seekFeedback = MutableStateFlow<SeekFeedbackEvent?>(null)
    private val isScrubbing = MutableStateFlow(false)
    private val scrubPositionMs = MutableStateFlow(0L)

    private var hideControlsJob: Job? = null
    private var seekFeedbackJob: Job? = null

    val uiState: StateFlow<PlayerUiState> = combine(
        playerService.uiState,
        isControlsVisible,
        isFullscreen,
        currentSpace,
        seekFeedback,
        isScrubbing,
    ) { args: Array<Any?> ->
        val service = args[0] as PlayerServiceState
        val controls = args[1] as Boolean
        val fullscreen = args[2] as Boolean
        val space = args[3] as? XSpace
        val feedback = args[4] as? SeekFeedbackEvent
        val scrubbing = args[5] as Boolean

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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

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
    }

    fun open(mediaUri: String, title: String? = null) {
        viewModelScope.launch {
            currentSpace.value = spaceRepository.getSpaceForMedia(mediaUri)
        }
        playerService.openMedia(
            mediaId = mediaUri,
            filePath = mediaUri,
            title = title,
            autoPlay = true,
        )
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
