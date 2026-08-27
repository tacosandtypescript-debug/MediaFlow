package com.mediaflow.data.media.metadata

import java.io.File

/**
 * Contract for embedding rich metadata tags into downloaded media files.
 */
interface MediaMetadataWriter {
    /**
     * Embeds the provided [metadata] into [file] without modifying its audio/video streams.
     * Operation is safe, atomic, and non-destructive on failure.
     */
    fun writeMetadata(file: File, metadata: MediaMetadata): Result<Unit>
}
