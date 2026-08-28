package com.mediaflow.app.ui.player.miniplayer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Thin progress bar for local files, styled seamlessly across light and dark themes.
 */
@Composable
fun MiniPlayerProgress(
    progressFraction: Float,
    isLive: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isLive) return

    LinearProgressIndicator(
        progress = { progressFraction.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(2.5.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.Transparent,
        strokeCap = StrokeCap.Round,
    )
}
