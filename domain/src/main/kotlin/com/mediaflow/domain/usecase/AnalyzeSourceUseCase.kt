package com.mediaflow.domain.usecase

import com.mediaflow.domain.repository.SourceInfo
import com.mediaflow.domain.repository.SourceResolver

/**
 * Analyzes a media source by delegating to the [SourceResolver].
 * No network access or yt-dlp call happens here.
 */
class AnalyzeSourceUseCase(
    private val sourceResolver: SourceResolver,
) {
    suspend operator fun invoke(sourceUrl: String): SourceInfo =
        sourceResolver.analyze(sourceUrl)
}
