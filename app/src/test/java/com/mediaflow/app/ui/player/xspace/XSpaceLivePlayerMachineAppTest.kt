package com.mediaflow.app.ui.player.xspace

import com.mediaflow.domain.player.xspace.XSpaceConnectionState
import com.mediaflow.domain.player.xspace.XSpaceLivePlayerEvent
import com.mediaflow.domain.player.xspace.XSpaceLivePlayerMachine
import com.mediaflow.domain.player.xspace.XSpacePlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XSpaceLivePlayerMachineAppTest {

    @Test
    fun livePlusPauseIsBehindLiveWithNegativeLag() {
        val live = connectedLive()
        val paused = XSpaceLivePlayerMachine.reduce(live, XSpaceLivePlayerEvent.Pause)
        assertEquals(XSpacePlaybackMode.BEHIND_LIVE, paused.playback)
        val behind = XSpaceLivePlayerMachine.reduce(
            paused,
            XSpaceLivePlayerEvent.LagSample(-24_000L),
        )
        assertTrue("lag must be negative while behind live", behind.liveLagMs < 0L)
        assertEquals(-24_000L, behind.liveLagMs)
    }

    @Test
    fun jumpToLiveEdgeIsLive() {
        val live = XSpaceLivePlayerMachine.reduce(connectedLive().let {
            XSpaceLivePlayerMachine.reduce(it, XSpaceLivePlayerEvent.Pause)
        }, XSpaceLivePlayerEvent.JumpToLiveEdge)
        assertEquals(XSpacePlaybackMode.LIVE, live.playback)
        assertEquals(0L, live.liveLagMs)
    }

    @Test
    fun endedReplayDoesNotActivateLiveControl() {
        val replay = XSpaceLivePlayerMachine.reduce(
            XSpaceLivePlayerMachine.initial(),
            XSpaceLivePlayerEvent.OpenReplay(seekAllowed = true),
        )
        assertEquals(XSpacePlaybackMode.REPLAY, replay.playback)
        assertFalse(replay.liveControlActive)
    }

    @Test
    fun liveIngestEndIsEndedWithoutRemount() {
        val live = connectedLive()
        val ended = XSpaceLivePlayerMachine.reduce(live, XSpaceLivePlayerEvent.IngestEnded)
        assertEquals(XSpaceConnectionState.ENDED, ended.connection)
        assertEquals(live.sessionGeneration, ended.sessionGeneration)
    }

    private fun connectedLive() = XSpaceLivePlayerMachine.reduce(
        XSpaceLivePlayerMachine.reduce(
            XSpaceLivePlayerMachine.initial(),
            XSpaceLivePlayerEvent.OpenLive(liveSeekAllowed = false),
        ),
        XSpaceLivePlayerEvent.ConnectedAtLiveEdge,
    )
}
