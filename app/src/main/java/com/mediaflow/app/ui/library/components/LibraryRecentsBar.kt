package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ViewList
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.library.LibrarySort

@Composable
fun LibraryRecentsBar(
    isGrid: Boolean,
    onToggleGrid: () -> Unit,
    showGridToggle: Boolean,
    selectedSort: LibrarySort = LibrarySort.NEWEST,
    onSelectSort: (LibrarySort) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { sortMenuOpen = true }
                    .testTag("library_sort_menu"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SwapVert,
                    contentDescription = stringResource(R.string.library_sort),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(labelFor(selectedSort)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                LibrarySort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(labelFor(option))) },
                        onClick = {
                            onSelectSort(option)
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }
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

private fun labelFor(sort: LibrarySort): Int = when (sort) {
    LibrarySort.NEWEST -> R.string.library_sort_newest
    LibrarySort.OLDEST -> R.string.library_sort_oldest
    LibrarySort.HEAVIEST -> R.string.library_sort_heaviest
    LibrarySort.LIGHTEST -> R.string.library_sort_lightest
    LibrarySort.LONGEST -> R.string.library_sort_longest
    LibrarySort.SHORTEST -> R.string.library_sort_shortest
    LibrarySort.NAME_AZ -> R.string.library_sort_name_az
    LibrarySort.NAME_ZA -> R.string.library_sort_name_za
}
