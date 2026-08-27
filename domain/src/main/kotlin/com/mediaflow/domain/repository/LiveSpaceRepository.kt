package com.mediaflow.domain.repository

import com.mediaflow.domain.model.LiveSpaceSource

/**
 * Contract for resolving anonymous live streams for X Spaces.
 */
interface LiveSpaceRepository {
    suspend fun resolveLiveSpace(urlOrId: String): Result<LiveSpaceSource>
}
