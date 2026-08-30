package com.mediaflow.app.ui.motion

import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTokensTest {
    @Test
    fun playerEnterIsNotAPlainInstantFade() {
        assertTrue(MotionTokens.PLAYER_DURATION_MS > MotionTokens.PLAYER_FADE_MS)
        assertTrue(MotionTokens.PLAYER_ENTER_SCALE < 1f)
        assertTrue(PlayerTransitions.slideOffsetPx(1000) > 0)
        assertTrue(MiniPlayerTransitions.slideOffsetPx(1000) > 0)
    }
}
