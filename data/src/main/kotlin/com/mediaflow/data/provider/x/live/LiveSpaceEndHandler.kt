package com.mediaflow.data.provider.x.live

import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.domain.live.PendingLiveDownload
import com.mediaflow.domain.live.PendingLiveDownloadRepository
import com.mediaflow.domain.live.PendingLiveDownloadStatus
import com.mediaflow.domain.live.ReplayResolutionResult
import com.mediaflow.domain.repository.DownloadRepository
import com.mediaflow.domain.repository.DownloadRequest
import com.mediaflow.domain.repository.XSpaceRepository
import com.mediaflow.domain.usecase.StartDownloadUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owner of post-live verify + auto-download. The player VM only paints overlay
 * and must not start a second download while this handler is in flight.
 */
class LiveSpaceEndHandler(
    private val liveEndMonitor: LiveSpaceEndMonitor,
    private val pendingDownloadRepo: PendingLiveDownloadRepository,
    private val spaceRepository: XSpaceRepository,
    private val downloadRepository: DownloadRepository,
    private val onBroadcastEnded: () -> Unit = {},
    private val onSpaceUpdated: (XSpace) -> Unit = {},
) {
    private val mutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    fun isHandling(spaceId: String): Boolean = jobs[spaceId]?.isActive == true

    suspend fun handleStreamEnded(
        spaceId: String,
        spaceUrl: String,
        autoDownloadWhenEnded: Boolean,
        scope: CoroutineScope,
    ): Job? = mutex.withLock {
        val active = jobs[spaceId]
        if (active?.isActive == true) return active
        val pending = pendingDownloadRepo.getPendingDownload(spaceId)
        if (pending?.status == PendingLiveDownloadStatus.DOWNLOADING ||
            pending?.status == PendingLiveDownloadStatus.COMPLETED
        ) {
            return active
        }
        val job = scope.launch {
            runEndHandling(spaceId, spaceUrl, autoDownloadWhenEnded)
        }
        jobs[spaceId] = job
        job
    }

    suspend fun resumeInterruptedWaits(scope: CoroutineScope) {
        val pendingList = pendingDownloadRepo.observePendingDownloads().first()
        for (pending in pendingList) {
            val shouldResume = pending.status == PendingLiveDownloadStatus.RESOLVING_REPLAY ||
                (pending.status == PendingLiveDownloadStatus.WAITING_FOR_END && pending.autoDownloadAfterEnd)
            if (shouldResume) {
                handleStreamEnded(
                    spaceId = pending.spaceId,
                    spaceUrl = pending.sourceUrl.ifBlank { "https://x.com/i/spaces/${pending.spaceId}" },
                    autoDownloadWhenEnded = pending.autoDownloadAfterEnd,
                    scope = scope,
                )
            }
        }
    }

    private suspend fun runEndHandling(
        spaceId: String,
        spaceUrl: String,
        autoDownloadWhenEnded: Boolean,
    ) {
        val result = liveEndMonitor.waitForReplay(spaceId, spaceUrl) { attempt, _ ->
            val current = pendingDownloadRepo.getPendingDownload(spaceId)
            if (current != null) {
                pendingDownloadRepo.savePendingDownload(
                    current.copy(
                        status = PendingLiveDownloadStatus.RESOLVING_REPLAY,
                        attemptCount = attempt,
                        errorMessage = null,
                    ),
                )
            }
        }

        when (result) {
            is ReplayResolutionResult.Available -> {
                val space = result.space.copy(
                    state = XSpaceState.ENDED,
                    audioStreamUrl = result.replayUrl,
                )
                onSpaceUpdated(space)
                spaceRepository.saveSpace(space, mediaId = space.url)
                onBroadcastEnded()
                val auto = autoDownloadWhenEnded || pendingDownloadRepo.isAutoDownloadEnabled(spaceId)
                if (auto) {
                    triggerPostLiveDownload(spaceId, space, result.replayUrl)
                } else {
                    val pending = pendingDownloadRepo.getPendingDownload(spaceId)
                    if (pending != null) {
                        pendingDownloadRepo.savePendingDownload(
                            pending.copy(
                                status = PendingLiveDownloadStatus.READY_TO_DOWNLOAD,
                                replayStreamUrl = result.replayUrl,
                            ),
                        )
                    }
                }
            }
            is ReplayResolutionResult.Processing -> {
                val pending = pendingDownloadRepo.getPendingDownload(spaceId) ?: return
                if (LiveSpaceEndMonitor.isStillBroadcasting(result)) {
                    if (pending.status == PendingLiveDownloadStatus.RESOLVING_REPLAY) {
                        pendingDownloadRepo.savePendingDownload(
                            pending.copy(status = PendingLiveDownloadStatus.WAITING_FOR_END),
                        )
                    }
                } else {
                    pendingDownloadRepo.savePendingDownload(
                        pending.copy(status = PendingLiveDownloadStatus.RESOLVING_REPLAY),
                    )
                }
            }
            is ReplayResolutionResult.NotAvailable -> {
                onBroadcastEnded()
                val pending = pendingDownloadRepo.getPendingDownload(spaceId)
                if (pending != null) {
                    pendingDownloadRepo.savePendingDownload(
                        pending.copy(
                            status = PendingLiveDownloadStatus.FAILED,
                            errorMessage = result.reason,
                        ),
                    )
                }
            }
            is ReplayResolutionResult.Error -> {
                val pending = pendingDownloadRepo.getPendingDownload(spaceId)
                if (pending != null) {
                    pendingDownloadRepo.savePendingDownload(
                        pending.copy(
                            status = PendingLiveDownloadStatus.FAILED,
                            errorMessage = result.message,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun triggerPostLiveDownload(spaceId: String, space: XSpace, replayUrl: String) {
        val pending = pendingDownloadRepo.getPendingDownload(spaceId)
        val downloads = downloadRepository.observeDownloads().first()
        if (SpaceDownloadDedup.shouldSkipDownload(spaceId, pending, downloads)) return

        val sourceUrl = space.url.ifBlank { replayUrl }
        val request = DownloadRequest(
            sourceUrl = sourceUrl,
            mediaType = MediaType.AUDIO,
            formatId = "space_audio_m4a",
            fileName = SpaceDownloadDedup.fileName(space.host.cleanUsername, space.id),
            mimeType = "audio/mp4",
            extension = "m4a",
            durationSeconds = space.durationSeconds.takeIf { it > 0 },
            thumbnailUrl = space.host.avatarUrl,
        )

        val downloadId = StartDownloadUseCase(downloadRepository)(request)

        pendingDownloadRepo.savePendingDownload(
            PendingLiveDownload(
                spaceId = spaceId,
                title = space.title,
                hostHandle = space.host.formattedHandle,
                sourceUrl = space.url,
                autoDownloadAfterEnd = true,
                status = PendingLiveDownloadStatus.DOWNLOADING,
                replayStreamUrl = replayUrl,
                downloadId = downloadId,
            ),
        )
    }
}
