package com.mediaflow.domain.player

import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.PlaybackStatus
import com.mediaflow.domain.repository.ProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** State exposed by [PlayerService] to the UI. */
data class PlayerServiceState(
    val mediaId: String? = null,
    val filePath: String? = null,
    val title: String? = null,
    val playbackState: EnginePlaybackState = EnginePlaybackState.IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val volume: Int = 100,
    val isMuted: Boolean = false,
    val isAudioOnly: Boolean = false,
    val isVideoAvailable: Boolean = false,
    val status: PlaybackStatus = PlaybackStatus.NEW,
    val errorMessage: String? = null,
) {
    val isPlaying: Boolean
        get() = playbackState == EnginePlaybackState.PLAYING

    val isPaused: Boolean
        get() = playbackState == EnginePlaybackState.PAUSED

    val isEnded: Boolean
        get() = playbackState == EnginePlaybackState.ENDED

    val isError: Boolean
        get() = playbackState == EnginePlaybackState.ERROR

    val progressFraction: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Service managing media sessions, engine interactions, periodic progress persistence,
 * smart resumption, and lifecycle-safe shutdown.
 */
class PlayerService(
    private val engine: PlaybackEngine,
    private val progressRepository: ProgressRepository,
    private val completionPolicy: CompletionPolicy = CompletionPolicy(),
    coroutineScope: CoroutineScope? = null,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val progressSaveIntervalMs: Long = 1_000L,
) {
    private val ownsScope = coroutineScope == null
    private val serviceScope = coroutineScope ?: CoroutineScope(SupervisorJob() + mainDispatcher)
    private val mutex = Mutex()

    private val _uiState = MutableStateFlow(PlayerServiceState())
    val uiState: StateFlow<PlayerServiceState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 64)
    val events: Flow<PlaybackEvent> = _events.asSharedFlow()

    private var periodicSaveJob: Job? = null
    private var isReleased = false

    init {
        // Collect engine state
        serviceScope.launch {
            engine.state.collect { engineState ->
                _uiState.value = _uiState.value.copy(
                    playbackState = engineState.playbackState,
                    currentPositionMs = engineState.currentPositionMs,
                    durationMs = engineState.durationMs,
                    speed = engineState.speed,
                    volume = engineState.volume,
                    isMuted = engineState.isMuted,
                    isAudioOnly = engineState.isAudioOnly,
                    isVideoAvailable = engineState.isVideoAvailable,
                    errorMessage = engineState.errorMessage,
                )

                if (engineState.isPlaying) {
                    startPeriodicSave()
                } else {
                    stopPeriodicSave()
                    if (engineState.isPaused || engineState.isEnded) {
                        saveCurrentProgressNow(isEof = engineState.isEnded)
                    }
                }
            }
        }

        // Forward engine events
        serviceScope.launch {
            engine.events.collect { event ->
                when (event) {
                    is PlaybackEvent.PositionChanged -> {
                        val isComplete = completionPolicy.isCompleted(
                            positionMs = event.positionMs,
                            durationMs = event.durationMs,
                        )
                        if (isComplete && _uiState.value.status != PlaybackStatus.COMPLETED) {
                            _uiState.value = _uiState.value.copy(status = PlaybackStatus.COMPLETED)
                            saveCurrentProgressNow(isEof = false)
                        }
                    }
                    is PlaybackEvent.PlaybackFinished -> {
                        _uiState.value = _uiState.value.copy(status = PlaybackStatus.COMPLETED)
                        saveCurrentProgressNow(isEof = true)
                    }
                    is PlaybackEvent.PlaybackPaused -> {
                        saveCurrentProgressNow(isEof = false)
                    }
                    else -> Unit
                }
                _events.emit(event)
            }
        }
    }

    /**
     * Opens a media item, resolving saved progress and configuring smart resumption.
     */
    fun openMedia(
        mediaId: String,
        filePath: String,
        title: String? = null,
        autoPlay: Boolean = true,
    ) {
        if (isReleased) return

        serviceScope.launch {
            mutex.withLock {
                // If another media was active, save its progress before opening new one
                if (_uiState.value.mediaId != null && _uiState.value.mediaId != mediaId) {
                    saveCurrentProgressNow()
                }

                val saved = progressRepository.getProgress(mediaId)
                val currentStatus = saved?.status ?: PlaybackStatus.NEW
                val startPos = if (saved != null) {
                    completionPolicy.computeResumePosition(
                        savedPositionMs = saved.currentPositionMs,
                        totalDurationMs = saved.totalDurationMs,
                        status = currentStatus,
                    )
                } else 0L

                val nextPlayCount = (saved?.playCount ?: 0) + 1
                val initialStatus = if (currentStatus == PlaybackStatus.COMPLETED) PlaybackStatus.COMPLETED else PlaybackStatus.IN_PROGRESS

                val initialProgress = (saved ?: PlaybackProgress.new(mediaId, filePath)).copy(
                    currentPositionMs = startPos,
                    status = initialStatus,
                    lastPlayedAt = System.currentTimeMillis(),
                    playCount = nextPlayCount,
                )
                progressRepository.saveProgress(initialProgress)

                _uiState.value = PlayerServiceState(
                    mediaId = mediaId,
                    filePath = filePath,
                    title = title ?: filePath.substringAfterLast('/'),
                    playbackState = EnginePlaybackState.PREPARING,
                    currentPositionMs = startPos,
                    durationMs = saved?.totalDurationMs ?: 0L,
                    status = initialStatus,
                )

                engine.load(mediaSource = filePath, startPositionMs = startPos, autoPlay = autoPlay)
            }
        }
    }

    /**
     * Restarts playback from 0:00.
     */
    fun restartFromBeginning() {
        if (isReleased) return
        val current = _uiState.value
        val mediaId = current.mediaId ?: return

        serviceScope.launch {
            mutex.withLock {
                engine.seekTo(0L)
                val updated = PlaybackProgress(
                    mediaId = mediaId,
                    filePath = current.filePath ?: mediaId,
                    totalDurationMs = current.durationMs,
                    currentPositionMs = 0L,
                    status = PlaybackStatus.IN_PROGRESS,
                    lastPlayedAt = System.currentTimeMillis(),
                )
                progressRepository.saveProgress(updated)
                _uiState.value = _uiState.value.copy(
                    currentPositionMs = 0L,
                    status = PlaybackStatus.IN_PROGRESS,
                )
                engine.play()
            }
        }
    }

    fun play() {
        if (isReleased) return
        engine.play()
    }

    fun pause() {
        if (isReleased) return
        engine.pause()
        serviceScope.launch {
            saveCurrentProgressNow(isEof = false)
        }
    }

    fun seekTo(positionMs: Long) {
        if (isReleased) return
        engine.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
        serviceScope.launch {
            saveCurrentProgressNow(isEof = false)
        }
    }

    fun setSpeed(speed: Float) {
        if (isReleased) return
        engine.setSpeed(speed)
    }

    fun setVolume(volume: Int) {
        if (isReleased) return
        engine.setVolume(volume)
    }

    fun setMute(muted: Boolean) {
        if (isReleased) return
        engine.setMute(muted)
    }

    fun attachSurface(surface: Any?) {
        if (isReleased) return
        engine.attachSurface(surface)
    }

    fun detachSurface() {
        if (isReleased) return
        engine.detachSurface()
    }

    /**
     * Resets progress for the currently open media or specific mediaId.
     */
    suspend fun resetProgress(mediaId: String? = null) {
        val targetId = mediaId ?: _uiState.value.mediaId ?: return
        progressRepository.resetProgress(targetId)
        if (targetId == _uiState.value.mediaId) {
            _uiState.value = _uiState.value.copy(
                currentPositionMs = 0L,
                status = PlaybackStatus.NEW,
            )
        }
    }

    private fun startPeriodicSave() {
        if (periodicSaveJob?.isActive == true) return
        periodicSaveJob = serviceScope.launch {
            while (isActive) {
                delay(progressSaveIntervalMs)
                saveCurrentProgressNow(isEof = false)
            }
        }
    }

    private fun stopPeriodicSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = null
    }

    private suspend fun saveCurrentProgressNow(isEof: Boolean = false) {
        val state = _uiState.value
        val mediaId = state.mediaId ?: return
        val filePath = state.filePath ?: mediaId
        val pos = state.currentPositionMs
        val dur = state.durationMs

        val status = completionPolicy.determineStatus(pos, dur, isEof)
        val progress = PlaybackProgress(
            mediaId = mediaId,
            filePath = filePath,
            totalDurationMs = dur,
            currentPositionMs = pos,
            status = status,
            lastPlayedAt = System.currentTimeMillis(),
        )
        progressRepository.saveProgress(progress)
        _uiState.value = _uiState.value.copy(status = status)
    }

    /**
     * Safe teardown: saves current progress, stops periodic tasks, and releases the engine.
     */
    fun release() {
        if (isReleased) return
        isReleased = true

        stopPeriodicSave()

        // Flush last progress before releasing
        val state = _uiState.value
        val mediaId = state.mediaId
        if (mediaId != null) {
            val status = completionPolicy.determineStatus(state.currentPositionMs, state.durationMs)
            val finalProgress = PlaybackProgress(
                mediaId = mediaId,
                filePath = state.filePath ?: mediaId,
                totalDurationMs = state.durationMs,
                currentPositionMs = state.currentPositionMs,
                status = status,
                lastPlayedAt = System.currentTimeMillis(),
            )
            // Use runBlocking or launch before cancelling scope to guarantee write
            serviceScope.launch {
                progressRepository.saveProgress(finalProgress)
            }
        }

        engine.release()
        if (ownsScope) {
            serviceScope.cancel()
        }
    }
}
