package com.mediaflow.app.ui.player.visualizer.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

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
    fun notPlayingPcmCollapses() {
        val pcm = sinePcm(64, 4)
        val s = AudioReactiveMapper.fromPcm(pcm, isPlaying = false, previousEnergy = 0.4f)
        assertFalse(s.beat)
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
    fun silentPcmIsNearZero() {
        val pcm = ByteArray(64) { 128.toByte() }
        val s = AudioReactiveMapper.fromPcm(pcm, isPlaying = true, previousEnergy = 0f)
        assertFalse(s.beat)
        assertTrue(s.energy < 0.05f)
        assertTrue(s.amplitude < 0.05f)
    }

    @Test
    fun energyJumpIsBeat() {
        val s = AudioReactiveMapper.fromBands(0.9f, 0.5f, 0.2f, 0.7f, isPlaying = true, previousEnergy = 0.1f)
        assertTrue(s.beat)
        assertTrue(s.bass > 0.5f)
    }

    @Test
    fun pcmEnergyJumpCanRegisterPeak() {
        val pcm = sinePcm(128, 2)
        val s = AudioReactiveMapper.fromPcm(pcm, isPlaying = true, previousEnergy = 0f)
        assertTrue(s.peak > 0.05f || s.amplitude > 0.05f)
        assertTrue(s.isPlaying)
    }

    @Test
    fun offFlagDisablesAnalysis() {
        assertFalse(AudioReactiveMapper.shouldAnalyze(false))
        assertTrue(AudioReactiveMapper.shouldAnalyze(true))
        assertFalse(AudioReactiveMapper.shouldAnalyze(true, isPlaying = false))
        assertFalse(AudioReactiveMapper.shouldAnalyze(false, isPlaying = true))
        assertTrue(AudioReactiveMapper.shouldAnalyze(true, isPlaying = true))
    }

    private fun sinePcm(n: Int, cycles: Int): ByteArray {
        return ByteArray(n) { i ->
            val v = sin(2.0 * Math.PI * cycles * i / n)
            (128 + (v * 90).toInt()).coerceIn(0, 255).toByte()
        }
    }
}
