package com.mediaflow.domain.player

import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.PlaybackQueueItem
import com.mediaflow.core.model.PlaybackStatus
import com.mediaflow.domain.repository.ProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerServiceQueueTest {

    private fun TestScope.createService(): Pair<PlayerService, FakePlaybackEngine> {
        val engine = FakePlaybackEngine()
        val repo = FakeProgressRepository()
        val dispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val service = PlayerService(
            engine = engine,
            progressRepository = repo,
            coroutineScope = backgroundScope,
            mainDispatcher = dispatcher,
        )
        return Pair(service, engine)
    }

    @Test
    fun playQueue_setsQueueAndPlaysFirst() = runTest {
        val (playerService, _) = createService()
        val items = listOf(
            PlaybackQueueItem("uri1", "Song 1", "Artist 1"),
            PlaybackQueueItem("uri2", "Song 2", "Artist 2"),
            PlaybackQueueItem("uri3", "Song 3", "Artist 3"),
        )

        playerService.playQueue(items, startIndex = 0, context = "Playlist: Test")
        runCurrent()

        val state = playerService.uiState.value
        assertEquals("uri1", state.mediaId)
        assertEquals(0, state.queueIndex)
        assertEquals(3, state.queue.size)
        assertTrue(state.hasNext)
        assertFalse(state.hasPrevious)
        assertEquals("Playlist: Test", state.playbackContext)
    }

    @Test
    fun playNextAndPlayPrevious_navigatesQueue() = runTest {
        val (playerService, _) = createService()
        val items = listOf(
            PlaybackQueueItem("uri1", "Song 1"),
            PlaybackQueueItem("uri2", "Song 2"),
            PlaybackQueueItem("uri3", "Song 3"),
        )

        playerService.playQueue(items, startIndex = 0)
        runCurrent()

        playerService.playNext()
        runCurrent()

        assertEquals("uri2", playerService.uiState.value.mediaId)
        assertEquals(1, playerService.uiState.value.queueIndex)
        assertTrue(playerService.uiState.value.hasNext)
        assertTrue(playerService.uiState.value.hasPrevious)

        playerService.playPrevious()
        runCurrent()

        assertEquals("uri1", playerService.uiState.value.mediaId)
        assertEquals(0, playerService.uiState.value.queueIndex)
    }

    @Test
    fun addToQueueAndRemoveFromQueue_updatesQueue() = runTest {
        val (playerService, _) = createService()
        val items = listOf(
            PlaybackQueueItem("uri1", "Song 1"),
        )

        playerService.playQueue(items, startIndex = 0)
        runCurrent()

        val newItem = PlaybackQueueItem("uri2", "Song 2")
        playerService.addToQueue(newItem)

        assertEquals(2, playerService.uiState.value.queue.size)
        assertTrue(playerService.uiState.value.hasNext)

        playerService.removeFromQueue(1)
        assertEquals(1, playerService.uiState.value.queue.size)
        assertFalse(playerService.uiState.value.hasNext)
    }

    private class FakePlaybackEngine : PlaybackEngine {
        private val _state = MutableStateFlow(EngineState())
        override val state = _state.asStateFlow()

        private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 64)
        override val events = _events.asSharedFlow()

        override fun load(mediaSource: String, startPositionMs: Long, autoPlay: Boolean) {
            _state.value = _state.value.copy(
                playbackState = if (autoPlay) EnginePlaybackState.PLAYING else EnginePlaybackState.PAUSED,
                currentPositionMs = startPositionMs,
            )
        }

        override fun play() { _state.value = _state.value.copy(playbackState = EnginePlaybackState.PLAYING) }
        override fun pause() { _state.value = _state.value.copy(playbackState = EnginePlaybackState.PAUSED) }
        override fun stop() { _state.value = _state.value.copy(playbackState = EnginePlaybackState.IDLE) }
        override fun seekTo(positionMs: Long) { _state.value = _state.value.copy(currentPositionMs = positionMs) }
        override fun setSpeed(speed: Float) { _state.value = _state.value.copy(speed = speed) }
        override fun setVolume(volume: Int) { _state.value = _state.value.copy(volume = volume) }
        override fun setMute(muted: Boolean) { _state.value = _state.value.copy(isMuted = muted) }
        override fun attachSurface(surface: Any?) {}
        override fun detachSurface() {}
        override fun release() {}
    }

    private class FakeProgressRepository : ProgressRepository {
        private val storage = mutableMapOf<String, PlaybackProgress>()
        private val _flow = MutableStateFlow<Map<String, PlaybackProgress>>(emptyMap())

        override suspend fun getProgress(mediaId: String): PlaybackProgress? = storage[mediaId]
        override suspend fun saveProgress(progress: PlaybackProgress) {
            storage[progress.mediaId] = progress
            _flow.value = storage.toMap()
        }
        override suspend fun resetProgress(mediaId: String) {
            storage.remove(mediaId)
            _flow.value = storage.toMap()
        }
        override suspend fun markCompleted(mediaId: String, totalDurationMs: Long) {
            val existing = storage[mediaId]
            val updated = existing?.copy(
                status = PlaybackStatus.COMPLETED,
                currentPositionMs = totalDurationMs,
                totalDurationMs = totalDurationMs,
            ) ?: PlaybackProgress(
                mediaId = mediaId,
                filePath = mediaId,
                totalDurationMs = totalDurationMs,
                currentPositionMs = totalDurationMs,
                status = PlaybackStatus.COMPLETED,
            )
            storage[mediaId] = updated
            _flow.value = storage.toMap()
        }
        override fun observeProgress(mediaId: String): Flow<PlaybackProgress?> = MutableStateFlow(storage[mediaId])
        override fun observeAllProgress(): Flow<Map<String, PlaybackProgress>> = _flow.asStateFlow()
    }
}
