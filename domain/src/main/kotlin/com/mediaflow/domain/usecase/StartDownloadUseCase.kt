package com.mediaflow.domain.usecase

import com.mediaflow.domain.repository.DownloadRepository
import com.mediaflow.domain.repository.DownloadRequest

/**
 * Starts a download by delegating to the [DownloadRepository].
 * Returns the new download id.
 */
class StartDownloadUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(request: DownloadRequest): String =
        repository.startDownload(request)
}
