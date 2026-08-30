package com.mediaflow.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvAudioAnalysisTest {
    @Test
    fun analysisDoesNotRunWhenVisualizerOff() {
        assertFalse(MpvAudioAnalysis.shouldRun(visualizerEnabled = false, isPlaying = true))
        assertFalse(MpvAudioAnalysis.shouldRun(visualizerEnabled = true, isPlaying = false))
        assertTrue(MpvAudioAnalysis.shouldRun(visualizerEnabled = true, isPlaying = true))
    }

    @Test
    fun silenceMetadataIsNearZero() {
        val frame = MpvAudioAnalysis.frameFromMetadata(
            mapOf("lavfi.astats.Overall.RMS_level" to "-90"),
            isPlaying = true,
        )
        assertTrue(frame.rms < 0.05f)
        assertTrue(frame.bass < 0.05f)
    }

    @Test
    fun pauseMetadataIsSilent() {
        val frame = MpvAudioAnalysis.frameFromMetadata(
            mapOf("lavfi.astats.Overall.RMS_level" to "-6"),
            isPlaying = false,
        )
        assertFalse(frame.isPlaying)
        assertEquals(0f, frame.rms, 0.0001f)
    }

    @Test
    fun loudRmsParsesAboveSilence() {
        val map = MpvAudioAnalysis.parseMetadataBlob("lavfi.astats.Overall.RMS_level=-6")
        val frame = MpvAudioAnalysis.frameFromMetadata(map, isPlaying = true)
        assertTrue(frame.rms > 0.2f)
        assertTrue(frame.isPlaying)
    }

    @Test
    fun bridgeOffDropsPublish() {
        PlaybackAudioBridge.setVisualizerEnabled(false)
        PlaybackAudioBridge.publish(
            PlaybackPcmFrame(rms = 0.9f, isPlaying = true),
        )
        assertEquals(0f, PlaybackAudioBridge.frame.value.rms, 0.0001f)
    }

    @Test
    fun seekResetZerosFrame() {
        PlaybackAudioBridge.setVisualizerEnabled(true)
        PlaybackAudioBridge.publish(PlaybackPcmFrame(rms = 0.8f, isPlaying = true))
        PlaybackAudioBridge.reset()
        assertEquals(0f, PlaybackAudioBridge.frame.value.rms, 0.0001f)
        PlaybackAudioBridge.setVisualizerEnabled(false)
    }
}
