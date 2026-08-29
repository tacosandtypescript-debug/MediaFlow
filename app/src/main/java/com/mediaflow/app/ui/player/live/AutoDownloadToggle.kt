package com.mediaflow.app.ui.player.live

import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R

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
                text = stringResource(
                    if (enabled) R.string.space_auto_download_on else R.string.space_auto_download_off,
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.DownloadForOffline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("auto_download_toggle"),
    )
}
