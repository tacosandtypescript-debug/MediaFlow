package com.mediaflow.domain.usecase

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.domain.repository.GalleryRepository
import kotlinx.coroutines.flow.Flow

class GetGalleryItemsUseCase(
    private val galleryRepository: GalleryRepository,
) {
    operator fun invoke(): Flow<List<DownloadItem>> = galleryRepository.observeGallery()
}
