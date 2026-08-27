package com.mediaflow.data.media

import android.media.MediaExtractor
import android.media.MediaFormat as AndroidMediaFormat
import android.util.Log
import com.mediaflow.core.model.MediaType
import java.io.File

/** Validates the produced container before it becomes visible as completed. */
object MediaFileValidator {
    private const val TAG = "MediaFileValidator"

    internal var extractorFactory: (File) -> MediaExtractorAdapter = { file ->
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        AndroidMediaExtractorAdapter(extractor)
    }

    fun validate(
        file: File,
        expectedType: MediaType,
        expectedExtension: String?,
        expectedDurationSeconds: Long? = null,
        expectedWidth: Int? = null,
        expectedHeight: Int? = null,
        expectedVideoCodec: String? = null,
        expectedAudioCodec: String? = null,
    ): Result<ValidatedMedia> = runCatching {
        require(file.isFile && file.length() > 0L) { "El archivo multimedia está vacío o no existe." }
        expectedExtension?.let { expected ->
            require(file.extension.equals(expected, ignoreCase = true)) {
                "La extensión real (.${file.extension}) no coincide con la extensión esperada (.$expected)."
            }
        }
        val extractor = extractorFactory(file)
        try {
            require(extractor.trackCount > 0) { "El contenedor no contiene pistas multimedia o está corrupto." }
            var hasExpectedTrack = false
            var durationUs = 0L
            var width: Int? = null
            var height: Int? = null
            var videoMime: String? = null
            var audioMime: String? = null
            var videoCodecString: String? = null
            var audioCodecString: String? = null

            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(AndroidMediaFormat.KEY_MIME).orEmpty()
                val isVideo = mime.startsWith("video/")
                val isAudio = mime.startsWith("audio/")
                if (isVideo || isAudio) {
                    durationUs = maxOf(durationUs, format.getLongOrZero(AndroidMediaFormat.KEY_DURATION))
                }
                if (isVideo) {
                    videoMime = mime
                    videoCodecString = format.getStringOrNull(AndroidMediaFormat.KEY_CODECS_STRING)
                    if (expectedType == MediaType.VIDEO) {
                        hasExpectedTrack = true
                        width = format.getIntegerOrNull(AndroidMediaFormat.KEY_WIDTH)
                        height = format.getIntegerOrNull(AndroidMediaFormat.KEY_HEIGHT)
                    }
                } else if (isAudio) {
                    audioMime = mime
                    audioCodecString = format.getStringOrNull(AndroidMediaFormat.KEY_CODECS_STRING)
                    if (expectedType == MediaType.AUDIO) {
                        hasExpectedTrack = true
                    }
                }
            }

            // Audio files delivered inside an MP4 container with video or audio tracks are valid
            if (expectedType == MediaType.AUDIO && !hasExpectedTrack && videoMime != null) {
                hasExpectedTrack = true
            }

            require(hasExpectedTrack) { "El archivo no contiene una pista ${expectedType.name.lowercase()} válida." }

            expectedWidth?.let { require(width == it) { "La anchura real ($width) no coincide con la esperada ($it)." } }
            expectedHeight?.let { require(height == it) { "La altura real ($height) no coincide con la esperada ($it)." } }

            val actualDurationSeconds = (durationUs / 1_000_000L).takeIf { it > 0L }

            // Informative duration check: for HLS, X Spaces and streaming media, metadata duration is orientative.
            // Never invalidate a healthy, playable file solely due to duration mismatch.
            if (expectedDurationSeconds != null && actualDurationSeconds != null) {
                val delta = kotlin.math.abs(actualDurationSeconds - expectedDurationSeconds)
                if (delta > 30L) {
                    Log.w(
                        TAG,
                        "Duración estimada por metadata ($expectedDurationSeconds s) difiere de la duración autoritativa del archivo ($actualDurationSeconds s, delta: ${delta}s). Usando duración real del archivo.",
                    )
                }
            }

            expectedVideoCodec?.let { require(codecMatches(it, videoMime, videoCodecString)) {
                "El códec de vídeo real ($videoMime) no coincide con el formato seleccionado."
            } }
            expectedAudioCodec?.let { require(codecMatches(it, audioMime, audioCodecString)) {
                "El códec de audio real ($audioMime) no coincide con el formato seleccionado."
            } }

            ValidatedMedia(
                sizeBytes = file.length(),
                durationSeconds = actualDurationSeconds ?: expectedDurationSeconds,
                width = width,
                height = height,
            )
        } finally {
            extractor.release()
        }
    }

    data class ValidatedMedia(
        val sizeBytes: Long,
        val durationSeconds: Long?,
        val width: Int?,
        val height: Int?,
    )

    private fun AndroidMediaFormat.getLongOrZero(key: String): Long =
        if (containsKey(key)) getLong(key) else 0L

    private fun AndroidMediaFormat.getIntegerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun AndroidMediaFormat.getStringOrNull(key: String): String? =
        if (containsKey(key)) getString(key) else null

    private fun codecMatches(expected: String, mime: String?, codecString: String?): Boolean {
        val wanted = expected.lowercase()
        val actual = codecString?.lowercase().orEmpty()
        if (actual.contains(wanted)) return true
        return when {
            wanted.contains("avc") || wanted.contains("h264") -> mime == "video/avc"
            wanted.contains("hev") || wanted.contains("h265") -> mime == "video/hevc"
            wanted.contains("aac") || wanted.contains("mp4a") -> mime == "audio/mp4a-latm"
            wanted == "none" -> mime == null
            else -> true
        }
    }
}

interface MediaExtractorAdapter {
    val trackCount: Int
    fun getTrackFormat(index: Int): AndroidMediaFormat
    fun release()
}

class AndroidMediaExtractorAdapter(private val extractor: MediaExtractor) : MediaExtractorAdapter {
    override val trackCount: Int get() = extractor.trackCount
    override fun getTrackFormat(index: Int): AndroidMediaFormat = extractor.getTrackFormat(index)
    override fun release() = extractor.release()
}
