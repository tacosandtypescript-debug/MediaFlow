package com.mediaflow.app.ui.player.gestures

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkSwipeMathTest {
    @Test
    fun swipeLeftPastThresholdCommitsNext() {
        assertEquals(
            ArtworkSwipeCommit.Next,
            ArtworkSwipeMath.commit(offsetPx = -300f, widthPx = 1000f, velocityPx = 0f),
        )
    }

    @Test
    fun swipeRightPastThresholdCommitsPrevious() {
        assertEquals(
            ArtworkSwipeCommit.Previous,
            ArtworkSwipeMath.commit(offsetPx = 300f, widthPx = 1000f, velocityPx = 0f),
        )
    }

    @Test
    fun smallDragSnapsBack() {
        assertEquals(
            ArtworkSwipeCommit.None,
            ArtworkSwipeMath.commit(offsetPx = -40f, widthPx = 1000f, velocityPx = 0f),
        )
    }
}
