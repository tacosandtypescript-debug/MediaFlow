package com.mediaflow.app.ui.player.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Filter chip allowing the user to toggle automatic replay download once the Space finishes.
 */
@Composable
fun AutoDownloadToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = enabled,
        onClick = { onToggle(!enabled) },
        label = {
            Text(
                text = if (enabled) "Descarga programada al terminar" else "Descargar cuando termine",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.DownloadForOffline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier,
    )
}
