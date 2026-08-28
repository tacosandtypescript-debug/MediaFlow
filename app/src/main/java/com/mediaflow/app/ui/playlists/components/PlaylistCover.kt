package com.mediaflow.app.ui.playlists.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.common.media.MediaArtwork

/**
 * Generative composite cover for playlists. Uses up to 4 existing track artworks
 * arranged in a 2x2 grid, or an elegant gradient system icon if no artworks exist.
 */
@Composable
fun PlaylistCover(
    artworks: List<String?>,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val validArtworks = artworks.filter { !it.isNullOrBlank() }

    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        when {
            validArtworks.size >= 4 -> {
                // 2x2 grid
                val halfSize = size / 2
                Column(modifier = Modifier.fillMaxSize()) {
                    Row {
                        MediaArtwork(artworkUrl = validArtworks[0], size = halfSize, shape = RoundedCornerShape(0.dp))
                        MediaArtwork(artworkUrl = validArtworks[1], size = halfSize, shape = RoundedCornerShape(0.dp))
                    }
                    Row {
                        MediaArtwork(artworkUrl = validArtworks[2], size = halfSize, shape = RoundedCornerShape(0.dp))
                        MediaArtwork(artworkUrl = validArtworks[3], size = halfSize, shape = RoundedCornerShape(0.dp))
                    }
                }
            }
            validArtworks.isNotEmpty() -> {
                // Single prominent artwork
                MediaArtwork(
                    artworkUrl = validArtworks.first(),
                    size = size,
                    shape = RoundedCornerShape(0.dp),
                )
            }
            else -> {
                // Generative themed icon fallback
                Icon(
                    imageVector = Icons.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(size * 0.44f),
                )
            }
        }
    }
}
