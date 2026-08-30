package com.mediaflow.domain.player.xspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XSpaceLivePlayerMachineTest {

    @Test
    fun livePauseGoesBehindLiveWithNegativeLag() {
        val live = XSpaceLivePlayerMachine.reduce(
            XSpaceLivePlayerMachine.initial(),
            XSpaceLivePlayerEvent.OpenLive(liveSeekAllowed = false),
        ).let { XSpaceLivePlayerMachine.reduce(it, XSpaceLivePlayerEvent.ConnectedAtLiveEdge) }

        assertEquals(XSpacePlaybackMode.LIVE, live.playback)
        assertEquals(0L, live.liveLagMs)

        val behind = XSpaceLivePlayerMachine.reduce(live, XSpaceLivePlayerEvent.Pause)
        assertEquals(XSpacePlaybackMode.BEHIND_LIVE, behind.playback)
        assertTrue(behind.liveControlActive)
        assertEquals(live.sessionGeneration, behind.sessionGeneration)

        val lagged = XSpaceLivePlayerMachine.reduce(
            behind,
            XSpaceLivePlayerEvent.LagSample(-24_000L),
        )
        assertEquals(XSpacePlaybackMode.BEHIND_LIVE, lagged.playback)
        assertEquals(-24_000L, lagged.liveLagMs)
        assertEquals("-00:24", LiveLagMath.format(lagged.liveLagMs))
    }

    @Test
    fun lagSampleDoesNotHardcodeMinusOne() {
        val live = XSpaceLivePlayerMachine.reduce(
            XSpaceLivePlayerMachine.initial(),
            XSpaceLivePlayerEvent.OpenLive(liveSeekAllowed = false),
        ).let { XSpaceLivePlayerMachine.reduce(it, XSpaceLivePlayerEvent.ConnectedAtLiveEdge) }
        val sample = XSpaceLivePlayerMachine.reduce(live, XSpaceLivePlayerEvent.LagSample(-136_000L))
        assertEquals(-136_000L, sample.liveLagMs)
        assertEquals(XSpacePlaybackMode.BEHIND_LIVE, sample.playback)
    }

    @Test
    fun jumpToLiveEdgeReturnsToLive() {
        val behind = openLiveThenPause()
        val live = XSpaceLivePlayerMachine.reduce(behind, XSpaceLivePlayerEvent.JumpToLiveEdge)
        assertEquals(XSpacePlaybackMode.LIVE, live.playback)
        assertEquals(0L, live.liveLagMs)
        assertTrue(live.liveControlActive)
        assertEquals(XSpaceConnectionState.CONNECTED, live.connection)
    }

    @Test
    fun endedReplayDisablesLiveControl() {
        val replay = XSpaceLivePlayerMachine.reduce(
            XSpaceLivePlayerMachine.initial(),
            XSpaceLivePlayerEvent.OpenReplay(seekAllowed = true),
        )
        assertEquals(XSpacePlaybackMode.REPLAY, replay.playback)
        assertEquals(XSpaceConnectionState.ENDED, replay.connection)
        assertFalse(replay.liveControlActive)
        assertTrue(replay.replaySeekAllowed)

        val jumped = XSpaceLivePlayerMachine.reduce(replay, XSpaceLivePlayerEvent.JumpToLiveEdge)
        assertFalse(jumped.liveControlActive)
        assertEquals(XSpacePlaybackMode.REPLAY, jumped.playback)
    }

    @Test
    fun liveIngestEndGoesEndedWithoutRemount() {
        val live = XSpaceLivePlayerMachine.reduce(
            XSpaceLivePlayerMachine.initial(),
            XSpaceLivePlayerEvent.OpenLive(liveSeekAllowed = false),
        ).let { XSpaceLivePlayerMachine.reduce(it, XSpaceLivePlayerEvent.ConnectedAtLiveEdge) }
        val generation = live.sessionGeneration

        val ended = XSpaceLivePlayerMachine.reduce(live, XSpaceLivePlayerEvent.IngestEnded)
        assertEquals(XSpaceConnectionState.ENDED, ended.connection)
        assertFalse(ended.liveControlActive)
        assertEquals(generation, ended.sessionGeneration)

        val replay = XSpaceLivePlayerMachine.reduce(
            ended,
            XSpaceLivePlayerEvent.StartReplay(seekAllowed = true),
        )
        assertEquals(generation, replay.sessionGeneration)
        assertEquals(XSpacePlaybackMode.REPLAY, replay.playback)
        assertFalse(replay.liveControlActive)
    }

    @Test
    fun endedReplayStaysReplayWhileEngineBuffers() {
        val replay = XSpaceLivePlayerMachine.reduce(
            XSpaceLivePlayerMachine.initial(),
            XSpaceLivePlayerEvent.OpenReplay(seekAllowed = true),
        )
        val buffering = XSpaceLivePlayerMachine.reduce(replay, XSpaceLivePlayerEvent.Buffering)
        assertEquals(XSpacePlaybackMode.REPLAY, buffering.playback)
        assertEquals(XSpaceConnectionState.ENDED, buffering.connection)
        assertFalse(buffering.liveControlActive)
        val playing = XSpaceLivePlayerMachine.reduce(buffering, XSpaceLivePlayerEvent.Resume)
        assertEquals(XSpacePlaybackMode.REPLAY, playing.playback)
        assertFalse(playing.liveControlActive)
    }

    private fun openLiveThenPause(): XSpaceLivePlayerState {
        val live = XSpaceLivePlayerMachine.reduce(
            XSpaceLivePlayerMachine.initial(),
            XSpaceLivePlayerEvent.OpenLive(liveSeekAllowed = false),
        ).let { XSpaceLivePlayerMachine.reduce(it, XSpaceLivePlayerEvent.ConnectedAtLiveEdge) }
        return XSpaceLivePlayerMachine.reduce(live, XSpaceLivePlayerEvent.Pause)
    }
}
