package com.mediaflow.app.ui.home.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val container = MaterialTheme.colorScheme.surfaceVariant
    TextField(
        value = fileName,
        onValueChange = onFileNameChange,
        label = { Text(stringResource(R.string.home_file_name_label)) },
        placeholder = {
            Text(suggestedFileName ?: stringResource(R.string.home_file_name_placeholder))
        },
        supportingText = {
            Text(
                text = if (suggestedFileName.isNullOrBlank()) {
                    stringResource(R.string.home_file_name_hint)
                } else {
                    stringResource(R.string.home_file_name_suggested, suggestedFileName)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("file_name_input"),
    )
}
