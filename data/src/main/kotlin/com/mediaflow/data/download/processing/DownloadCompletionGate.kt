package com.mediaflow.data.download.processing

import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.data.media.MediaFileValidator
import com.mediaflow.domain.repository.DownloadRequest
import java.io.File

/**
 * A download is COMPLETED only after [MediaFileValidator] accepts the file.
 * Empty/corrupt outputs stay failed; temps should be deleted by the caller.
 */
object DownloadCompletionGate {
    fun evaluate(
        file: File,
        request: DownloadRequest,
    ): Result<MediaFileValidator.ValidatedMedia> = MediaFileValidator.validate(
        file = file,
        expectedType = request.mediaType,
        expectedExtension = request.extension ?: file.extension,
        expectedDurationSeconds = request.durationSeconds,
        expectedWidth = request.width,
        expectedHeight = request.height,
        expectedVideoCodec = request.videoCodec,
        expectedAudioCodec = request.audioCodec,
    )

    fun statusAfter(result: Result<MediaFileValidator.ValidatedMedia>): DownloadStatus =
        if (result.isSuccess) DownloadStatus.COMPLETED else DownloadStatus.FAILED

    fun needsMerge(format: MediaFormat): Boolean =
        format.mediaType == MediaType.VIDEO && format.requiresMuxing && !format.isProgressive
}
