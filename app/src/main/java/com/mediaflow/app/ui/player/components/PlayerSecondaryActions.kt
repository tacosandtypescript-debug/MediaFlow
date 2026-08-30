package com.mediaflow.app.ui.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R

/**
 * Secondary actions row positioned below the playback controls (Queue, Add to playlist, Speed, Delete).
 */
@Composable
fun PlayerSecondaryActions(
    speed: Float,
    queueCount: Int,
    isLive: Boolean,
    onSpeedChange: (Float) -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .testTag("player_secondary_actions"),
    ) {
        // Speed Button
        if (!isLive) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.testTag("speed_btn"),
            ) {
                IconButton(
                    onClick = {
                        val nextSpeed = when (speed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            else -> 1.0f
                        }
                        onSpeedChange(nextSpeed)
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(
                        text = "${speed}x",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // Add to Playlist
        IconButton(
            onClick = onAddToPlaylist,
            modifier = Modifier.testTag("player_add_to_playlist_btn"),
        ) {
            Icon(
                imageVector = Icons.Outlined.PlaylistAdd,
                contentDescription = "Añadir a playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        if (onShare != null) {
            IconButton(
                onClick = onShare,
                modifier = Modifier.testTag("player_share_btn"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Open Queue
        IconButton(
            onClick = onOpenQueue,
            modifier = Modifier.testTag("player_open_queue_btn"),
        ) {
            BadgedBox(
                badge = {
                    if (queueCount > 1) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("$queueCount")
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.QueueMusic,
                    contentDescription = "Ver cola",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Delete Media File
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("player_delete_btn"),
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Eliminar",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
