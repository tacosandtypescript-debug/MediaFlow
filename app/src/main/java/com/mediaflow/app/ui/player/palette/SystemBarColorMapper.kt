package com.mediaflow.app.ui.player.palette

import android.graphics.Color
import android.os.Build
import android.view.Window
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Maps cover-derived player background to status + navigation bar colors
 * so system bars continue the player wash (edge-to-edge).
 */
data class SystemBarColorScheme(
    val statusBarColor: Int,
    val navigationBarColor: Int,
    /** True = dark glyphs on a light bar. */
    val lightStatusBarIcons: Boolean,
    val lightNavigationBarIcons: Boolean,
    val edgeToEdge: Boolean = true,
    val navigationBarContrastEnforced: Boolean = false,
) {
    val isPlayerWash: Boolean
        get() = statusBarColor == navigationBarColor &&
            Color.alpha(statusBarColor) == 255
}

object SystemBarColorMapper {
    /** App chrome outside Player: transparent edge-to-edge, not a cover wash. */
    val AppNormal: SystemBarColorScheme = SystemBarColorScheme(
        statusBarColor = Color.TRANSPARENT,
        navigationBarColor = Color.TRANSPARENT,
        lightStatusBarIcons = false,
        lightNavigationBarIcons = false,
        edgeToEdge = true,
        navigationBarContrastEnforced = true,
    )

    fun fromPalette(palette: PlayerColorPalette): SystemBarColorScheme {
        return fromBackgroundArgb(palette.background.toArgb())
    }

    fun fromBackgroundArgb(backgroundArgb: Int): SystemBarColorScheme {
        val opaque = Color.argb(
            255,
            Color.red(backgroundArgb),
            Color.green(backgroundArgb),
            Color.blue(backgroundArgb),
        )
        val lightIconsOnDarkBar = relativeLuminance(opaque) < 0.35f
        val lightBarIcons = !lightIconsOnDarkBar
        return SystemBarColorScheme(
            statusBarColor = opaque,
            navigationBarColor = opaque,
            lightStatusBarIcons = lightBarIcons,
            lightNavigationBarIcons = lightBarIcons,
            edgeToEdge = true,
            navigationBarContrastEnforced = false,
        )
    }

    fun restoreScheme(): SystemBarColorScheme = AppNormal

    fun apply(window: Window, scheme: SystemBarColorScheme) {
        WindowCompat.setDecorFitsSystemWindows(window, !scheme.edgeToEdge)
        window.statusBarColor = scheme.statusBarColor
        window.navigationBarColor = scheme.navigationBarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = scheme.navigationBarContrastEnforced
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = scheme.lightStatusBarIcons
        controller.isAppearanceLightNavigationBars = scheme.lightNavigationBarIcons
    }

    private fun relativeLuminance(argb: Int): Float {
        val r = Color.red(argb) / 255f
        val g = Color.green(argb) / 255f
        val b = Color.blue(argb) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}
