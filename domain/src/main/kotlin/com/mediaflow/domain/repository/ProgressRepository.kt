package com.mediaflow.domain.repository

import com.mediaflow.core.model.PlaybackProgress
import kotlinx.coroutines.flow.Flow

/**
 * Storage contract for persisting and observing media playback progress.
 */
interface ProgressRepository {
    /** Returns current saved progress for [mediaId], or null if never recorded. */
    suspend fun getProgress(mediaId: String): PlaybackProgress?

    /** Observes updates for a specific media item. Emits null if no progress exists. */
    fun observeProgress(mediaId: String): Flow<PlaybackProgress?>

    /** Observes all saved progress records mapped by their mediaId. */
    fun observeAllProgress(): Flow<Map<String, PlaybackProgress>>

    /** Saves or updates progress for a media item. */
    suspend fun saveProgress(progress: PlaybackProgress)

    /** Resets progress for [mediaId] back to initial unplayed state or clears it. */
    suspend fun resetProgress(mediaId: String)

    /** Marks content as completed. */
    suspend fun markCompleted(mediaId: String, totalDurationMs: Long)
}
