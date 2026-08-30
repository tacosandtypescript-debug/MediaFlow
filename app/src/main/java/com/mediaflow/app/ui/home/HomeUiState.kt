package com.mediaflow.app.ui.home

import androidx.annotation.StringRes
import com.mediaflow.app.R
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.domain.repository.SourceInfo

/**
 * Content type selectable on the Home screen.
 *
 * Defined locally for this phase. If core/model later exposes a shared
 * media type model, this can be replaced without changing the UI shape.
 */
enum class ContentType(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    VIDEO(R.string.media_type_video, R.string.media_type_video_description),
    AUDIO(R.string.media_type_audio, R.string.media_type_audio_description),
}

/**
 * Visual quality options shown on the Home screen.
 *
 * Fallback labels used before a source has been analyzed. Production Home
 * replaces them with the source's real formats after analysis.
 */
enum class QualityOption(@StringRes val labelRes: Int) {
    AUTO(R.string.quality_auto),
    P360(R.string.quality_360p),
    P480(R.string.quality_480p),
    P720(R.string.quality_720p),
    P1080(R.string.quality_1080p),
    HIGH(R.string.quality_high),
    MEDIUM(R.string.quality_medium),
    LOW(R.string.quality_low);

    companion object {
        val videoOptions: List<QualityOption> = listOf(AUTO, P360, P480, P720, P1080)
        val audioOptions: List<QualityOption> = listOf(AUTO, HIGH, MEDIUM, LOW)

        fun optionsFor(contentType: ContentType): List<QualityOption> =
            if (contentType == ContentType.VIDEO) videoOptions else audioOptions
    }
}

/**
 * Picks a downloadable format that MediaTrackMuxer can actually finish.
 * Highest YouTube rungs are often VP9/AV1 video-only; AUTO prefers progressive
 * MP4, then muxable AVC/HEVC+MP4, and only then VP9/AV1.
 */
internal object PreferredDownloadFormat {
    fun select(
        formats: List<MediaFormat>,
        quality: QualityOption,
        selectedFormatId: String? = null,
    ): MediaFormat? {
        if (selectedFormatId != null) {
            formats.firstOrNull { it.formatId == selectedFormatId }?.let { return it }
        }
        if (formats.isEmpty()) return null
        val preferLowest = quality == QualityOption.LOW
        val targetHeight = when (quality) {
            QualityOption.P360 -> 360
            QualityOption.P480 -> 480
            QualityOption.P720 -> 720
            QualityOption.P1080 -> 1080
            QualityOption.MEDIUM -> medianHeight(formats)
            QualityOption.AUTO, QualityOption.HIGH, QualityOption.LOW -> null
        }
        return selectPreferred(formats, targetHeight, preferLowest)
            ?: formats.firstOrNull()
    }

    private fun selectPreferred(
        formats: List<MediaFormat>,
        targetHeight: Int?,
        preferLowest: Boolean,
    ): MediaFormat? {
        val heights = formats.mapNotNull { it.height }.distinct().let { distinct ->
            if (targetHeight != null) {
                val atOrBelow = distinct.filter { it <= targetHeight }
                if (atOrBelow.isNotEmpty()) atOrBelow
                else distinct.minByOrNull { kotlin.math.abs(it - targetHeight) }?.let { listOf(it) }.orEmpty()
            } else {
                distinct
            }
        }.let { list ->
            if (preferLowest) list.sorted() else list.sortedDescending()
        }

        // AUTO: a progressive MP4 (audio+video) is more reliable than a higher
        // video-only rung that still has to be muxed.
        if (targetHeight == null && !preferLowest) {
            pickCompatible(formats.filter { it.isProgressive && !it.requiresMuxing }, preferLowest = false)
                ?.let { return it }
        }

        for (height in heights) {
            pickCompatible(formats.filter { it.height == height }, preferLowest)?.let { return it }
        }
        pickCompatible(formats, preferLowest)?.let { return it }
        return best(formats, preferLowest)
    }

    private fun pickCompatible(pool: List<MediaFormat>, preferLowest: Boolean): MediaFormat? {
        if (pool.isEmpty()) return null
        val progressive = pool.filter { it.isProgressive && !it.requiresMuxing }
        if (progressive.isNotEmpty()) return best(progressive, preferLowest)
        val muxable = pool.filter { isMuxableAvcHevcMp4(it) }
        if (muxable.isNotEmpty()) return best(muxable, preferLowest)
        return null
    }

    private fun best(pool: List<MediaFormat>, preferLowest: Boolean): MediaFormat? {
        if (pool.isEmpty()) return null
        val bestClass = pool.maxOf { compatibilityScore(it) }
        val ranked = pool.filter { compatibilityScore(it) == bestClass }
        val byQuality = compareBy<MediaFormat> { it.height ?: 0 }
            .thenBy { it.fps ?: 0.0 }
            .thenBy { it.bitrate ?: 0L }
        return if (preferLowest) ranked.minWithOrNull(byQuality) else ranked.maxWithOrNull(byQuality)
    }

    private fun medianHeight(formats: List<MediaFormat>): Int? {
        val heights = formats.mapNotNull { it.height }.distinct().sorted()
        if (heights.isEmpty()) return null
        return heights[heights.size / 2]
    }

    private fun isMuxableAvcHevcMp4(format: MediaFormat): Boolean {
        val codec = format.videoCodec?.lowercase().orEmpty()
        val ext = format.extension?.lowercase().orEmpty()
        val container = format.container?.lowercase().orEmpty()
        val muxableCodec = codec.contains("avc") || codec.contains("h264") ||
            codec.contains("hev") || codec.contains("h265") || codec.contains("hvc")
        val mp4 = ext in setOf("mp4", "m4v") || container.contains("mp4")
        return muxableCodec && (mp4 || ext.isEmpty())
    }

    private fun compatibilityScore(format: MediaFormat): Int {
        val codec = format.videoCodec?.lowercase().orEmpty()
        val audio = format.audioCodec?.lowercase().orEmpty()
        val ext = format.extension?.lowercase().orEmpty()
        var score = 0
        if (format.isProgressive && !format.requiresMuxing) score += 8
        if (isMuxableAvcHevcMp4(format)) score += 6
        if (ext in setOf("mp4", "m4v", "m4a")) score += 2
        if (audio.contains("mp4a") || audio.contains("aac")) score += 2
        if (codec.contains("vp9") || codec.contains("vp09") || codec.contains("av01") ||
            (codec.contains("av1") && !codec.contains("avc"))
        ) {
            score -= 6
        }
        if (ext == "webm" || ext == "mkv") score -= 2
        if (audio.contains("opus")) score -= 1
        return score
    }
}

/**
 * Result of the local, offline URL validation.
 */
enum class ValidationState {
    /** Empty field. */
    Empty,

    /** Text that does not look like a URL. */
    Invalid,

    /** An HTTP URL, rejected for security. */
    NotHttps,

    /** A valid HTTPS URL. */
    Valid,
}

enum class AnalysisState { IDLE, ANALYZING, READY, FAILED }

/**
 * Explicit UI state for the Home screen.
 */
data class HomeUiState(
    val url: String = "",
    val mediaType: ContentType = ContentType.VIDEO,
    val qualityOptions: List<QualityOption> = listOf(QualityOption.AUTO),
    val quality: QualityOption = QualityOption.AUTO,
    val fileName: String = "",
    val suggestedFileName: String? = null,
    val validationState: ValidationState = ValidationState.Empty,
    /** Message shown as error (null when there is no error). */
    @StringRes val errorMessage: Int? = null,
    /** Informative message shown while typing (e.g. "Enlace válido"). */
    @StringRes val infoMessage: Int? = null,
    /** Whether the main download button is enabled. */
    val isDownloadButtonEnabled: Boolean = false,
    val analysisState: AnalysisState = AnalysisState.IDLE,
    val sourceInfo: SourceInfo? = null,
    val availableFormats: List<MediaFormat> = emptyList(),
    val selectedFormatId: String? = null,
    val analysisError: String? = null,
)
