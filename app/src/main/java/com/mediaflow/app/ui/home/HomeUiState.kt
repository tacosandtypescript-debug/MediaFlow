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
enum class ContentType(@StringRes val labelRes: Int) {
    VIDEO(R.string.media_type_video),
    AUDIO(R.string.media_type_audio),
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
    val qualityOptions: List<QualityOption> = QualityOption.videoOptions,
    val quality: QualityOption = QualityOption.AUTO,
    val fileName: String = "",
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
