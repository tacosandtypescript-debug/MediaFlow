package com.mediaflow.app.ui.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.mediaflow.app.ui.common.media.MediaArtwork
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.XSpace

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioPlayerView(
    title: String,
    @Suppress("UNUSED_PARAMETER") isPlaying: Boolean,
    modifier: Modifier = Modifier,
    space: XSpace? = null,
    subtitle: String? = null,
    artworkUrl: String? = null,
    heroCover: Boolean = false,
    showDetails: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (heroCover) 0.dp else 24.dp, vertical = if (heroCover) 8.dp else 16.dp),
    ) {
        if (heroCover) {
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val cover = min(maxWidth * 0.76f, maxHeight)
                val artShape = RoundedCornerShape(10.dp)
                Surface(
                    shape = artShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 16.dp,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .width(cover)
                        .aspectRatio(1f),
                ) {
                    MediaArtwork(
                        artworkUrl = preferredArtworkUrl(artworkUrl, space?.host?.avatarUrl),
                        size = cover,
                        shape = artShape,
                        isSpace = space != null,
                        mediaType = MediaType.AUDIO,
                        contentDescription = title,
                        fillMax = true,
                        fullResolution = true,
                    )
                }
            }
        } else {
            MediaArtwork(
                artworkUrl = preferredArtworkUrl(artworkUrl, space?.host?.avatarUrl),
                size = 220.dp,
                shape = RoundedCornerShape(12.dp),
                isSpace = space != null,
                mediaType = MediaType.AUDIO,
                contentDescription = title,
                fullResolution = true,
            )
        }

        if (showDetails) {
            Spacer(Modifier.height(if (heroCover) 16.dp else 20.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (space != null) Icons.Outlined.Radio else Icons.Outlined.Headphones,
                        contentDescription = null,
                        modifier = Modifier
                            .size(13.dp)
                            .padding(end = 4.dp),
                    )
                    Text(
                        text = if (space != null) "X SPACE" else "AUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (space != null && space.liveListenersCount > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = "${space.liveListenersCount} oyentes",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .testTag("player_title"),
        )

        if (space != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Host: ${space.host.displayName} (@${space.host.cleanUsername})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val otherSpeakers = space.allSpeakers.filter {
                !it.cleanUsername.equals(space.host.cleanUsername, ignoreCase = true)
            }
            if (otherSpeakers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    otherSpeakers.take(4).forEach { speaker ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = speaker.cleanUsername,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        } else if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        }
    }
}
