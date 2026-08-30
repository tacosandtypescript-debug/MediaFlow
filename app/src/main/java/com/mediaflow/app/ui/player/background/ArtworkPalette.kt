package com.mediaflow.app.ui.player.background

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min

/** Cover-derived colors for the Now Playing wash. Purple if there is no art. */
data class ArtworkPalette(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
) {
    companion object {
        val Fallback = ArtworkPalette(
            primary = Color(0xFF7C3AED),
            secondary = Color(0xFF8B5CF6),
            accent = Color(0xFF5B21B6),
        )
    }
}

/** Playing is a bit brighter; paused eases down. Range 0.45–0.78. */
fun ambientIntensity(isPlaying: Boolean): Float = if (isPlaying) 0.78f else 0.45f

fun artworkPaletteFromArgbSamples(samples: IntArray): ArtworkPalette {
    if (samples.isEmpty()) return ArtworkPalette.Fallback
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0
    var maxSat = -1f
    var satColor = 0
    for (argb in samples) {
        val a = (argb ushr 24) and 0xFF
        if (a < 32) continue
        val cr = (argb ushr 16) and 0xFF
        val cg = (argb ushr 8) and 0xFF
        val cb = argb and 0xFF
        val lum = (cr + cg + cb) / 3
        if (lum < 18 || lum > 245) continue
        r += cr
        g += cg
        b += cb
        count++
        val sat = saturation(cr, cg, cb)
        if (sat > maxSat) {
            maxSat = sat
            satColor = argb
        }
    }
    if (count == 0) return ArtworkPalette.Fallback
    val avg = Color(
        red = (r / count).toInt(),
        green = (g / count).toInt(),
        blue = (b / count).toInt(),
    )
    val accent = if (maxSat > 0.08f) Color(satColor) else ArtworkPalette.Fallback.secondary
    return ArtworkPalette(
        primary = avg,
        secondary = mix(avg, accent, 0.45f),
        accent = accent,
    )
}

private fun saturation(r: Int, g: Int, b: Int): Float {
    val maxC = max(r, max(g, b)).toFloat()
    val minC = min(r, min(g, b)).toFloat()
    if (maxC <= 0f) return 0f
    return (maxC - minC) / maxC
}

private fun mix(a: Color, b: Color, t: Float): Color {
    val aa = a.toArgb()
    val bb = b.toArgb()
    fun ch(shift: Int): Int {
        val ca = (aa ushr shift) and 0xFF
        val cb = (bb ushr shift) and 0xFF
        return (ca + ((cb - ca) * t).toInt()).coerceIn(0, 255)
    }
    return Color(ch(16), ch(8), ch(0))
}
