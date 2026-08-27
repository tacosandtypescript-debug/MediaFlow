package com.mediaflow.domain.usecase

import com.mediaflow.domain.repository.DownloadRepository

class CancelDownloadUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(id: String) = repository.cancelDownload(id)
}
