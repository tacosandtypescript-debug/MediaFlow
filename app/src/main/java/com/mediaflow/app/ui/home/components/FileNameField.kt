package com.mediaflow.app.ui.home.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R

/**
 * Optional file name field. Empty means "automatic name". No file is created.
 */
@Composable
fun FileNameField(
    fileName: String,
    suggestedFileName: String? = null,
    onFileNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = fileName,
        onValueChange = onFileNameChange,
        label = { Text(stringResource(R.string.home_file_name_label)) },
        placeholder = {
            Text(suggestedFileName ?: stringResource(R.string.home_file_name_placeholder))
        },
        supportingText = {
            Text(
                if (suggestedFileName.isNullOrBlank()) {
                    stringResource(R.string.home_file_name_hint)
                } else {
                    stringResource(R.string.home_file_name_suggested, suggestedFileName)
                },
            )
        },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("file_name_input"),
    )
}
