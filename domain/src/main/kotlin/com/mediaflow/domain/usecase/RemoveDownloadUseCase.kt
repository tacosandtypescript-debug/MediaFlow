package com.mediaflow.domain.usecase

import com.mediaflow.domain.repository.DownloadRepository

/** Removes a download from the persistent DownloadIndex/history. */
class RemoveDownloadUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(id: String) = repository.removeDownload(id)
}
