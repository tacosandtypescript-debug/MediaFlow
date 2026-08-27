package com.mediaflow.app.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediaflow.app.data.GalleryPreferences
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.XSpace
import com.mediaflow.data.repository.MediaStoreGalleryRepository
import com.mediaflow.data.repository.ProgressRepositoryImpl
import com.mediaflow.data.repository.XSpaceRepositoryImpl
import com.mediaflow.domain.repository.GalleryRepository
import com.mediaflow.domain.repository.ProgressRepository
import com.mediaflow.domain.repository.XSpaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GalleryFilter { ALL, VIDEOS, AUDIO }
enum class GalleryViewMode { GRID, LIST }

data class GalleryUiState(
    val items: List<DownloadItem> = emptyList(),
    val progressMap: Map<String, PlaybackProgress> = emptyMap(),
    val spacesMap: Map<String, XSpace> = emptyMap(),
    val filter: GalleryFilter = GalleryFilter.ALL,
    val viewMode: GalleryViewMode = GalleryViewMode.GRID,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class GalleryViewModel(
    application: Application,
    private val repository: GalleryRepository = MediaStoreGalleryRepository(application),
    private val progressRepository: ProgressRepository = ProgressRepositoryImpl(application),
    private val spaceRepository: XSpaceRepository = XSpaceRepositoryImpl(application),
) : AndroidViewModel(application) {

    private val preferences = GalleryPreferences(application)
    private val filter = MutableStateFlow(GalleryFilter.ALL)
    private val viewMode = MutableStateFlow(GalleryViewMode.GRID)

    init {
        viewModelScope.launch {
            preferences.filter.collect { filter.value = it }
        }
        viewModelScope.launch {
            preferences.viewMode.collect { viewMode.value = it }
        }
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        repository.observeGallery()
            .map { Result.success(it) }
            .catch { emit(Result.failure(it)) }
            .flowOn(kotlinx.coroutines.Dispatchers.IO),
        progressRepository.observeAllProgress()
            .flowOn(kotlinx.coroutines.Dispatchers.IO),
        spaceRepository.observeAllSpaces()
            .flowOn(kotlinx.coroutines.Dispatchers.IO),
        filter,
        viewMode,
    ) { result, progressMap, spacesMap, selectedFilter, selectedView ->
        val items = result.getOrDefault(emptyList())
        GalleryUiState(
            items = items.filterFor(selectedFilter),
            progressMap = progressMap,
            spacesMap = spacesMap,
            filter = selectedFilter,
            viewMode = selectedView,
            isLoading = false,
            errorMessage = result.exceptionOrNull()?.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    fun setFilter(value: GalleryFilter) {
        filter.value = value
        viewModelScope.launch { preferences.setFilter(value) }
    }

    fun setViewMode(value: GalleryViewMode) {
        viewMode.value = value
        viewModelScope.launch { preferences.setViewMode(value) }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch { repository.deleteItem(id) }
    }

    fun refresh() {
        viewModelScope.launch { repository.getItemById("invalid://mediaflow") }
    }

    private fun List<DownloadItem>.filterFor(value: GalleryFilter): List<DownloadItem> = when (value) {
        GalleryFilter.ALL -> this
        GalleryFilter.VIDEOS -> filter { it.mediaType == MediaType.VIDEO }
        GalleryFilter.AUDIO -> filter { it.mediaType == MediaType.AUDIO }
    }

    class Factory(
        private val application: Application,
        private val repository: GalleryRepository? = null,
        private val progressRepository: ProgressRepository? = null,
        private val spaceRepository: XSpaceRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GalleryViewModel(
                application = application,
                repository = repository ?: MediaStoreGalleryRepository(application),
                progressRepository = progressRepository ?: ProgressRepositoryImpl(application),
                spaceRepository = spaceRepository ?: XSpaceRepositoryImpl(application),
            ) as T
        }
    }
}
