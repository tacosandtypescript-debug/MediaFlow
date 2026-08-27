package com.mediaflow.data.download

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.mediaflow.core.model.DownloadStatus

/** Maps Media3 lifecycle states without inventing an intermediate state. */
@OptIn(markerClass = [UnstableApi::class])
object Media3DownloadStateMapper {
    fun map(state: Int, stopReason: Int): DownloadStatus = when (state) {
        Download.STATE_QUEUED -> DownloadStatus.QUEUED
        Download.STATE_RESTARTING -> DownloadStatus.PREPARING
        Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
        Download.STATE_STOPPED -> if (stopReason != Download.STOP_REASON_NONE) {
            DownloadStatus.PAUSED
        } else {
            DownloadStatus.IDLE
        }
        Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
        Download.STATE_FAILED -> DownloadStatus.FAILED
        Download.STATE_REMOVING -> DownloadStatus.CANCELED
        else -> DownloadStatus.IDLE
    }
}
