package com.mediaflow.app.ui.common.media

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.theme.customColors

/**
 * Modern, clean media row representing an audio track or X Space in the library,
 * playlists, favorites, and search results.
 */
@Composable
fun AudioMediaRow(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    durationText: String?,
    isSpace: Boolean = false,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    progressFraction: Float = 0f,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowBackground = if (isPlaying) {
        MaterialTheme.customColors.libraryRowPlaying
    } else {
        Color.Transparent
    }
    val rowBorder = if (isPlaying) {
        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.customColors.libraryRowPlayingBorder)
    } else {
        null
    }

    Surface(
        color = rowBackground,
        border = rowBorder,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clickable(onClick = onClick)
            .testTag("audio_media_row"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            // Artwork with playing badge overlay
            Box(contentAlignment = Alignment.Center) {
                MediaArtwork(
                    artworkUrl = artworkUrl,
                    size = 48.dp,
                    isSpace = isSpace,
                    shape = RoundedCornerShape(12.dp),
                )

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedSoundWaves(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            // Metadata Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isSpace) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = "SPACE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }

                    Text(
                        text = subtitle ?: "Audio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (!durationText.isNullOrBlank()) {
                        Text(
                            text = "· $durationText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }

                // In-progress indicator
                if (progressFraction in 0.05f..0.95f && !isPlaying) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            // Favorite Button
            FavoriteButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite,
                modifier = Modifier.size(48.dp),
            )

            MediaOverflowMenu(
                isFavorite = isFavorite,
                onPlay = onClick,
                onAddToPlaylist = onAddToPlaylist,
                onToggleFavorite = onToggleFavorite,
                onAddToQueue = onAddToQueue,
                onDelete = onDelete,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/**
 * Subtle soundwaves animation for actively playing items.
 */
@Composable
fun AnimatedSoundWaves(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "soundwaves")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wave1",
    )

    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wave2",
    )

    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wave3",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp * h1)
                .clip(CircleShape)
                .background(color),
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp * h2)
                .clip(CircleShape)
                .background(color),
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp * h3)
                .clip(CircleShape)
                .background(color),
        )
    }
}
