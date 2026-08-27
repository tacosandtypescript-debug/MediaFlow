package com.mediaflow.core.model

/**
 * Lifecycle states of a download.
 */
enum class DownloadStatus {
    IDLE,
    /** Media3 has accepted the request but has not started transferring. */
    QUEUED,
    ANALYZING,
    PREPARING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED,
}
