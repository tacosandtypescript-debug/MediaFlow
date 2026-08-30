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
        assertTrue(behind.liveLagMs < 0L)
        assertTrue(behind.liveControlActive)
        assertEquals(live.sessionGeneration, behind.sessionGeneration)
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

    private fun openLiveThenPause(): XSpaceLivePlayerState {
        val live = XSpaceLivePlayerMachine.reduce(
            XSpaceLivePlayerMachine.initial(),
            XSpaceLivePlayerEvent.OpenLive(liveSeekAllowed = false),
        ).let { XSpaceLivePlayerMachine.reduce(it, XSpaceLivePlayerEvent.ConnectedAtLiveEdge) }
        return XSpaceLivePlayerMachine.reduce(live, XSpaceLivePlayerEvent.Pause)
    }
}
