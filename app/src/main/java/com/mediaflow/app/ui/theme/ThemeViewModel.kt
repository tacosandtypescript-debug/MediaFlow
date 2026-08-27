package com.mediaflow.app.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediaflow.app.data.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Exposes the currently selected [ThemeMode] as state and persists changes.
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = ThemePreferences(application)

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.themeMode
                .distinctUntilChanged()
                .collect { _themeMode.value = it }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
            _themeMode.value = mode
        }
    }
}
