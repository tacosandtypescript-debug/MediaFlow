package com.mediaflow.app.ui.player.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerColorPaletteTest {
    @Test
    fun emptySamplesFallbackPurple() {
        val p = PlayerColorPalette.fromArgbSamples(intArrayOf())
        assertEquals(PlayerColorPalette.Fallback.primary, p.primary)
    }

    @Test
    fun blueDominantSamplesAreNotFallbackPurple() {
        val blue = android.graphics.Color.rgb(20, 40, 200)
        val samples = IntArray(40) { blue }
        val p = PlayerColorPalette.fromArgbSamples(samples)
        assertTrue(p.primary.blue > p.primary.red)
        assertTrue(p.primary.blue > 0.3f)
    }

    @Test
    fun offBackgroundHasNoReactiveFields() {
        val p = PlayerColorPalette.Fallback
        assertTrue(p.background.red + p.background.green + p.background.blue < 1.2f)
    }
}
