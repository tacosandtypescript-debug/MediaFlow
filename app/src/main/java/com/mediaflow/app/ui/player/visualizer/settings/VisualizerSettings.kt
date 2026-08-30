package com.mediaflow.app.ui.player.visualizer.settings

data class VisualizerSettings(
    val enabled: Boolean = false,
    val style: VisualizerStyle = VisualizerStyle.BALLS,
    val intensity: Float = 0.55f,
    val motion: Float = 0.55f,
    val useCoverColors: Boolean = true,
    val dynamicSystemBars: Boolean = true,
    val reduceOnPause: Boolean = true,
    val batterySaver: Boolean = false,
) {
    fun particleCount(): Int = when {
        !enabled -> 0
        batterySaver -> 8
        else -> (18 * motion).toInt().coerceIn(8, 26)
    }
}
