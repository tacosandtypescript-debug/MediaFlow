package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R

@Composable
fun LibraryHeader(
    searchOpen: Boolean,
    searchQuery: String,
    onSearchOpenChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (searchOpen) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
            trailingIcon = {
                IconButton(
                    onClick = {
                        onSearchQueryChange("")
                        onSearchOpenChange(false)
                    },
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.library_search_close))
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("library_search_field"),
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onSearchOpenChange(true) },
            modifier = Modifier
                .size(48.dp)
                .testTag("library_search_btn"),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = stringResource(R.string.library_search),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(
            onClick = onCreatePlaylist,
            modifier = Modifier
                .size(48.dp)
                .testTag("library_add_playlist_btn"),
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = stringResource(R.string.playlists_create),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
