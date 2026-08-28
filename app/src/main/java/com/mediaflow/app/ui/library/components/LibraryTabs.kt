package com.mediaflow.app.ui.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.theme.customColors

/**
 * Sub-category tabs within the Audio library.
 */
enum class AudioLibraryTab(val label: String) {
    ALL("Todos"),
    FAVORITES("Favoritos"),
    PLAYLISTS("Playlists"),
}

@Composable
fun LibraryTabs(
    selectedTab: AudioLibraryTab,
    onSelectTab: (AudioLibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        AudioLibraryTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab

            FilterChip(
                selected = isSelected,
                onClick = { onSelectTab(tab) },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.customColors.chipSelectedBackground,
                    selectedLabelColor = MaterialTheme.customColors.chipSelectedText,
                    containerColor = MaterialTheme.customColors.chipBackground,
                    labelColor = MaterialTheme.customColors.chipText,
                ),
                border = null,
                modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
            )
        }
    }
}
