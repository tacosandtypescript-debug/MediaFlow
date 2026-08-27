package com.mediaflow.app.ui.home.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R

/**
 * Main "Descargar ahora" button. Its [enabled] state reflects whether a valid
 * HTTPS URL is present. It never starts a real download in this phase.
 */
@Composable
fun DownloadButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_button"),
    ) {
        Icon(Icons.Outlined.FileDownload, contentDescription = null)
        Text(
            text = stringResource(R.string.home_download_now),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
