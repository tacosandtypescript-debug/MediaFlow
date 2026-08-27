package com.mediaflow.data.repository

import android.content.Context
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.PlaybackStatus
import com.mediaflow.data.player.PlatformProgressStore
import com.mediaflow.domain.repository.ProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Default implementation of [ProgressRepository] using [PlatformProgressStore] and in-memory caching.
 */
class ProgressRepositoryImpl(
    private val store: PlatformProgressStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProgressRepository {

    constructor(context: Context) : this(PlatformProgressStore(context.applicationContext))

    private val mutex = Mutex()
    private val stateFlow = MutableStateFlow<Map<String, PlaybackProgress>>(emptyMap())
    private var isInitialized = false

    private suspend fun ensureLoaded() = mutex.withLock {
        if (!isInitialized) {
            val loaded = withContext(ioDispatcher) { store.load() }
            stateFlow.value = loaded
            isInitialized = true
        }
    }

    override suspend fun getProgress(mediaId: String): PlaybackProgress? {
        ensureLoaded()
        return stateFlow.value[mediaId]
    }

    override fun observeProgress(mediaId: String): Flow<PlaybackProgress?> {
        return stateFlow.map { map -> map[mediaId] }
    }

    override fun observeAllProgress(): Flow<Map<String, PlaybackProgress>> {
        return stateFlow.asStateFlow()
    }

    override suspend fun saveProgress(progress: PlaybackProgress) {
        ensureLoaded()
        mutex.withLock {
            val updated = stateFlow.value.toMutableMap()
            updated[progress.mediaId] = progress
            stateFlow.value = updated
            withContext(ioDispatcher) {
                store.save(updated)
            }
        }
    }

    override suspend fun resetProgress(mediaId: String) {
        ensureLoaded()
        mutex.withLock {
            val current = stateFlow.value[mediaId] ?: return@withLock
            val updated = stateFlow.value.toMutableMap()
            updated[mediaId] = current.copy(
                currentPositionMs = 0L,
                playbackPercentage = 0f,
                status = PlaybackStatus.NEW,
                lastPlayedAt = System.currentTimeMillis(),
            )
            stateFlow.value = updated
            withContext(ioDispatcher) {
                store.save(updated)
            }
        }
    }

    override suspend fun markCompleted(mediaId: String, totalDurationMs: Long) {
        ensureLoaded()
        mutex.withLock {
            val current = stateFlow.value[mediaId]
            val duration = if (totalDurationMs > 0L) totalDurationMs else current?.totalDurationMs ?: 0L
            val updated = stateFlow.value.toMutableMap()
            val newItem = (current ?: PlaybackProgress.new(mediaId, mediaId)).copy(
                totalDurationMs = duration,
                currentPositionMs = duration,
                playbackPercentage = 1f,
                status = PlaybackStatus.COMPLETED,
                lastPlayedAt = System.currentTimeMillis(),
            )
            updated[mediaId] = newItem
            stateFlow.value = updated
            withContext(ioDispatcher) {
                store.save(updated)
            }
        }
    }
}
