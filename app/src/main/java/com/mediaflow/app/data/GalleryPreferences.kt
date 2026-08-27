package com.mediaflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mediaflow.app.ui.gallery.GalleryFilter
import com.mediaflow.app.ui.gallery.GalleryViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.galleryDataStore by preferencesDataStore(name = "mediaflow_gallery")

/** Persists non-destructive gallery presentation choices. */
class GalleryPreferences(private val context: Context) {
    val filter: Flow<GalleryFilter> = context.galleryDataStore.data.map { preferences ->
        preferences[KEY_FILTER]?.let { runCatching { GalleryFilter.valueOf(it) }.getOrNull() }
            ?: GalleryFilter.ALL
    }

    val viewMode: Flow<GalleryViewMode> = context.galleryDataStore.data.map { preferences ->
        preferences[KEY_VIEW_MODE]?.let { runCatching { GalleryViewMode.valueOf(it) }.getOrNull() }
            ?: GalleryViewMode.GRID
    }

    suspend fun setFilter(value: GalleryFilter) {
        context.galleryDataStore.edit { it[KEY_FILTER] = value.name }
    }

    suspend fun setViewMode(value: GalleryViewMode) {
        context.galleryDataStore.edit { it[KEY_VIEW_MODE] = value.name }
    }

    private companion object {
        val KEY_FILTER = stringPreferencesKey("gallery_filter")
        val KEY_VIEW_MODE = stringPreferencesKey("gallery_view_mode")
    }
}
