package com.mediaflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mediaflow.app.ui.downloads.DownloadsViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.downloadsDataStore by preferencesDataStore(name = "mediaflow_downloads")

/** Persists Downloads list/grid presentation. */
class DownloadsPreferences(private val context: Context) {
    val viewMode: Flow<DownloadsViewMode> = context.downloadsDataStore.data.map { preferences ->
        preferences[KEY_VIEW_MODE]?.let { runCatching { DownloadsViewMode.valueOf(it) }.getOrNull() }
            ?: DownloadsViewMode.LIST
    }

    suspend fun setViewMode(value: DownloadsViewMode) {
        context.downloadsDataStore.edit { it[KEY_VIEW_MODE] = value.name }
    }

    private companion object {
        val KEY_VIEW_MODE = stringPreferencesKey("downloads_view_mode")
    }
}
