package com.mediaflow.data.player.background

import android.media.AudioManager
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

    private fun createManager(
        onPause: () -> Unit = {},
        onResume: () -> Unit = {},
        onStop: () -> Unit = {},
        onDuck: (Boolean) -> Unit = {},
    ): AudioFocusManager {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        return AudioFocusManager(
            context = context,
            onPauseRequested = onPause,
            onResumeRequested = onResume,
            onStopRequested = onStop,
            onVolumeDuckRequested = onDuck,
        )
    }

    @Test
    fun `requestAudioFocus and abandonAudioFocus operate without crashes`() {
        var paused = false
        var resumed = false
        var stopped = false

        val manager = createManager(
            onPause = { paused = true },
            onResume = { resumed = true },
            onStop = { stopped = true },
        )

        val granted = manager.requestAudioFocus()
        assertTrue(granted)

        manager.abandonAudioFocus()
        assertFalse(paused)
        assertFalse(resumed)
        assertFalse(stopped)
    }

    @Test
    fun `permanent LOSS pauses without stop and does not auto-resume on GAIN`() {
        var pauseCount = 0
        var resumeCount = 0
        var stopCount = 0

        val manager = createManager(
            onPause = { pauseCount++ },
            onResume = { resumeCount++ },
            onStop = { stopCount++ },
        )
        assertTrue(manager.requestAudioFocus())

        manager.dispatchFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertTrue(manager.isPausedByAudioFocus)
        assertFalse(manager.hasAudioFocus)
        assertEquals(1, pauseCount)
        assertEquals(0, stopCount)

        manager.dispatchFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(0, resumeCount)

        assertTrue(manager.requestAudioFocus())
        assertFalse(manager.isPausedByAudioFocus)
        assertTrue(manager.hasAudioFocus)
    }

    @Test
    fun `transient LOSS pauses and resumes on GAIN unless user paused`() {
        var pauseCount = 0
        var resumeCount = 0
        var duck = false

        val manager = createManager(
            onPause = { pauseCount++ },
            onResume = { resumeCount++ },
            onDuck = { duck = it },
        )
        assertTrue(manager.requestAudioFocus())

        manager.dispatchFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(1, pauseCount)
        assertTrue(manager.isPausedByAudioFocus)

        manager.dispatchFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1, resumeCount)

        manager.dispatchFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertTrue(duck)

        manager.markPausedByUser()
        manager.dispatchFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        manager.dispatchFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1, resumeCount)
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
