package com.mediaflow.app.ui.common.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.mediaflow.core.model.MediaType
import java.io.File

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
private val MEDIA_FILE_EXTENSIONS = setOf(
    "mp4", "m4a", "mp3", "webm", "mkv", "mov", "aac", "opus", "ogg", "wav", "m4v", "m3u8", "m3u",
)

/** True only for HTTP(S) images, content image URIs, or local image files. */
fun isLoadableArtworkUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.trim().lowercase()
    val extension = lower
        .substringAfterLast('/', lower)
        .substringAfterLast('.', "")
        .substringBefore('?')
        .substringBefore('#')
    if (extension in MEDIA_FILE_EXTENSIONS) return false
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
        return extension.isEmpty() || extension in IMAGE_EXTENSIONS ||
            !lower.substringAfterLast('/').contains('.')
    }
    if (lower.startsWith("content://")) {
        if (extension in MEDIA_FILE_EXTENSIONS) return false
        if (extension in IMAGE_EXTENSIONS) return true
        return lower.contains("/images") || lower.contains("image")
    }
    if (lower.startsWith("file:") || lower.startsWith("/")) {
        return extension in IMAGE_EXTENSIONS
    }
    return false
}

internal fun coilArtworkModel(url: String): Any {
    if (url.startsWith("file:") || url.startsWith("/")) {
        val path = if (url.startsWith("file:")) Uri.parse(url).path else url
        if (!path.isNullOrBlank()) return File(path)
    }
    return url
}

fun preferredArtworkUrl(thumbnailUri: String?, spaceAvatarUrl: String? = null): String? =
    listOf(thumbnailUri, spaceAvatarUrl).firstOrNull(::isLoadableArtworkUrl)

@Composable
fun MediaArtwork(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    isSpace: Boolean = false,
    mediaType: MediaType = MediaType.AUDIO,
    contentDescription: String? = null,
    fillMax: Boolean = false,
    fullResolution: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fallbackIcon: ImageVector = when {
        isSpace -> Icons.Outlined.GraphicEq
        mediaType == MediaType.VIDEO -> Icons.Outlined.Videocam
        else -> Icons.Outlined.Audiotrack
    }
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
        ),
    )
    val loadable = isLoadableArtworkUrl(artworkUrl)
    val decodePx = remember(size, fillMax, fullResolution, density) {
        if (fullResolution) 0
        else {
            val tile = if (fillMax) 360.dp else size
            with(density) { tile.roundToPx() }.coerceIn(48, 720)
        }
    }
    val request = remember(artworkUrl, loadable, decodePx, fullResolution, context) {
        if (!loadable || artworkUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(coilArtworkModel(artworkUrl))
                .size(if (fullResolution) Size.ORIGINAL else Size(decodePx, decodePx))
                .crossfade(false)
                .allowHardware(true)
                .build()
        }
    }

    Box(
        modifier = modifier
            .then(if (fillMax) Modifier.fillMaxSize() else Modifier.size(size))
            .clip(shape)
            .background(gradientBrush)
            .testTag("media_artwork"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(size * 0.48f),
        )
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
