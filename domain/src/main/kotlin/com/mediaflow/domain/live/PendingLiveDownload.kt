package com.mediaflow.domain.live

/**
 * Lifecycle status of a pending post-live space download.
 */
enum class PendingLiveDownloadStatus {
    WAITING_FOR_END,
    RESOLVING_REPLAY,
    READY_TO_DOWNLOAD,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

/**
 * Model representing an active or ended X Space registered for replay download.
 */
data class PendingLiveDownload(
    val spaceId: String,
    val title: String,
    val hostHandle: String,
    val sourceUrl: String,
    val requestedAt: Long = System.currentTimeMillis(),
    val autoDownloadAfterEnd: Boolean = false,
    val status: PendingLiveDownloadStatus = PendingLiveDownloadStatus.WAITING_FOR_END,
    val replayStreamUrl: String? = null,
    val downloadId: String? = null,
    val errorMessage: String? = null,
    val attemptCount: Int = 0,
    val nextRetryAtMs: Long? = null,
)

/**
 * State representing post-live status in the UI player.
 */
sealed class LiveSpaceEndState {
    data object ActiveLive : LiveSpaceEndState()
    data class EndedResolvingReplay(val attempt: Int = 1) : LiveSpaceEndState()
    data class EndedReplayAvailable(val replayUrl: String) : LiveSpaceEndState()
    data class EndedReplayProcessing(
        val message: String = "La grabación todavía se está procesando...",
        val attempt: Int = 1,
    ) : LiveSpaceEndState()
    data class EndedNoReplay(
        val reason: String = "La grabación no está disponible.",
        val canCheckAgain: Boolean = false,
    ) : LiveSpaceEndState()
    data class EndedDownloadStarted(val downloadId: String) : LiveSpaceEndState()
}
