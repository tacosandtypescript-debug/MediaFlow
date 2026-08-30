package com.mediaflow.data.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFinishedGateTest {

    @Test
    fun `eof and END_FILE only emit PlaybackFinished once`() {
        val gate = PlaybackFinishedGate()
        gate.markStarted()
        assertTrue(gate.tryMarkEmitted())
        assertFalse(gate.tryMarkEmitted())
        assertFalse(gate.tryMarkEmitted())
    }

    @Test
    fun `reset allows a new finished event for the next load`() {
        val gate = PlaybackFinishedGate()
        gate.markStarted()
        assertTrue(gate.tryMarkEmitted())
        gate.reset()
        gate.markStarted()
        assertTrue(gate.tryMarkEmitted())
        assertFalse(gate.tryMarkEmitted())
    }

    @Test
    fun `END_FILE before playback starts is ignored`() {
        val gate = PlaybackFinishedGate()
        gate.reset()
        assertFalse(gate.tryMarkEmitted())
        gate.markStarted()
        assertTrue(gate.tryMarkEmitted())
    }
}
