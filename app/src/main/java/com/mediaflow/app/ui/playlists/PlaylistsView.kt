package com.mediaflow.app.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.library.components.EmptyLibraryState
import com.mediaflow.app.ui.playlists.components.CreatePlaylistDialog
import com.mediaflow.app.ui.playlists.components.PlaylistCard
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.Playlist

/**
 * Tab displaying all user-created playlists with creation, rename, play and delete actions.
 */
@Composable
fun PlaylistsView(
    playlists: List<Playlist>,
    allMediaItems: List<DownloadItem>,
    onOpenPlaylist: (playlist: Playlist) -> Unit,
    onPlayPlaylist: (playlist: Playlist) -> Unit,
    onCreatePlaylist: (name: String) -> Unit,
    onRenamePlaylist: (id: String, newName: String) -> Unit,
    onDeletePlaylist: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Sub-header with action button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.playlists_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            FilledTonalButton(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.playlists_new), style = MaterialTheme.typography.labelLarge)
            }
        }

        if (playlists.isEmpty()) {
            EmptyLibraryState(
                icon = Icons.Outlined.PlaylistPlay,
                title = stringResource(R.string.playlists_empty_title),
                subtitle = stringResource(R.string.playlists_empty_subtitle),
                actionLabel = stringResource(R.string.playlists_create),
                onAction = { showCreateDialog = true },
                modifier = Modifier.weight(1f),
            )
        } else {
            val mediaMap = remember(allMediaItems) {
                allMediaItems.associateBy { it.localUri ?: it.id }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("playlists_list"),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    val artworks = playlist.mediaUris.take(4).map { uri ->
                        preferredArtworkUrl(mediaMap[uri]?.thumbnailUri)
                    }

                    PlaylistCard(
                        playlist = playlist,
                        artworks = artworks,
                        onClick = { onOpenPlaylist(playlist) },
                        onPlay = { onPlayPlaylist(playlist) },
                        onRename = { playlistToRename = playlist },
                        onDelete = { onDeletePlaylist(playlist.id) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            title = "Nueva playlist",
            confirmLabel = "Crear",
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    playlistToRename?.let { playlist ->
        CreatePlaylistDialog(
            initialName = playlist.name,
            title = "Renombrar playlist",
            confirmLabel = "Guardar",
            onConfirm = { newName ->
                onRenamePlaylist(playlist.id, newName)
                playlistToRename = null
            },
            onDismiss = { playlistToRename = null },
        )
    }
}
