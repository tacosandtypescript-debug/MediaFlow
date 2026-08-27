package com.mediaflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediaflow.app.R
import com.mediaflow.core.model.MediaType
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
        analysisJob?.cancel()
        updateUrlValidation(url)
    }

    fun onClearUrl() {
        analysisJob?.cancel()
        updateUrlValidation("")
    }

    fun onMediaTypeSelected(contentType: ContentType) {
        val qualityOptions = QualityOption.optionsFor(contentType)
        _uiState.update {
            it.copy(
                mediaType = contentType,
                qualityOptions = qualityOptions,
                quality = QualityOption.AUTO,
                analysisState = AnalysisState.IDLE,
                sourceInfo = null,
                availableFormats = emptyList(),
                selectedFormatId = null,
                analysisError = null,
                isDownloadButtonEnabled = it.validationState == ValidationState.Valid,
            )
        }
    }

    fun onQualitySelected(quality: QualityOption) {
        if (quality !in _uiState.value.qualityOptions) return
        _uiState.update { it.copy(quality = quality) }
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
                availableFormats = emptyList(),
                selectedFormatId = null,
                analysisError = null,
                isDownloadButtonEnabled = false,
            )
        }
        analysisJob = viewModelScope.launch {
            runCatching { sourceResolver.analyze(current.url) }
                .onSuccess { info ->
                    val isSpace = info.spaceMetadata != null
                    val effectiveMediaType = if (isSpace) ContentType.AUDIO else current.mediaType
                    val targetCoreType = if (effectiveMediaType == ContentType.VIDEO) MediaType.VIDEO else MediaType.AUDIO
                    val formats = info.availableFormats.filter { it.mediaType == targetCoreType }
                    val error = info.errorMessage ?: if (formats.isEmpty() && !isSpace) {
                        "La fuente no ofrece formatos para el tipo seleccionado."
                    } else null
                    _uiState.update {
                        it.copy(
                            mediaType = effectiveMediaType,
                            qualityOptions = QualityOption.optionsFor(effectiveMediaType),
                            analysisState = if (error == null) AnalysisState.READY else AnalysisState.FAILED,
                            sourceInfo = info,
                            availableFormats = formats,
                            selectedFormatId = formats.firstOrNull()?.formatId,
                            analysisError = error,
                            infoMessage = if (error == null) R.string.analysis_ready else null,
                            isDownloadButtonEnabled = error == null,
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
        private val ILLEGAL_CHARACTERS_REGEX = Regex("""[\\/:*?"<>|]""")
    }
}
