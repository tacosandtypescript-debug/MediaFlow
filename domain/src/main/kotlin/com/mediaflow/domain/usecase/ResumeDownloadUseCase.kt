package com.mediaflow.domain.usecase

import com.mediaflow.domain.repository.DownloadRepository

class ResumeDownloadUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(id: String) = repository.resumeDownload(id)
}
