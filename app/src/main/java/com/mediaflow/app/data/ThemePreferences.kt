package com.mediaflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mediaflow.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "mediaflow_settings")

/**
 * Persists the user's [ThemeMode] choice.
 */
class ThemePreferences(private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { preferences ->
        preferences[KEY_THEME_MODE]
            ?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
