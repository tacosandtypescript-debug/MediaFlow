package com.mediaflow.app.ui.player.visualizer.audio

import com.mediaflow.data.player.PlaybackAudioBridge
import com.mediaflow.data.player.PlaybackPcmFrame
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OutputMixAnalyzerTest {
    private lateinit var analyzer: OutputMixAnalyzer

    @Before
    fun setUp() {
        PlaybackAudioBridge.setVisualizerEnabled(false)
        analyzer = OutputMixAnalyzer()
    }

    @After
    fun tearDown() {
        analyzer.release()
        PlaybackAudioBridge.setVisualizerEnabled(false)
    }

    @Test
    fun visualizerOffDoesNotKeepAnalysisRunning() {
        analyzer.setEnabled(enabled = true, isPlaying = true)
        assertTrue(analyzer.isAnalyzing)
        analyzer.setEnabled(enabled = false, isPlaying = true)
        assertFalse(analyzer.isAnalyzing)
        assertFalse(PlaybackAudioBridge.enabled.value)
    }

    @Test
    fun pauseStopsAnalysis() {
        analyzer.setEnabled(true, true)
        analyzer.onTransport(OutputMixAnalyzer.Transport.Pause)
        assertFalse(analyzer.isAnalyzing)
        assertFalse(analyzer.state.value.beat)
        assertTrue(analyzer.state.value.energy < AudioReactiveMapper.PauseCeiling)
    }

    @Test
    fun seekNextPreviousZeroAnalysis() {
        analyzer.setEnabled(true, true)
        analyzer.ingestPcm(ByteArray(32) { 200.toByte() }, isPlaying = true)
        analyzer.onTransport(OutputMixAnalyzer.Transport.Seek)
        assertTrue(analyzer.state.value.energy < 0.05f)
        analyzer.onTransport(OutputMixAnalyzer.Transport.Next)
        assertTrue(analyzer.state.value.energy < 0.05f)
        analyzer.onTransport(OutputMixAnalyzer.Transport.Previous)
        assertTrue(analyzer.state.value.energy < 0.05f)
    }

    @Test
    fun ingestSilenceNearZeroWhenPlaying() {
        analyzer.setEnabled(true, true)
        val s = analyzer.ingest(
            PlaybackPcmFrame(pcm = ByteArray(64) { 128.toByte() }, isPlaying = true),
        )
        assertTrue(s.energy < 0.05f)
        assertFalse(s.beat)
    }

    @Test
    fun ingestEnergyJumpIsBeatWhenPlaying() {
        analyzer.setEnabled(true, true)
        analyzer.ingest(
            PlaybackPcmFrame(bass = 0.1f, rms = 0.1f, isPlaying = true),
        )
        val s = analyzer.ingest(
            PlaybackPcmFrame(bass = 0.95f, mids = 0.5f, highs = 0.2f, rms = 0.8f, isPlaying = true),
        )
        assertTrue(s.beat)
        assertTrue(s.peak > 0.5f)
    }

    @Test
    fun notPlayingIngestCollapses() {
        analyzer.setEnabled(true, false)
        assertFalse(analyzer.isAnalyzing)
        val s = analyzer.ingest(
            PlaybackPcmFrame(bass = 0.9f, rms = 0.9f, isPlaying = false),
        )
        assertFalse(s.isPlaying)
        assertTrue(s.energy < AudioReactiveMapper.PauseCeiling)
    }
}
