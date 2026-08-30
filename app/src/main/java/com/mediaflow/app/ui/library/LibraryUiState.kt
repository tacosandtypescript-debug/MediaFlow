package com.mediaflow.app.ui.library

import com.mediaflow.app.ui.library.components.AudioLibraryTab
import com.mediaflow.app.ui.library.components.LibraryFilter
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.Playlist
import com.mediaflow.core.model.XSpace

/**
 * Consolidates full UI state for the Library tab (Audio, Video, Playlists, Favorites).
 */
data class LibraryUiState(
    val selectedFilter: LibraryFilter = LibraryFilter.ALL,
    val selectedMediaType: MediaType = MediaType.AUDIO,
    val selectedAudioTab: AudioLibraryTab = AudioLibraryTab.ALL,
    val allItems: List<DownloadItem> = emptyList(),
    val audioItems: List<DownloadItem> = emptyList(),
    val videoItems: List<DownloadItem> = emptyList(),
    val favoriteItems: List<DownloadItem> = emptyList(),
    val favoriteUris: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val spacesMap: Map<String, XSpace> = emptyMap(),
    val progressMap: Map<String, PlaybackProgress> = emptyMap(),
    val playingMediaId: String? = null,
    val isPlayerPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
