package com.mediaflow.app.ui.player.visualizer.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisualizerPreferencesTest {
    @Test
    fun persistsOffOnAndEachStyle() = runBlocking {
        val prefs = VisualizerPreferences(ApplicationProvider.getApplicationContext())
        prefs.setEnabled(false)
        assertFalse(prefs.settings.first().enabled)
        prefs.setEnabled(true)
        assertTrue(prefs.settings.first().enabled)
        VisualizerStyle.entries.forEach { style ->
            prefs.setStyle(style)
            assertEquals(style, prefs.settings.first().style)
        }
        prefs.setIntensity(0.2f)
        prefs.setMotion(0.9f)
        val s = prefs.settings.first()
        assertEquals(0.2f, s.intensity, 0.001f)
        assertEquals(0.9f, s.motion, 0.001f)
        assertEquals(0, VisualizerSettings(enabled = false).particleCount())
    }
}
