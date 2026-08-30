package com.mediaflow.app.ui.player.visualizer.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.visualizerDataStore by preferencesDataStore(name = "mediaflow_visualizer")

class VisualizerPreferences(private val context: Context) {
    val settings: Flow<VisualizerSettings> = context.visualizerDataStore.data.map { p ->
        VisualizerSettings(
            enabled = p[KEY_ENABLED] ?: false,
            style = p[KEY_STYLE]?.let { runCatching { VisualizerStyle.valueOf(it) }.getOrNull() }
                ?: VisualizerStyle.BALLS,
            intensity = p[KEY_INTENSITY] ?: 0.55f,
            motion = p[KEY_MOTION] ?: 0.55f,
            useCoverColors = p[KEY_COVER] ?: true,
            dynamicSystemBars = p[KEY_BARS] ?: true,
            reduceOnPause = p[KEY_PAUSE] ?: true,
            batterySaver = p[KEY_BATTERY] ?: false,
        )
    }

    suspend fun setEnabled(value: Boolean) = edit { it[KEY_ENABLED] = value }
    suspend fun setStyle(value: VisualizerStyle) = edit { it[KEY_STYLE] = value.name }
    suspend fun setIntensity(value: Float) = edit { it[KEY_INTENSITY] = value.coerceIn(0f, 1f) }
    suspend fun setMotion(value: Float) = edit { it[KEY_MOTION] = value.coerceIn(0f, 1f) }
    suspend fun setUseCoverColors(value: Boolean) = edit { it[KEY_COVER] = value }
    suspend fun setDynamicSystemBars(value: Boolean) = edit { it[KEY_BARS] = value }
    suspend fun setReduceOnPause(value: Boolean) = edit { it[KEY_PAUSE] = value }
    suspend fun setBatterySaver(value: Boolean) = edit { it[KEY_BATTERY] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.visualizerDataStore.edit(block)
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("viz_enabled")
        val KEY_STYLE = stringPreferencesKey("viz_style")
        val KEY_INTENSITY = floatPreferencesKey("viz_intensity")
        val KEY_MOTION = floatPreferencesKey("viz_motion")
        val KEY_COVER = booleanPreferencesKey("viz_cover")
        val KEY_BARS = booleanPreferencesKey("viz_bars")
        val KEY_PAUSE = booleanPreferencesKey("viz_pause")
        val KEY_BATTERY = booleanPreferencesKey("viz_battery")
    }
}
