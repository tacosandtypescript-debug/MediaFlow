package com.mediaflow.domain.live

import kotlinx.coroutines.flow.Flow

/**
 * Storage contract for managing pending and in-progress post-live space downloads.
 */
interface PendingLiveDownloadRepository {
    fun observePendingDownloads(): Flow<List<PendingLiveDownload>>
    suspend fun getPendingDownload(spaceId: String): PendingLiveDownload?
    suspend fun savePendingDownload(download: PendingLiveDownload)
    suspend fun removePendingDownload(spaceId: String)
    suspend fun isAutoDownloadEnabled(spaceId: String): Boolean
    suspend fun setAutoDownloadEnabled(
        spaceId: String,
        title: String,
        hostHandle: String,
        sourceUrl: String,
        enabled: Boolean,
    )
}
