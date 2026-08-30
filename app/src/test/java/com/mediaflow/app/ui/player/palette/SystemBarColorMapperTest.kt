package com.mediaflow.app.ui.player.palette

import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemBarColorMapperTest {

    @Test
    fun coverDerivedBackgroundYieldsSameStatusAndNavColor() {
        val blue = Color.rgb(20, 40, 200)
        val samples = IntArray(40) { blue }
        val palette = PlayerColorPalette.fromArgbSamples(samples)
        val scheme = SystemBarColorMapper.fromPalette(palette)

        assertEquals(scheme.statusBarColor, scheme.navigationBarColor)
        assertEquals(255, Color.alpha(scheme.statusBarColor))
        assertEquals(palette.background.toArgb(), scheme.statusBarColor)
        assertTrue(scheme.isPlayerWash)
        assertTrue(scheme.edgeToEdge)
        assertFalse(scheme.navigationBarContrastEnforced)
    }

    @Test
    fun darkBackgroundUsesLightIcons() {
        val dark = Color.rgb(8, 10, 24)
        val scheme = SystemBarColorMapper.fromBackgroundArgb(dark)
        assertFalse(scheme.lightStatusBarIcons)
        assertFalse(scheme.lightNavigationBarIcons)
        assertEquals(dark, scheme.statusBarColor)
        assertEquals(dark, scheme.navigationBarColor)
    }

    @Test
    fun lightBackgroundUsesDarkIcons() {
        val light = Color.rgb(240, 236, 228)
        val scheme = SystemBarColorMapper.fromBackgroundArgb(light)
        assertTrue(scheme.lightStatusBarIcons)
        assertTrue(scheme.lightNavigationBarIcons)
        assertEquals(light, scheme.statusBarColor)
    }

    @Test
    fun leavePlayerRestoreReturnsAppNormalNotPlayerWash() {
        val palette = PlayerColorPalette.fromArgbSamples(
            IntArray(16) { Color.rgb(180, 40, 90) },
        )
        val player = SystemBarColorMapper.fromPalette(palette)
        val restored = SystemBarColorMapper.restoreScheme()

        assertTrue(player.isPlayerWash)
        assertFalse(restored.isPlayerWash)
        assertEquals(SystemBarColorMapper.AppNormal, restored)
        assertEquals(Color.TRANSPARENT, restored.statusBarColor)
        assertEquals(Color.TRANSPARENT, restored.navigationBarColor)
        assertNotEquals(player.statusBarColor, restored.statusBarColor)
    }
}
