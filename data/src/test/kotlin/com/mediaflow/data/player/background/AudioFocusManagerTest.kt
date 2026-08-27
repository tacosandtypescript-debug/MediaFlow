package com.mediaflow.data.player.background

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AudioFocusManagerTest {

    @Test
    fun `requestAudioFocus and abandonAudioFocus operate without crashes`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        var paused = false
        var resumed = false
        var stopped = false

        val manager = AudioFocusManager(
            context = context,
            onPauseRequested = { paused = true },
            onResumeRequested = { resumed = true },
            onStopRequested = { stopped = true },
        )

        val granted = manager.requestAudioFocus()
        assertTrue(granted)

        manager.abandonAudioFocus()
        assertFalse(paused)
        assertFalse(resumed)
        assertFalse(stopped)
    }
}
