package com.mediaflow.app.ui.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.common.media.FavoriteButton

/**
 * Clean metadata section showing Title, Host/Author, Space pill, and Favorite heart button.
 */
@Composable
fun PlayerMetadataSection(
    title: String,
    subtitle: String?,
    isSpace: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    album: String? = null,
    nowPlaying: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (nowPlaying) 24.dp else 24.dp, vertical = if (nowPlaying) 4.dp else 6.dp)
            .testTag("player_metadata_section"),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (nowPlaying) {
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = if (nowPlaying) FontWeight.ExtraBold else FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("player_title"),
            )

            Spacer(Modifier.height(if (nowPlaying) 6.dp else 4.dp))

            if (nowPlaying) {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("player_artist"),
                    )
                }
                if (!album.isNullOrBlank()) {
                    Text(
                        text = album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isSpace) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "X SPACE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("player_artist"),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .testTag("player_favorite"),
            contentAlignment = Alignment.Center,
        ) {
            FavoriteButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
