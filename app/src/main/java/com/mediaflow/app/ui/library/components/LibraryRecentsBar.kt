package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
fun LibraryRecentsBar(
    isGrid: Boolean,
    onToggleGrid: () -> Unit,
    showGridToggle: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.SwapVert,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.library_recents),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        if (showGridToggle) {
            IconButton(
                onClick = onToggleGrid,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("library_view_toggle"),
            ) {
                Icon(
                    imageVector = if (isGrid) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                    contentDescription = stringResource(
                        if (isGrid) R.string.library_view_list else R.string.library_view_grid,
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
