package com.mediaflow.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FiberNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.PlaybackStatus

/**
 * Discreet visual badge displaying playback status:
 * - NEW: "Nuevo"
 * - IN_PROGRESS: "En progreso · 64 %"
 * - COMPLETED: "Visto" (video) or "Escuchado" (audio)
 */
@Composable
fun PlaybackBadge(
    progress: PlaybackProgress?,
    mediaType: MediaType = MediaType.VIDEO,
    modifier: Modifier = Modifier,
) {
    val status = progress?.status ?: PlaybackStatus.NEW
    val percentage = progress?.percentageInt ?: 0

    val (label, containerColor, contentColor, icon) = when (status) {
        PlaybackStatus.NEW -> Quadruple(
            "Nuevo",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Outlined.FiberNew,
        )
        PlaybackStatus.IN_PROGRESS -> Quadruple(
            "En progreso · $percentage %",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Outlined.PlayArrow,
        )
        PlaybackStatus.COMPLETED -> Quadruple(
            if (mediaType == MediaType.AUDIO) "Escuchado" else "Visto",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Outlined.Check,
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor.copy(alpha = 0.9f),
        contentColor = contentColor,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(13.dp)
                    .padding(end = 4.dp),
                tint = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

/**
 * Mini progress bar for in-progress media items.
 */
@Composable
fun PlaybackProgressBar(
    progress: PlaybackProgress?,
    modifier: Modifier = Modifier,
) {
    if (progress != null && progress.status == PlaybackStatus.IN_PROGRESS && progress.playbackPercentage > 0f) {
        LinearProgressIndicator(
            progress = { progress.playbackPercentage },
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
