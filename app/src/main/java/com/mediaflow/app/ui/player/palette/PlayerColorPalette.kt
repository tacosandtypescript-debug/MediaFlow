package com.mediaflow.app.ui.player.palette

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mediaflow.app.ui.player.background.ArtworkPalette
import com.mediaflow.app.ui.player.background.artworkPaletteFromArgbSamples

data class PlayerColorPalette(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val foreground: Color,
    val lightIcons: Boolean,
) {
    fun colors(): List<Color> = listOf(primary, secondary, accent, surface, primary.copy(alpha = 0.7f))

    companion object {
        val Fallback = fromArtwork(ArtworkPalette.Fallback)

        fun fromArtwork(art: ArtworkPalette): PlayerColorPalette {
            val bg = darken(art.primary, 0.78f)
            val surface = darken(art.secondary, 0.55f)
            val lightIcons = bg.luminance() < 0.35f
            return PlayerColorPalette(
                primary = art.primary,
                secondary = art.secondary,
                accent = art.accent,
                background = bg,
                surface = surface,
                foreground = if (lightIcons) Color(0xFFF5F5F5) else Color(0xFF121212),
                lightIcons = lightIcons,
            )
        }

        fun fromArgbSamples(samples: IntArray): PlayerColorPalette {
            if (samples.isEmpty()) return Fallback
            return fromArtwork(artworkPaletteFromArgbSamples(samples))
        }

        private fun darken(c: Color, amount: Float): Color = Color(
            red = c.red * (1f - amount),
            green = c.green * (1f - amount),
            blue = c.blue * (1f - amount),
            alpha = 1f,
        )
    }
}
