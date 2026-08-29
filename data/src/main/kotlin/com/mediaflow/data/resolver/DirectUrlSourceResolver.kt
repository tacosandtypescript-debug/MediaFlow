package com.mediaflow.data.resolver

import java.net.URI
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.DownloadRequest
import com.mediaflow.domain.repository.SourceInfo
import com.mediaflow.domain.repository.SourceResolver
import java.util.Locale

/**
 * Resolves direct HTTPS media files and delegates every other HTTPS page to yt-dlp.
 *
 * Direct files are resolved locally; pages are handed to the embedded yt-dlp
 * adapter without a hard-coded platform allow-list.
 */
class DirectUrlSourceResolver : SourceResolver {
    override suspend fun analyze(sourceUrl: String): SourceInfo {
        PlatformUrlSupport.platformFor(sourceUrl)?.let { platform ->
            return SourceInfo(
                sourceUrl = sourceUrl.trim(),
                title = "${platform.label} video",
                availableFormats = listOf(
                    MediaFormat(
                        formatId = "yt-dlp",
                        extension = "mp4",
                        mimeType = "video/mp4",
                        mediaType = MediaType.VIDEO,
                        qualityLabel = "Automática",
                        isProgressive = true,
                        requiresMuxing = false,
                    ),
                ),
            )
        }
        val descriptor = descriptor(sourceUrl)
            ?: return SourceInfo(
                sourceUrl = sourceUrl.trim(),
                errorMessage = if (!PlatformUrlSupport.isGenericYtDlpPage(sourceUrl)) ERROR_INVALID_URL else null,
                title = "Vídeo de la página",
                availableFormats = if (PlatformUrlSupport.isGenericYtDlpPage(sourceUrl)) listOf(
                    MediaFormat(
                        formatId = "yt-dlp",
                        extension = "mp4",
                        mimeType = "video/mp4",
                        mediaType = MediaType.VIDEO,
                        qualityLabel = "Automática",
                        isProgressive = true,
                        requiresMuxing = false,
                    ),
                ) else emptyList(),
            )

        val fileName = fileNameFrom(sourceUrl, descriptor.extension)
        return SourceInfo(
            sourceUrl = sourceUrl.trim(),
            title = fileName,
            availableFormats = listOf(
                MediaFormat(
                    formatId = "direct",
                    extension = descriptor.extension,
                    mimeType = descriptor.mimeType,
                    mediaType = descriptor.mediaType,
                    qualityLabel = null,
                    isProgressive = true,
                    requiresMuxing = false,
                ),
            ),
        )
    }

    /** Builds a domain request only after the URL passes direct-file checks. */
    fun createRequest(
        sourceUrl: String,
        mediaType: MediaType,
        qualityLabel: String?,
        customFileName: String?,
    ): Result<DownloadRequest> {
        PlatformUrlSupport.platformFor(sourceUrl)?.let { platform ->
            val isAudio = mediaType == MediaType.AUDIO
            val ext = if (isAudio) "m4a" else "mp4"
            val mime = if (isAudio) "audio/mp4" else "video/mp4"
            val defaultSuffix = if (isAudio) "${platform.filePrefix}_audio" else "${platform.filePrefix}_video"
            val name = customFileName?.trim()?.takeIf { it.isNotEmpty() }
                ?.let(::sanitizeFileName)
                ?.takeIf { it.isNotEmpty() }
                ?: defaultSuffix
            return Result.success(
                DownloadRequest(
                    sourceUrl = sourceUrl.trim(),
                    mediaType = mediaType,
                    qualityLabel = qualityLabel,
                    formatId = if (isAudio) (if (platform == PlatformUrlSupport.Platform.X) "space_audio_m4a" else "bestaudio") else "yt-dlp",
                    fileName = name,
                    mimeType = mime,
                    extension = ext,
                ),
            )
        }
        val descriptor = descriptor(sourceUrl)
        if (descriptor == null) {
            if (!PlatformUrlSupport.isGenericYtDlpPage(sourceUrl)) {
                return Result.failure(IllegalArgumentException(ERROR_INVALID_URL))
            }
            val name = customFileName?.trim()?.takeIf { it.isNotEmpty() }
                ?.let(::sanitizeFileName)
                ?.takeIf { it.isNotEmpty() }
                ?: "mediaflow_video"
            return Result.success(
                DownloadRequest(
                    sourceUrl = sourceUrl.trim(),
                    mediaType = mediaType,
                    qualityLabel = qualityLabel,
                    formatId = "yt-dlp",
                    fileName = name,
                    mimeType = if (mediaType == MediaType.AUDIO) "audio/mpeg" else "video/mp4",
                    extension = if (mediaType == MediaType.AUDIO) "mp3" else "mp4",
                ),
            )
        }
        if (descriptor.mediaType != mediaType) {
            return Result.failure(IllegalArgumentException("El tipo de archivo no coincide con el tipo seleccionado"))
        }
        val automaticName = fileNameFrom(sourceUrl, descriptor.extension)
        val safeName = customFileName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::sanitizeFileName)
            ?.takeIf { it.isNotEmpty() }
            ?: automaticName
        return Result.success(
            DownloadRequest(
                sourceUrl = sourceUrl.trim(),
                mediaType = mediaType,
                qualityLabel = qualityLabel,
                formatId = "direct",
                fileName = ensureExtension(safeName, descriptor.extension),
                mimeType = descriptor.mimeType,
                extension = descriptor.extension,
            ),
        )
    }

    private fun descriptor(sourceUrl: String): DirectDescriptor? {
        val uri = runCatching { URI(sourceUrl.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
        val extension = uri.path
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
            ?: return null
        return knownTypes[extension]
    }

    private fun fileNameFrom(sourceUrl: String, extension: String): String {
        val pathName = runCatching {
            URI(sourceUrl.trim()).path?.substringAfterLast('/', "")
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(::sanitizeFileName)
        return if (pathName.isNullOrBlank()) "mediaflow_download.$extension" else pathName
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\u0000-\u001F]"), "")

    private fun ensureExtension(fileName: String, extension: String): String =
        if (fileName.substringAfterLast('.', "").equals(extension, ignoreCase = true)) {
            fileName
        } else {
            "$fileName.$extension"
        }

    private data class DirectDescriptor(
        val extension: String,
        val mimeType: String,
        val mediaType: MediaType,
    )

    private companion object {
        const val ERROR_INVALID_URL = "Enlace no válido: se requiere una URL HTTPS pública."

        val knownTypes = mapOf(
            "mp4" to DirectDescriptor("mp4", "video/mp4", MediaType.VIDEO),
            "m4v" to DirectDescriptor("m4v", "video/x-m4v", MediaType.VIDEO),
            "webm" to DirectDescriptor("webm", "video/webm", MediaType.VIDEO),
            "mp3" to DirectDescriptor("mp3", "audio/mpeg", MediaType.AUDIO),
            "m4a" to DirectDescriptor("m4a", "audio/mp4", MediaType.AUDIO),
            "aac" to DirectDescriptor("aac", "audio/aac", MediaType.AUDIO),
            "wav" to DirectDescriptor("wav", "audio/wav", MediaType.AUDIO),
            "ogg" to DirectDescriptor("ogg", "audio/ogg", MediaType.AUDIO),
        )
    }
}
