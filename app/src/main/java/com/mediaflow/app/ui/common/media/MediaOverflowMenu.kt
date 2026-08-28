package com.mediaflow.app.ui.common.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Reusable 3-dots overflow contextual menu for audio and video media items.
 */
@Composable
fun MediaOverflowMenu(
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { expanded = true },
        modifier = modifier.testTag("media_overflow_btn"),
    ) {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "Opciones",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Reproducir") },
                leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                onClick = {
                    expanded = false
                    onPlay()
                },
            )

            DropdownMenuItem(
                text = { Text("Añadir a playlist") },
                leadingIcon = { Icon(Icons.Outlined.PlaylistAdd, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAddToPlaylist()
                },
            )

            DropdownMenuItem(
                text = { Text(if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos") },
                leadingIcon = {
                    Icon(
                        if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onToggleFavorite()
                },
            )

            if (onAddToQueue != null) {
                DropdownMenuItem(
                    text = { Text("Añadir a la cola") },
                    leadingIcon = { Icon(Icons.Outlined.QueueMusic, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAddToQueue()
                    },
                )
            }

            DropdownMenuItem(
                text = {
                    Text(
                        "Eliminar",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
