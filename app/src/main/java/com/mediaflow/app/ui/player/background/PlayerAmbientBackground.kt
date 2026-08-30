package com.mediaflow.app.ui.player.background

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mediaflow.app.ui.common.media.coilArtworkModel
import com.mediaflow.app.ui.common.media.isLoadableArtworkUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Subtle animated glow behind audio Now Playing. Cover colors when available; purple otherwise.
 */
@Composable
fun PlayerAmbientBackground(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var palette by remember(artworkUrl) { mutableStateOf(ArtworkPalette.Fallback) }

    LaunchedEffect(artworkUrl) {
        if (!isLoadableArtworkUrl(artworkUrl)) {
            palette = ArtworkPalette.Fallback
            return@LaunchedEffect
        }
        val url = artworkUrl!!
        palette = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(coilArtworkModel(url))
                    .allowHardware(false)
                    .size(48)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable.let { drawable ->
                    if (drawable is android.graphics.drawable.BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        null
                    }
                }
                if (bitmap != null && !bitmap.isRecycled) {
                    artworkPaletteFromArgbSamples(samplePixels(bitmap))
                } else {
                    ArtworkPalette.Fallback
                }
            }.getOrDefault(ArtworkPalette.Fallback)
        }
    }

    val targetIntensity = ambientIntensity(isPlaying)
    val intensity by animateFloatAsState(
        targetValue = targetIntensity,
        animationSpec = tween(durationMillis = 700),
        label = "ambientIntensity",
    )

    val infinite = rememberInfiniteTransition(label = "ambientDrift")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
        ),
        label = "ambientPhase",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w * (0.5f + 0.08f * cos(phase.toDouble()).toFloat())
        val cy = h * (0.32f + 0.06f * sin(phase.toDouble()).toFloat())
        val radius = min(w, h) * 0.85f

        drawRect(Color(0xFF0B0E15))
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.primary.copy(alpha = 0.22f * intensity),
                    Color.Transparent,
                    Color.Transparent,
                ),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.secondary.copy(alpha = 0.42f * intensity),
                    palette.accent.copy(alpha = 0.12f * intensity),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = radius,
            ),
            radius = radius,
            center = Offset(cx, cy),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.accent.copy(alpha = 0.28f * intensity),
                    Color.Transparent,
                ),
                center = Offset(w * 0.82f, h * 0.72f + 24f * sin((phase + 1f).toDouble()).toFloat()),
                radius = radius * 0.55f,
            ),
            radius = radius * 0.55f,
            center = Offset(w * 0.82f, h * 0.72f),
        )
    }
}

private fun samplePixels(bitmap: Bitmap): IntArray {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return intArrayOf()
    val stepX = (w / 8).coerceAtLeast(1)
    val stepY = (h / 8).coerceAtLeast(1)
    val samples = IntArray((w / stepX) * (h / stepY))
    var i = 0
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            if (i < samples.size) {
                samples[i] = bitmap.getPixel(x, y)
                i++
            }
            x += stepX
        }
        y += stepY
    }
    return if (i == samples.size) samples else samples.copyOf(i)
}
