package com.mediaflow.app.ui.player.visualizer.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioReactiveMapperTest {
    @Test
    fun pauseCollapsesReaction() {
        val s = AudioReactiveMapper.fromBands(0.9f, 0.8f, 0.7f, 0.6f, isPlaying = false, previousEnergy = 0.2f)
        assertFalse(s.beat)
        assertTrue(s.amplitude < AudioReactiveMapper.PauseCeiling)
        assertTrue(s.energy < AudioReactiveMapper.PauseCeiling)
        assertFalse(s.isPlaying)
    }

    @Test
    fun silenceIsNearZero() {
        val s = AudioReactiveMapper.fromBands(0f, 0f, 0f, 0f, isPlaying = true, previousEnergy = 0f)
        assertFalse(s.beat)
        assertTrue(s.energy < 0.05f)
    }

    @Test
    fun energyJumpIsBeat() {
        val s = AudioReactiveMapper.fromBands(0.9f, 0.5f, 0.2f, 0.7f, isPlaying = true, previousEnergy = 0.1f)
        assertTrue(s.beat)
        assertTrue(s.bass > 0.5f)
    }

    @Test
    fun offFlagDisablesAnalysis() {
        assertFalse(AudioReactiveMapper.shouldAnalyze(false))
        assertTrue(AudioReactiveMapper.shouldAnalyze(true))
    }
}
