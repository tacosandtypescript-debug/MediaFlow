package com.mediaflow.app.ui.home.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R

/**
 * URL input with validation feedback, a clear button and a paste-from-clipboard
 * button. It never contacts the link.
 */
@Composable
fun UrlInputField(
    url: String,
    onUrlChange: (String) -> Unit,
    onClear: () -> Unit,
    @StringRes errorMessage: Int?,
    @StringRes infoMessage: Int?,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val hasError = errorMessage != null
    val supporting = errorMessage ?: infoMessage

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(R.string.home_url_label)) },
        placeholder = { Text(stringResource(R.string.home_url_placeholder)) },
        singleLine = true,
        isError = hasError,
        supportingText = {
            if (supporting != null) {
                Text(
                    text = stringResource(supporting),
                    color = if (hasError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        trailingIcon = {
            if (url.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.home_clear),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Uri,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("url_input"),
    )

    Button(
        onClick = {
            clipboard.getText()?.text?.let { pasted ->
                if (pasted.isNotBlank()) onUrlChange(pasted)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("paste_button"),
    ) {
        Icon(Icons.Outlined.ContentPaste, contentDescription = null)
        Text(
            text = stringResource(R.string.home_paste_button),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
