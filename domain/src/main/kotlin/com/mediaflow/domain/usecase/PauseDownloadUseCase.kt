package com.mediaflow.domain.usecase

import com.mediaflow.domain.repository.DownloadRepository

class PauseDownloadUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(id: String) = repository.pauseDownload(id)
}
