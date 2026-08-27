package com.mediaflow.domain.repository

import com.mediaflow.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow

/**
 * Contract to manage gallery media items. The Android data layer currently
 * implements it with MediaStore; the domain remains platform-independent.
 *
 * Items are represented by [DownloadItem] for this phase.
 */
interface GalleryRepository {
    /** Observes all gallery media items. */
    fun observeGallery(): Flow<List<DownloadItem>>

    suspend fun getItemById(id: String): DownloadItem?

    suspend fun deleteItem(id: String): Boolean

    suspend fun renameItem(id: String, newName: String): DownloadItem?

    /** Returns the local URI of an item (e.g. for sharing), if available. */
    suspend fun getLocalUri(id: String): String?
}
