package com.mediaflow.app.ui.player.palette

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mediaflow.app.ui.common.media.coilArtworkModel
import com.mediaflow.app.ui.common.media.isLoadableArtworkUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberPlayerPalette(artworkUrl: String?): PlayerColorPalette {
    val context = LocalContext.current
    var palette by remember(artworkUrl) { mutableStateOf(PlayerColorPalette.Fallback) }
    LaunchedEffect(artworkUrl) {
        if (!isLoadableArtworkUrl(artworkUrl)) {
            palette = PlayerColorPalette.Fallback
            return@LaunchedEffect
        }
        palette = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(coilArtworkModel(artworkUrl!!))
                    .allowHardware(false)
                    .size(48)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable.let { d ->
                    (d as? BitmapDrawable)?.bitmap
                }
                if (bitmap != null && !bitmap.isRecycled) {
                    PlayerColorPalette.fromArgbSamples(sample(bitmap))
                } else PlayerColorPalette.Fallback
            }.getOrDefault(PlayerColorPalette.Fallback)
        }
    }
    return palette
}

private fun sample(bitmap: Bitmap): IntArray {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return intArrayOf()
    val sx = (w / 8).coerceAtLeast(1)
    val sy = (h / 8).coerceAtLeast(1)
    val out = ArrayList<Int>()
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            out.add(bitmap.getPixel(x, y))
            x += sx
        }
        y += sy
    }
    return out.toIntArray()
}
