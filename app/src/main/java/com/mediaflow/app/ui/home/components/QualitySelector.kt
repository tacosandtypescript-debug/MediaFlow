package com.mediaflow.app.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
    Text(
        text = stringResource(R.string.home_quality),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.labelRes)) },
                modifier = Modifier.testTag("quality_${option.name.lowercase()}"),
            )
        }
    }
}
