package com.mediaflow.app.ui.downloads

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediaflow.app.ui.home.HomeUiState
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.XSpace
import com.mediaflow.data.repository.Media3DownloadRepository
import com.mediaflow.data.repository.ProgressRepositoryImpl
import com.mediaflow.data.repository.XSpaceRepositoryImpl
import com.mediaflow.data.resolver.DirectUrlSourceResolver
import com.mediaflow.data.resolver.YtDlpSourceResolver
import com.mediaflow.domain.repository.DownloadRepository
import com.mediaflow.domain.repository.ProgressRepository
import com.mediaflow.domain.repository.XSpaceRepository
import com.mediaflow.domain.usecase.CancelDownloadUseCase
import com.mediaflow.domain.usecase.GetDownloadsUseCase
import com.mediaflow.domain.usecase.PauseDownloadUseCase
import com.mediaflow.domain.usecase.RemoveDownloadUseCase
import com.mediaflow.domain.usecase.RetryDownloadUseCase
import com.mediaflow.domain.usecase.ResumeDownloadUseCase
import com.mediaflow.domain.usecase.StartDownloadUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

sealed interface DownloadStartResult {
    data object Accepted : DownloadStartResult
    data object AwaitingNotificationPermission : DownloadStartResult
    data class Rejected(val message: String) : DownloadStartResult
}

sealed interface DownloadEvent {
    data class Started(val id: String) : DownloadEvent
    data class Failed(val message: String) : DownloadEvent
}

/** Coordinates UI actions with the real domain/data download adapter. */
class DownloadViewModel(
    application: Application,
    private val progressRepository: ProgressRepository = ProgressRepositoryImpl(application),
    private val spaceRepository: XSpaceRepository = XSpaceRepositoryImpl(application),
) : AndroidViewModel(application) {

    private val repositoryDeferred = viewModelScope.async(Dispatchers.IO) {
        Media3DownloadRepository.get(application)
    }
    private val resolver = DirectUrlSourceResolver()
    private val sourceResolver = YtDlpSourceResolver(application)
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    val progressMap: StateFlow<Map<String, PlaybackProgress>> = progressRepository.observeAllProgress()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val spacesMap: StateFlow<Map<String, XSpace>> = spaceRepository.observeAllSpaces()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val repository = repositoryDeferred.await()
            GetDownloadsUseCase(repository)().collect { _downloads.value = it }
        }
    }

    fun start(state: HomeUiState): DownloadStartResult {
        android.util.Log.d("MediaFlow", "DownloadViewModel.start called with state.url=${state.url}")
        val request = resolver.createRequest(
            sourceUrl = state.url,
            mediaType = state.mediaType.toCoreType(),
            qualityLabel = getApplication<Application>().getString(state.quality.labelRes),
            customFileName = state.fileName,
        ).getOrElse {
            android.util.Log.e("MediaFlow", "createRequest error: ${it.message}", it)
            return DownloadStartResult.Rejected(it.message.orEmpty())
        }

        viewModelScope.launch {
            val repository = repositoryDeferred.await()
            runCatching {
                val source = sourceResolver.analyze(state.url)
                check(source.errorMessage == null && source.availableFormats.isNotEmpty()) {
                    source.errorMessage ?: "La fuente no devolvió calidades descargables."
                }
                val selected = selectFormat(
                    source.availableFormats,
                    state.mediaType.toCoreType(),
                    state.quality,
                    state.selectedFormatId,
                )
                check(selected != null) { "La calidad seleccionada no está disponible en esta fuente." }

                val space = source.spaceMetadata
                val finalFileName = if (state.fileName.isNotBlank()) {
                    state.fileName
                } else if (space != null) {
                    space.title
                } else {
                    source.title ?: request.fileName
                }

                if (space != null) {
                    spaceRepository.saveSpace(space, mediaId = request.sourceUrl)
                }

                val downloadId = StartDownloadUseCase(repository)(request.copy(
                    fileName = finalFileName,
                    mediaType = selected.mediaType,
                    formatId = selected.formatId,
                    mimeType = selected.mimeType ?: request.mimeType,
                    extension = selected.extension ?: request.extension,
                    durationSeconds = source.durationSeconds,
                    requiresMuxing = selected.requiresMuxing,
                    width = selected.width,
                    height = selected.height,
                    fps = selected.fps,
                    container = selected.container,
                    videoCodec = selected.videoCodec,
                    audioCodec = selected.audioCodec,
                ))

                if (space != null) {
                    spaceRepository.saveSpace(space, mediaId = downloadId)
                }
                android.util.Log.d("MediaFlow", "Download started successfully with id=$downloadId")
                _events.emit(DownloadEvent.Started(downloadId))
            }.onFailure { error ->
                android.util.Log.e("MediaFlow", "DownloadViewModel failed to start download", error)
                _events.emit(DownloadEvent.Failed(error.message ?: "No se pudo iniciar la descarga"))
            }
        }
        return DownloadStartResult.Accepted
    }

    fun pause(id: String) = viewModelScope.launch { PauseDownloadUseCase(repositoryDeferred.await())(id) }
    fun resume(id: String) = viewModelScope.launch { ResumeDownloadUseCase(repositoryDeferred.await())(id) }
    fun cancel(id: String) = viewModelScope.launch { CancelDownloadUseCase(repositoryDeferred.await())(id) }
    fun retry(id: String) = viewModelScope.launch { RetryDownloadUseCase(repositoryDeferred.await())(id) }
    fun remove(id: String) = viewModelScope.launch { RemoveDownloadUseCase(repositoryDeferred.await())(id) }

    fun open(item: DownloadItem) {
        if (item.status != com.mediaflow.core.model.DownloadStatus.COMPLETED) return
        val localUri = item.localUri ?: return
        runCatching {
            val file = File(localUri.toUri().path ?: error("URI local no válida"))
            require(file.isFile) { "El archivo descargado ya no existe" }
            val application = getApplication<Application>()
            val contentUri = FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(contentUri, item.selectedFormat?.mimeType ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            application.startActivity(intent)
        }.onFailure { error ->
            _events.tryEmit(
                DownloadEvent.Failed(error.message ?: "No se pudo abrir el archivo descargado"),
            )
        }
    }

    private fun com.mediaflow.app.ui.home.ContentType.toCoreType() =
        if (this == com.mediaflow.app.ui.home.ContentType.VIDEO) MediaType.VIDEO else MediaType.AUDIO

    private fun selectFormat(
        formats: List<MediaFormat>,
        targetType: MediaType,
        quality: com.mediaflow.app.ui.home.QualityOption,
        selectedFormatId: String?,
    ): MediaFormat? {
        val preferred = formats.filter { it.mediaType == targetType }
        val typed = preferred.ifEmpty { formats }
        if (selectedFormatId != null) {
            val manual = typed.firstOrNull { it.formatId == selectedFormatId }
            if (manual != null) return manual
        }
        return when (quality) {
            com.mediaflow.app.ui.home.QualityOption.AUTO -> typed.firstOrNull()
            com.mediaflow.app.ui.home.QualityOption.HIGH -> typed.firstOrNull()
            com.mediaflow.app.ui.home.QualityOption.MEDIUM -> typed.getOrNull(typed.size / 2) ?: typed.firstOrNull()
            com.mediaflow.app.ui.home.QualityOption.LOW -> typed.lastOrNull()
            else -> typed.firstOrNull()
        }
    }

    class Factory(
        private val application: Application,
        private val progressRepository: ProgressRepository? = null,
        private val spaceRepository: XSpaceRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DownloadViewModel(
                application = application,
                progressRepository = progressRepository ?: ProgressRepositoryImpl(application),
                spaceRepository = spaceRepository ?: XSpaceRepositoryImpl(application),
            ) as T
        }
    }
}
