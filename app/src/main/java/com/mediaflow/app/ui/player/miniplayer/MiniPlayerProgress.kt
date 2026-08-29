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
import com.mediaflow.app.ui.theme.customColors

@Composable
fun MiniPlayerProgress(
    progressFraction: Float,
    isLive: Boolean,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { if (isLive) 1f else progressFraction.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp),
        color = if (isLive) MaterialTheme.customColors.live else MaterialTheme.colorScheme.primary,
        trackColor = Color.Transparent,
        strokeCap = StrokeCap.Round,
    )
}
