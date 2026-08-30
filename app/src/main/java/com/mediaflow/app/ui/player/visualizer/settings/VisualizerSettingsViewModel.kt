package com.mediaflow.app.ui.player.visualizer.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VisualizerSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = VisualizerPreferences(application)
    val settings: StateFlow<VisualizerSettings> = prefs.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        VisualizerSettings(),
    )

    fun setEnabled(v: Boolean) = viewModelScope.launch { prefs.setEnabled(v) }
    fun setStyle(v: VisualizerStyle) = viewModelScope.launch { prefs.setStyle(v) }
    fun setIntensity(v: Float) = viewModelScope.launch { prefs.setIntensity(v) }
    fun setMotion(v: Float) = viewModelScope.launch { prefs.setMotion(v) }
    fun setUseCoverColors(v: Boolean) = viewModelScope.launch { prefs.setUseCoverColors(v) }
    fun setDynamicSystemBars(v: Boolean) = viewModelScope.launch { prefs.setDynamicSystemBars(v) }
    fun setReduceOnPause(v: Boolean) = viewModelScope.launch { prefs.setReduceOnPause(v) }
    fun setBatterySaver(v: Boolean) = viewModelScope.launch { prefs.setBatterySaver(v) }
}
