package com.mediaflow.domain.player

import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.PlaybackStatus
import com.mediaflow.domain.repository.ProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeProgressRepository : ProgressRepository {
    private val data = MutableStateFlow<Map<String, PlaybackProgress>>(emptyMap())

    override suspend fun getProgress(mediaId: String): PlaybackProgress? = data.value[mediaId]

    override fun observeProgress(mediaId: String): Flow<PlaybackProgress?> =
        data.map { it[mediaId] }

    override fun observeAllProgress(): Flow<Map<String, PlaybackProgress>> =
        data.asStateFlow()

    override suspend fun saveProgress(progress: PlaybackProgress) {
        val map = data.value.toMutableMap()
        map[progress.mediaId] = progress
        data.value = map
    }

    override suspend fun resetProgress(mediaId: String) {
        val current = data.value[mediaId] ?: return
        val map = data.value.toMutableMap()
        map[mediaId] = current.copy(
            currentPositionMs = 0L,
            playbackPercentage = 0f,
            status = PlaybackStatus.NEW,
        )
        data.value = map
    }

    override suspend fun markCompleted(mediaId: String, totalDurationMs: Long) {
        val current = data.value[mediaId]
        val duration = if (totalDurationMs > 0L) totalDurationMs else current?.totalDurationMs ?: 0L
        val map = data.value.toMutableMap()
        map[mediaId] = (current ?: PlaybackProgress.new(mediaId, mediaId)).copy(
            totalDurationMs = duration,
            currentPositionMs = duration,
            playbackPercentage = 1f,
            status = PlaybackStatus.COMPLETED,
        )
        data.value = map
    }
}

class FakePlaybackEngine : PlaybackEngine {
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 64)
    override val events: Flow<PlaybackEvent> = _events.asSharedFlow()

    var lastAttachedSurface: Any? = null
    var isReleased = false
    var failOnLoad: String? = null

    override fun load(mediaSource: String, startPositionMs: Long, autoPlay: Boolean) {
        failOnLoad?.let { errorMsg ->
            _state.value = _state.value.copy(
                mediaSource = mediaSource,
                playbackState = EnginePlaybackState.ERROR,
                errorMessage = errorMsg,
            )
            _events.tryEmit(PlaybackEvent.PlaybackError(mediaSource, errorMsg, isFatal = true))
            return
        }

        _state.value = _state.value.copy(
            mediaSource = mediaSource,
            playbackState = if (autoPlay) EnginePlaybackState.PLAYING else EnginePlaybackState.PAUSED,
            currentPositionMs = startPositionMs,
            errorMessage = null,
        )
        _events.tryEmit(
            PlaybackEvent.MediaOpened(
                mediaId = mediaSource,
                durationMs = _state.value.durationMs,
                isAudioOnly = _state.value.isAudioOnly,
            )
        )
    }

    override fun play() {
        _state.value = _state.value.copy(playbackState = EnginePlaybackState.PLAYING)
        _events.tryEmit(PlaybackEvent.PlaybackStarted(_state.value.mediaSource.orEmpty(), _state.value.currentPositionMs))
    }

    override fun pause() {
        _state.value = _state.value.copy(playbackState = EnginePlaybackState.PAUSED)
        _events.tryEmit(PlaybackEvent.PlaybackPaused(_state.value.mediaSource.orEmpty(), _state.value.currentPositionMs))
    }

    override fun stop() {
        _state.value = _state.value.copy(playbackState = EnginePlaybackState.IDLE)
    }

    override fun seekTo(positionMs: Long) {
        _state.value = _state.value.copy(currentPositionMs = positionMs)
        _events.tryEmit(
            PlaybackEvent.PositionChanged(
                mediaId = _state.value.mediaSource.orEmpty(),
                positionMs = positionMs,
                durationMs = _state.value.durationMs,
            )
        )
    }

    override fun setSpeed(speed: Float) {
        _state.value = _state.value.copy(speed = speed)
    }

    override fun setVolume(volume: Int) {
        _state.value = _state.value.copy(volume = volume)
    }

    override fun setMute(muted: Boolean) {
        _state.value = _state.value.copy(isMuted = muted)
    }

    override fun attachSurface(surface: Any?) {
        lastAttachedSurface = surface
    }

    override fun detachSurface() {
        lastAttachedSurface = null
    }

    override fun release() {
        isReleased = true
        _events.tryEmit(PlaybackEvent.MediaClosed(_state.value.mediaSource.orEmpty(), _state.value.currentPositionMs))
    }

    fun simulateDuration(durationMs: Long) {
        _state.value = _state.value.copy(durationMs = durationMs)
    }

    fun simulatePosition(positionMs: Long) {
        _state.value = _state.value.copy(currentPositionMs = positionMs)
        _events.tryEmit(
            PlaybackEvent.PositionChanged(
                mediaId = _state.value.mediaSource.orEmpty(),
                positionMs = positionMs,
                durationMs = _state.value.durationMs,
            )
        )
    }

    fun simulateAudioOnly(isAudio: Boolean) {
        _state.value = _state.value.copy(isAudioOnly = isAudio, isVideoAvailable = !isAudio)
    }

    fun simulateEof() {
        _state.value = _state.value.copy(playbackState = EnginePlaybackState.ENDED)
        _events.tryEmit(PlaybackEvent.PlaybackFinished(_state.value.mediaSource.orEmpty(), _state.value.durationMs))
    }

    fun simulateError(message: String) {
        _state.value = _state.value.copy(playbackState = EnginePlaybackState.ERROR, errorMessage = message)
        _events.tryEmit(PlaybackEvent.PlaybackError(_state.value.mediaSource.orEmpty(), message, isFatal = true))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerServiceTest {

    private fun TestScope.createService(
        engine: FakePlaybackEngine = FakePlaybackEngine(),
        repository: FakeProgressRepository = FakeProgressRepository(),
    ): Triple<PlayerService, FakePlaybackEngine, FakeProgressRepository> {
        val service = PlayerService(
            engine = engine,
            progressRepository = repository,
            coroutineScope = backgroundScope,
            mainDispatcher = coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
            progressSaveIntervalMs = 500L,
        )
        return Triple(service, engine, repository)
    }

    @Test
    fun `1 creation of a new session initializes state correctly`() = runTest {
        val (service, _, _) = createService()
        service.openMedia("vid-1", "/storage/vid-1.mp4", "Sample Video")
        runCurrent()

        val state = service.uiState.value
        assertEquals("vid-1", state.mediaId)
        assertEquals("/storage/vid-1.mp4", state.filePath)
        assertEquals("Sample Video", state.title)
        assertEquals(PlaybackStatus.IN_PROGRESS, state.status)
        assertEquals(0L, state.currentPositionMs)
    }

    @Test
    fun `2 saving position periodically during playback`() = runTest {
        val (service, engine, repository) = createService()
        engine.simulateDuration(100_000L)
        service.openMedia("vid-2", "/storage/vid-2.mp4", "Test Video")
        runCurrent()

        engine.simulatePosition(30_000L)
        advanceTimeBy(600L)
        runCurrent()

        val progress = repository.getProgress("vid-2")
        assertNotNull(progress)
        assertEquals(30_000L, progress?.currentPositionMs)
        assertEquals(PlaybackStatus.IN_PROGRESS, progress?.status)
    }

    @Test
    fun `3 recovering progress after restart`() = runTest {
        val repository = FakeProgressRepository()
        repository.saveProgress(
            PlaybackProgress(
                mediaId = "vid-restart",
                filePath = "/storage/restart.mp4",
                totalDurationMs = 120_000L,
                currentPositionMs = 45_000L,
                status = PlaybackStatus.IN_PROGRESS,
            )
        )

        val (service, engine, _) = createService(repository = repository)
        service.openMedia("vid-restart", "/storage/restart.mp4")
        runCurrent()

        assertEquals(45_000L, service.uiState.value.currentPositionMs)
        assertEquals(45_000L, engine.state.value.currentPositionMs)
    }

    @Test
    fun `4 transition NEW to IN_PROGRESS on opening and playing`() = runTest {
        val (service, _, repository) = createService()
        service.openMedia("vid-new", "/storage/new.mp4")
        runCurrent()

        val progress = repository.getProgress("vid-new")
        assertNotNull(progress)
        assertEquals(PlaybackStatus.IN_PROGRESS, progress?.status)
        assertEquals(1, progress?.playCount)
    }

    @Test
    fun `5 transition IN_PROGRESS to COMPLETED on reaching end of file`() = runTest {
        val (service, engine, repository) = createService()
        engine.simulateDuration(100_000L)
        service.openMedia("vid-comp", "/storage/comp.mp4")
        runCurrent()

        engine.simulateEof()
        runCurrent()

        val progress = repository.getProgress("vid-comp")
        assertNotNull(progress)
        assertEquals(PlaybackStatus.COMPLETED, progress?.status)
        assertTrue(service.uiState.value.status == PlaybackStatus.COMPLETED)
    }

    @Test
    fun `6 completion threshold at 95 percent automatically marks COMPLETED`() = runTest {
        val (service, engine, repository) = createService()
        engine.simulateDuration(100_000L)
        service.openMedia("vid-thresh", "/storage/thresh.mp4")
        runCurrent()

        // 95_500 / 100_000 = 95.5% (>= 95%)
        engine.simulatePosition(95_500L)
        runCurrent()

        val progress = repository.getProgress("vid-thresh")
        assertNotNull(progress)
        assertEquals(PlaybackStatus.COMPLETED, progress?.status)
        assertEquals(PlaybackStatus.COMPLETED, service.uiState.value.status)
    }

    @Test
    fun `7 audio file detection sets isAudioOnly flag`() = runTest {
        val (service, engine, _) = createService()
        service.openMedia("audio-1", "/storage/song.mp3")
        engine.simulateAudioOnly(true)
        runCurrent()

        assertTrue(service.uiState.value.isAudioOnly)
        assertFalse(service.uiState.value.isVideoAvailable)
    }

    @Test
    fun `8 video file playback session has video available`() = runTest {
        val (service, engine, _) = createService()
        service.openMedia("video-1", "/storage/movie.mp4")
        engine.simulateAudioOnly(false)
        runCurrent()

        assertFalse(service.uiState.value.isAudioOnly)
        assertTrue(service.uiState.value.isVideoAvailable)
    }

    @Test
    fun `9 non-existent file error handling`() = runTest {
        val engine = FakePlaybackEngine()
        engine.failOnLoad = "El archivo no existe o fue movido"
        val (service, _, _) = createService(engine = engine)
        service.openMedia("missing-file", "/storage/deleted.mp4")
        runCurrent()

        val state = service.uiState.value
        assertEquals(EnginePlaybackState.ERROR, state.playbackState)
        assertEquals("El archivo no existe o fue movido", state.errorMessage)
    }

    @Test
    fun `10 app shutdown during playback flushes position and releases engine`() = runTest {
        val (service, engine, repository) = createService()
        engine.simulateDuration(100_000L)
        service.openMedia("vid-shutdown", "/storage/shutdown.mp4")
        runCurrent()

        engine.simulatePosition(42_000L)
        runCurrent()

        service.release()
        runCurrent()

        assertTrue(engine.isReleased)
        val progress = repository.getProgress("vid-shutdown")
        assertNotNull(progress)
        assertEquals(42_000L, progress?.currentPositionMs)
    }

    @Test
    fun `11 reopening content resumes from saved position`() = runTest {
        val repository = FakeProgressRepository()
        repository.saveProgress(
            PlaybackProgress(
                mediaId = "vid-resume",
                filePath = "/storage/resume.mp4",
                totalDurationMs = 200_000L,
                currentPositionMs = 85_000L,
                status = PlaybackStatus.IN_PROGRESS,
            )
        )

        val (service, engine, _) = createService(repository = repository)
        service.openMedia("vid-resume", "/storage/resume.mp4")
        runCurrent()

        assertEquals(85_000L, service.uiState.value.currentPositionMs)
        assertEquals(85_000L, engine.state.value.currentPositionMs)
    }

    @Test
    fun `12 restart from beginning button resets position to 0 and plays`() = runTest {
        val (service, engine, repository) = createService()
        engine.simulateDuration(100_000L)
        service.openMedia("vid-restart-btn", "/storage/restart.mp4")
        runCurrent()

        engine.simulatePosition(60_000L)
        runCurrent()

        service.restartFromBeginning()
        runCurrent()

        assertEquals(0L, service.uiState.value.currentPositionMs)
        assertEquals(0L, engine.state.value.currentPositionMs)
        assertEquals(EnginePlaybackState.PLAYING, engine.state.value.playbackState)

        val progress = repository.getProgress("vid-restart-btn")
        assertEquals(0L, progress?.currentPositionMs)
    }

    @Test
    fun `13 completed content starts from beginning 0`() = runTest {
        val repository = FakeProgressRepository()
        repository.saveProgress(
            PlaybackProgress(
                mediaId = "vid-already-done",
                filePath = "/storage/done.mp4",
                totalDurationMs = 100_000L,
                currentPositionMs = 99_000L,
                status = PlaybackStatus.COMPLETED,
            )
        )

        val (service, engine, _) = createService(repository = repository)
        service.openMedia("vid-already-done", "/storage/done.mp4")
        runCurrent()

        assertEquals(0L, service.uiState.value.currentPositionMs)
        assertEquals(0L, engine.state.value.currentPositionMs)
    }

    @Test
    fun `14 engine error handling during playback`() = runTest {
        val (service, engine, _) = createService()
        service.openMedia("vid-err", "/storage/err.mp4")
        runCurrent()

        engine.simulateError("Decoder failed")
        runCurrent()

        assertEquals(EnginePlaybackState.ERROR, service.uiState.value.playbackState)
        assertEquals("Decoder failed", service.uiState.value.errorMessage)
    }
}
