package com.mediaflow.data.provider.x.live

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.domain.live.PendingLiveDownload
import com.mediaflow.domain.live.PendingLiveDownloadStatus

/**
 * Deduplicates Space replay downloads by canonical [spaceId], not SHA download ids.
 */
object SpaceDownloadDedup {

    fun fileName(hostUsername: String, spaceId: String): String =
        "Space_${hostUsername}_${spaceId}.m4a"

    fun shouldSkipDownload(
        spaceId: String,
        pending: PendingLiveDownload?,
        downloads: List<DownloadItem>,
    ): Boolean {
        when (pending?.status) {
            PendingLiveDownloadStatus.DOWNLOADING,
            PendingLiveDownloadStatus.COMPLETED,
            -> return true
            PendingLiveDownloadStatus.READY_TO_DOWNLOAD,
            PendingLiveDownloadStatus.RESOLVING_REPLAY,
            -> if (!pending.downloadId.isNullOrBlank()) return true
            else -> Unit
        }
        if (!pending?.downloadId.isNullOrBlank()) {
            val downloadId = pending.downloadId
            if (downloads.any { it.id == downloadId }) return true
        }
        return downloads.any { item -> matchesSpace(spaceId, item) }
    }

    fun matchesSpace(spaceId: String, item: DownloadItem): Boolean {
        if (spaceId.isBlank()) return false
        val fileName = item.fileName.orEmpty()
        return fileName.contains(spaceId) || item.sourceUrl.contains(spaceId)
    }
}
