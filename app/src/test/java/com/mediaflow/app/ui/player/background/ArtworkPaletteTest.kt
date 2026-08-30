package com.mediaflow.app.ui.player.background

import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPaletteTest {
    @Test
    fun playingIsBrighterThanPaused() {
        assertTrue(ambientIntensity(true) > ambientIntensity(false))
    }
}
