package com.mediaflow.app.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.home.QualityOption

/**
 * Quality selector. Shows only the options valid for the current content type.
 * These are visual-only options; nothing asserts source availability.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QualitySelector(
    options: List<QualityOption>,
    selected: QualityOption,
    onSelect: (QualityOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_quality),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val selectedChip = selected == option
                FilterChip(
                    selected = selectedChip,
                    onClick = { onSelect(option) },
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
                        .testTag("quality_${option.name.lowercase()}"),
                )
            }
        }
    }
}
