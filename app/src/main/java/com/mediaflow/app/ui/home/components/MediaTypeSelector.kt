package com.mediaflow.app.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.mediaflow.app.ui.home.ContentType

/**
 * Vídeo / Audio selector with clearly visible selection state.
 */
@Composable
fun MediaTypeSelector(
    selected: ContentType,
    onSelect: (ContentType) -> Unit,
    modifier: Modifier = Modifier,
    videoEnabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_media_type),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContentType.entries.forEach { option ->
                val enabled = option != ContentType.VIDEO || videoEnabled
                val selectedChip = selected == option
                FilterChip(
                    selected = selectedChip,
                    onClick = { if (enabled) onSelect(option) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedChip) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    shape = CircleShape,
                    border = null,
                    colors = homeFilterChipColors(),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("media_type_${option.name.lowercase()}"),
                )
            }
        }
        Text(
            text = stringResource(selected.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun homeFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
    selectedLabelColor = MaterialTheme.colorScheme.surface,
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    disabledSelectedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
)
