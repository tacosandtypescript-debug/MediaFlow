package com.mediaflow.domain.player

import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.PlaybackQueueItem
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
    val artistOrHost: String? = null,
    val artworkUrl: String? = null,
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
    val isLive: Boolean = false,
    val queue: List<PlaybackQueueItem> = emptyList(),
    val queueIndex: Int = -1,
    val playbackContext: String? = null,
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
        get() = if (!isLive && durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val currentQueueItem: PlaybackQueueItem?
        get() = if (queueIndex in queue.indices) queue[queueIndex] else null

    val hasNext: Boolean
        get() = queueIndex in 0 until queue.lastIndex

    val hasPrevious: Boolean
        get() = queueIndex > 0
}

/**
 * Service managing media sessions, engine interactions, periodic progress persistence,
 * smart resumption, queue orchestration, and lifecycle-safe shutdown.
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

        // Forward engine events and handle automatic queue progression
        serviceScope.launch {
            engine.events.collect { event ->
                if (_uiState.value.isLive) {
                    _events.emit(event)
                    return@collect
                }
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

                        // Auto-advance in queue if available
                        val currentState = _uiState.value
                        if (currentState.hasNext) {
                            playNext()
                        }
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
     * Plays an entire queue starting from [startIndex] within [playbackContext].
     */
    fun playQueue(
        items: List<PlaybackQueueItem>,
        startIndex: Int = 0,
        context: String? = null,
    ) {
        if (items.isEmpty() || isReleased) return
        val validIndex = startIndex.coerceIn(0, items.lastIndex)
        val target = items[validIndex]

        _uiState.value = _uiState.value.copy(
            queue = items,
            queueIndex = validIndex,
            playbackContext = context,
            artistOrHost = target.artistOrHost,
            artworkUrl = target.artworkUrl,
        )

        openMedia(
            mediaId = target.mediaUri,
            filePath = target.mediaUri,
            title = target.title,
            artistOrHost = target.artistOrHost,
            artworkUrl = target.artworkUrl,
            autoPlay = true,
            isLive = target.isLive,
        )
    }

    /**
     * Opens a media item, resolving saved progress and configuring smart resumption.
     */
    fun openMedia(
        mediaId: String,
        filePath: String,
        title: String? = null,
        artistOrHost: String? = null,
        artworkUrl: String? = null,
        autoPlay: Boolean = true,
        isLive: Boolean = false,
    ) {
        if (isReleased) return

        serviceScope.launch {
            mutex.withLock {
                // If another media was active, save its progress before opening new one
                if (_uiState.value.mediaId != null && _uiState.value.mediaId != mediaId && !_uiState.value.isLive) {
                    saveCurrentProgressNow()
                }

                if (isLive) {
                    _uiState.value = _uiState.value.copy(
                        mediaId = mediaId,
                        filePath = filePath,
                        title = title ?: filePath.substringAfterLast('/'),
                        artistOrHost = artistOrHost,
                        artworkUrl = artworkUrl,
                        playbackState = EnginePlaybackState.PREPARING,
                        currentPositionMs = 0L,
                        durationMs = 0L,
                        status = PlaybackStatus.IN_PROGRESS,
                        isLive = true,
                    )
                    engine.load(mediaSource = filePath, startPositionMs = 0L, autoPlay = autoPlay)
                    return@withLock
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
                val initialStatus = if (currentStatus == PlaybackStatus.COMPLETED) {
                    PlaybackStatus.COMPLETED
                } else {
                    PlaybackStatus.IN_PROGRESS
                }

                val initialProgress = (saved ?: PlaybackProgress.new(mediaId, filePath)).copy(
                    currentPositionMs = startPos,
                    status = initialStatus,
                    lastPlayedAt = System.currentTimeMillis(),
                    playCount = nextPlayCount,
                )
                progressRepository.saveProgress(initialProgress)

                _uiState.value = _uiState.value.copy(
                    mediaId = mediaId,
                    filePath = filePath,
                    title = title ?: filePath.substringAfterLast('/'),
                    artistOrHost = artistOrHost,
                    artworkUrl = artworkUrl,
                    playbackState = EnginePlaybackState.PREPARING,
                    currentPositionMs = startPos,
                    durationMs = saved?.totalDurationMs ?: 0L,
                    status = initialStatus,
                    isLive = false,
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

    fun markBroadcastEnded() {
        if (isReleased) return
        val current = _uiState.value
        if (current.isLive) {
            _uiState.value = current.copy(isLive = false)
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

    fun stop() {
        if (isReleased) return
        engine.stop()
        serviceScope.launch {
            saveCurrentProgressNow(isEof = false)
            _uiState.value = _uiState.value.copy(
                playbackState = EnginePlaybackState.IDLE,
            )
        }
    }

    fun playNext() {
        val current = _uiState.value
        if (current.hasNext) {
            val nextIndex = current.queueIndex + 1
            skipToIndex(nextIndex)
        }
    }

    fun playPrevious() {
        val current = _uiState.value
        if (current.currentPositionMs > 3_000L) {
            // Seek to start of current song if already played > 3 seconds
            seekTo(0L)
        } else if (current.hasPrevious) {
            val prevIndex = current.queueIndex - 1
            skipToIndex(prevIndex)
        } else {
            seekTo(0L)
        }
    }

    fun skipToIndex(index: Int) {
        val current = _uiState.value
        if (index !in current.queue.indices || isReleased) return
        val item = current.queue[index]
        _uiState.value = current.copy(
            queueIndex = index,
            artistOrHost = item.artistOrHost,
            artworkUrl = item.artworkUrl,
        )
        openMedia(
            mediaId = item.mediaUri,
            filePath = item.mediaUri,
            title = item.title,
            artistOrHost = item.artistOrHost,
            artworkUrl = item.artworkUrl,
            autoPlay = true,
            isLive = item.isLive,
        )
    }

    fun addToQueue(item: PlaybackQueueItem) {
        val current = _uiState.value
        val updatedQueue = current.queue.toMutableList().apply { add(item) }
        val newIndex = if (current.queueIndex == -1) 0 else current.queueIndex
        _uiState.value = current.copy(queue = updatedQueue, queueIndex = newIndex)
    }

    fun removeFromQueue(index: Int) {
        val current = _uiState.value
        if (index !in current.queue.indices) return
        val updatedQueue = current.queue.toMutableList().apply { removeAt(index) }
        val newIndex = when {
            updatedQueue.isEmpty() -> -1
            index < current.queueIndex -> current.queueIndex - 1
            index == current.queueIndex -> current.queueIndex.coerceAtMost(updatedQueue.lastIndex)
            else -> current.queueIndex
        }
        _uiState.value = current.copy(queue = updatedQueue, queueIndex = newIndex)
    }

    fun setPlaybackContext(context: String?) {
        _uiState.value = _uiState.value.copy(playbackContext = context)
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
        if (_uiState.value.isLive) return
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
        if (state.isLive) return
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
        if (mediaId != null && !state.isLive) {
            val status = completionPolicy.determineStatus(state.currentPositionMs, state.durationMs)
            val finalProgress = PlaybackProgress(
                mediaId = mediaId,
                filePath = state.filePath ?: mediaId,
                totalDurationMs = state.durationMs,
                currentPositionMs = state.currentPositionMs,
                status = status,
                lastPlayedAt = System.currentTimeMillis(),
            )
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
