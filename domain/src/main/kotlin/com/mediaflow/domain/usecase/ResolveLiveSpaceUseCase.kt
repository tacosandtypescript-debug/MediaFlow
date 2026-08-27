package com.mediaflow.domain.usecase

import com.mediaflow.domain.model.LiveSpaceSource
import com.mediaflow.domain.repository.LiveSpaceRepository

/**
 * UseCase resolving an anonymous, live HLS stream for an X Space.
 */
class ResolveLiveSpaceUseCase(
    private val repository: LiveSpaceRepository,
) {
    suspend operator fun invoke(urlOrId: String): Result<LiveSpaceSource> {
        return repository.resolveLiveSpace(urlOrId)
    }
}
