package com.mediaflow.data.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFinishedGateTest {

    @Test
    fun `eof and END_FILE only emit PlaybackFinished once`() {
        val gate = PlaybackFinishedGate()
        assertTrue(gate.tryMarkEmitted())
        assertFalse(gate.tryMarkEmitted())
        assertFalse(gate.tryMarkEmitted())
    }

    @Test
    fun `reset allows a new finished event for the next load`() {
        val gate = PlaybackFinishedGate()
        assertTrue(gate.tryMarkEmitted())
        gate.reset()
        assertTrue(gate.tryMarkEmitted())
        assertFalse(gate.tryMarkEmitted())
    }
}
