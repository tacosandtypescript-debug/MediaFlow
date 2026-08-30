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
import com.mediaflow.data.download.formats.RealFormatCatalog

/** One chip per extractor format so 1080p H.264 30 and 1080p VP9 60 stay distinct. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyzedFormatSelector(
    choices: List<RealFormatCatalog.ListedFormat>,
    selectedFormatId: String?,
    autoBest: Boolean,
    onSelectAuto: () -> Unit,
    onSelectFormat: (String) -> Unit,
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
            FilterChip(
                selected = autoBest,
                onClick = onSelectAuto,
                label = {
                    Text(
                        text = stringResource(R.string.quality_auto),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (autoBest) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                shape = CircleShape,
                border = null,
                colors = homeFilterChipColors(),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("quality_auto"),
            )
            choices.forEach { choice ->
                val selected = !autoBest && choice.format.formatId == selectedFormatId
                FilterChip(
                    selected = selected,
                    onClick = { onSelectFormat(choice.format.formatId) },
                    label = {
                        Text(
                            text = choice.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    shape = CircleShape,
                    border = null,
                    colors = homeFilterChipColors(),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("format_choice_${choice.format.formatId}"),
                )
            }
        }
    }
}
