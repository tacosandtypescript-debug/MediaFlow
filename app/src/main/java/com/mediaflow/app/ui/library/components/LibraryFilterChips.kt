package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class LibraryFilter(val label: String) {
    ALL("Todos"),
    AUDIO("Audio"),
    VIDEO("Video"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favoritos"),
}

@Composable
fun LibraryFilterChips(
    selected: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(LibraryFilter.entries, key = { it.name }) { filter ->
            val selectedChip = filter == selected
            FilterChip(
                selected = selectedChip,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedChip) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                shape = CircleShape,
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("library_filter_${filter.name.lowercase()}"),
            )
        }
    }
}
