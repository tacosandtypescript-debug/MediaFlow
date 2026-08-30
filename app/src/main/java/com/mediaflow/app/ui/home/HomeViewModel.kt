package com.mediaflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediaflow.app.R
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.data.resolver.PlatformUrlSupport
import com.mediaflow.domain.repository.SourceInfo
import com.mediaflow.domain.repository.SourceResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the Home screen state and performs local, offline validation.
 *
 * This class owns only local URL validation and form state. The navigation
 * layer delegates accepted requests to the data download adapters.
 */
class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var analysisJob: Job? = null

    fun onUrlChanged(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty() && trimmed == _uiState.value.url) return
        analysisJob?.cancel()
        updateUrlValidation(url)
    }

    fun onClearUrl() {
        analysisJob?.cancel()
        updateUrlValidation("")
    }

    fun onMediaTypeSelected(contentType: ContentType) {
        _uiState.update { applyContentType(it, contentType) }
    }

    fun onQualitySelected(quality: QualityOption) {
        if (quality !in _uiState.value.qualityOptions) return
        _uiState.update {
            val selectedId = PreferredDownloadFormat.select(it.availableFormats, quality)?.formatId
                ?: it.selectedFormatId
            it.copy(quality = quality, selectedFormatId = selectedId)
        }
    }

    fun onFormatSelected(formatId: String) {
        if (_uiState.value.availableFormats.any { it.formatId == formatId }) {
            _uiState.update { it.copy(selectedFormatId = formatId) }
        }
    }

    /** Performs the real source analysis used by the production Home screen. */
    fun analyze(sourceResolver: SourceResolver) {
        val current = _uiState.value
        if (current.validationState != ValidationState.Valid) return
        analysisJob?.cancel()
        _uiState.update {
            it.copy(
                analysisState = AnalysisState.ANALYZING,
                sourceInfo = null,
                suggestedFileName = null,
                availableFormats = emptyList(),
                selectedFormatId = null,
                analysisError = null,
                isDownloadButtonEnabled = false,
            )
        }
        analysisJob = viewModelScope.launch {
            runCatching { sourceResolver.analyze(current.url) }
                .onSuccess { info ->
                    _uiState.update { latest ->
                        val requested = when {
                            info.spaceMetadata != null -> ContentType.AUDIO
                            PlatformUrlSupport.isYoutubeMusic(latest.url) -> ContentType.AUDIO
                            else -> latest.mediaType
                        }
                        applyContentType(
                            latest.copy(
                                sourceInfo = info,
                                suggestedFileName = info.title
                                    ?.trim()
                                    ?.let(::sanitizeFileName)
                                    ?.takeIf { it.isNotBlank() },
                                analysisState = AnalysisState.READY,
                            ),
                            requested,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            analysisState = AnalysisState.FAILED,
                            availableFormats = emptyList(),
                            selectedFormatId = null,
                            analysisError = error.message ?: "No se pudo analizar la fuente.",
                            infoMessage = null,
                            isDownloadButtonEnabled = false,
                        )
                    }
                }
        }
    }

    fun onFileNameChanged(fileName: String) {
        _uiState.update { it.copy(fileName = sanitizeFileName(fileName)) }
    }

    fun onDownloadNow() {
        // No-op in HomeViewModel as downloading is delegated to DownloadViewModel/UseCase
    }

    /**
     * Internal validator that strictly checks for a standard HTTPS scheme
     * and a supported host.
     */
    /**
     * Reaplica Vídeo/Audio sobre el análisis ya hecho. Un Space de X permanece
     * en audio; si el usuario pide audio y la fuente solo trae vídeo, se ofrece
     * una pista de audio extraíble con yt-dlp (`bestaudio`).
     */
    private fun applyContentType(state: HomeUiState, contentType: ContentType): HomeUiState {
        val info = state.sourceInfo
        val isSpace = info?.spaceMetadata != null
        val effective = if (isSpace) ContentType.AUDIO else contentType
        val qualityOptions = QualityOption.optionsFor(effective)
        val quality = if (state.quality in qualityOptions) state.quality else QualityOption.AUTO
        if (info == null ||
            state.analysisState == AnalysisState.IDLE ||
            state.analysisState == AnalysisState.ANALYZING
        ) {
            return state.copy(
                mediaType = effective,
                qualityOptions = qualityOptions,
                quality = quality,
            )
        }
        val formats = formatsFor(info, effective)
        val error = info.errorMessage ?: if (formats.isEmpty() && !isSpace) {
            "La fuente no ofrece formatos para el tipo seleccionado."
        } else {
            null
        }
        return state.copy(
            mediaType = effective,
            qualityOptions = qualityOptions,
            quality = quality,
            availableFormats = formats,
            selectedFormatId = PreferredDownloadFormat.select(formats, quality)?.formatId,
            analysisState = if (error == null) AnalysisState.READY else AnalysisState.FAILED,
            analysisError = error,
            infoMessage = if (error == null) R.string.analysis_ready else null,
            isDownloadButtonEnabled = error == null,
        )
    }

    private fun updateUrlValidation(rawUrl: String) {
        val trimmed = rawUrl.trim()
        val validation = validateUrl(trimmed)
        _uiState.update {
            it.copy(
                url = trimmed,
                validationState = validation,
                errorMessage = when (validation) {
                    ValidationState.Empty -> R.string.home_error_empty
                    ValidationState.Invalid -> R.string.home_error_invalid
                    ValidationState.NotHttps -> R.string.home_error_http
                    ValidationState.Valid -> null
                },
                infoMessage = if (validation == ValidationState.Valid) R.string.home_info_valid else null,
                isDownloadButtonEnabled = validation == ValidationState.Valid,
                analysisState = AnalysisState.IDLE,
                sourceInfo = null,
                suggestedFileName = null,
                availableFormats = emptyList(),
                selectedFormatId = null,
                analysisError = null,
            )
        }
    }

    private fun validateUrl(url: String): ValidationState {
        if (url.isEmpty()) return ValidationState.Empty
        if (url.startsWith("http://", ignoreCase = true)) return ValidationState.NotHttps
        if (!url.startsWith("https://", ignoreCase = true)) return ValidationState.Invalid
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
            ?: return ValidationState.Invalid
        return if (host.contains('.')) ValidationState.Valid else ValidationState.Invalid
    }

    private fun sanitizeFileName(input: String): String {
        return input.replace(ILLEGAL_CHARACTERS_REGEX, "")
    }

    companion object {
        internal const val SYNTHETIC_AUDIO_FORMAT_ID = "bestaudio"
        private val ILLEGAL_CHARACTERS_REGEX = Regex("""[\\/:*?"<>|]""")

        internal fun formatsFor(info: SourceInfo, contentType: ContentType): List<MediaFormat> {
            if (contentType == ContentType.AUDIO || info.spaceMetadata != null) {
                val audio = info.availableFormats.filter { it.mediaType == MediaType.AUDIO }
                if (audio.isNotEmpty()) return audio
                if (info.spaceMetadata == null && info.availableFormats.any { it.mediaType == MediaType.VIDEO }) {
                    val video = info.availableFormats.first { it.mediaType == MediaType.VIDEO }
                    return listOf(
                        MediaFormat(
                            formatId = SYNTHETIC_AUDIO_FORMAT_ID,
                            extension = "m4a",
                            mimeType = "audio/mp4",
                            mediaType = MediaType.AUDIO,
                            qualityLabel = "Audio",
                            durationSeconds = info.durationSeconds ?: video.durationSeconds,
                            audioCodec = "mp4a",
                            isProgressive = true,
                            requiresMuxing = false,
                        ),
                    )
                }
                return audio
            }
            return info.availableFormats.filter { it.mediaType == MediaType.VIDEO }
        }
    }
}
