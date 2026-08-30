package com.mediaflow.domain.repository

import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.XSpace

/**
 * Result of analyzing a source URL.
 *
 * A result must contain only formats returned by the source analyser. Empty
 * formats plus an error means the source was not analysed successfully.
 */
data class PlaylistEntry(
    val sourceUrl: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
)

data class SourceInfo(
    val sourceUrl: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val availableFormats: List<MediaFormat> = emptyList(),
    val errorMessage: String? = null,
    val spaceMetadata: XSpace? = null,
    val playlistEntries: List<PlaylistEntry> = emptyList(),
)

/**
 * Contract to analyze a media source before a download is offered.
 */
interface SourceResolver {
    suspend fun analyze(sourceUrl: String): SourceInfo
}
