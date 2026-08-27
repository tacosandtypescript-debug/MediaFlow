package com.mediaflow.domain.usecase

import com.mediaflow.domain.repository.DownloadRepository

/** Delegates retrying a failed download to the repository. */
class RetryDownloadUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(id: String) = repository.retryDownload(id)
}
