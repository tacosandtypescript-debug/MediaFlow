package com.mediaflow.domain.repository

import com.mediaflow.core.model.XSpace
import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting and retrieving structured X Space metadata
 * associated with downloaded or analysed media.
 */
interface XSpaceRepository {
    suspend fun saveSpace(space: XSpace, mediaId: String? = null)
    suspend fun getSpace(spaceId: String): XSpace?
    suspend fun getSpaceForMedia(mediaId: String): XSpace?
    fun observeAllSpaces(): Flow<Map<String, XSpace>>
    fun observeSpaceForMedia(mediaId: String): Flow<XSpace?>
}
