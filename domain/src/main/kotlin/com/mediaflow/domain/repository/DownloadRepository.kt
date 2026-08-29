package com.mediaflow.domain.repository

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import kotlinx.coroutines.flow.Flow

/**
 * Parameters to start a download.
 */
data class DownloadRequest(
    val sourceUrl: String,
    val mediaType: MediaType,
    val qualityLabel: String? = null,
    val formatId: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val extension: String? = null,
    val durationSeconds: Long? = null,
    val requiresMuxing: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val container: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val thumbnailUrl: String? = null,
    val streamUrl: String? = null,
)

/**
 * Contract to manage downloads. The real implementation (e.g. WorkManager or
 * DownloadManager based) will be provided in a later phase.
 */
interface DownloadRepository {
    /** Observes the current list of downloads. */
    fun observeDownloads(): Flow<List<DownloadItem>>

    suspend fun getDownloadById(id: String): DownloadItem?

    /** Starts a download and returns its id. */
    suspend fun startDownload(request: DownloadRequest): String

    suspend fun pauseDownload(id: String)

    suspend fun resumeDownload(id: String)

    suspend fun cancelDownload(id: String)

    /** Retries a failed download using its existing request. */
    suspend fun retryDownload(id: String)

    /** Removes a download from the history. */
    suspend fun removeDownload(id: String)
}
