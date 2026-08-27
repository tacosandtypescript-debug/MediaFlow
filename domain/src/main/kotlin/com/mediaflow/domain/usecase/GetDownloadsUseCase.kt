package com.mediaflow.domain.usecase

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow

class GetDownloadsUseCase(
    private val repository: DownloadRepository,
) {
    operator fun invoke(): Flow<List<DownloadItem>> = repository.observeDownloads()
}
