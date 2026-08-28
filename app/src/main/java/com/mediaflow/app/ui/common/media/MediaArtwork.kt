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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mediaflow.core.model.MediaType

/**
 * Robust, high-aesthetic media artwork loader with automatic fallbacks for Spaces,
 * downloaded audios, podcasts, and videos.
 */
@Composable
fun MediaArtwork(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    isSpace: Boolean = false,
    mediaType: MediaType = MediaType.AUDIO,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val fallbackIcon: ImageVector = when {
        isSpace -> Icons.Outlined.GraphicEq
        mediaType == MediaType.VIDEO -> Icons.Outlined.Videocam
        else -> Icons.Outlined.Audiotrack
    }

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(gradientBrush),
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.size(size * 0.48f),
            )
        }
    }
}
