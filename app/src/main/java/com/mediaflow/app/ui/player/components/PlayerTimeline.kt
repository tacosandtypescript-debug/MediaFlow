package com.mediaflow.app.ui.player.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.theme.customColors
import java.util.Locale

/**
 * Custom modern Timeline Seek Bar with dynamic track thickness, animated thumb scaling,
 * distinct buffered progress, and decoupled scrubbing feedback.
 */
@Composable
fun PlayerTimeline(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bufferedMs: Long = 0L,
    onScrubbingChanged: (Boolean) -> Unit = {},
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgressFraction by remember { mutableFloatStateOf(0f) }

    val effectiveDuration = durationMs.coerceAtLeast(0L)
    val actualProgressFraction = if (effectiveDuration > 0L) {
        (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val bufferedFraction = if (effectiveDuration > 0L) {
        (bufferedMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val displayFraction = if (isDragging) dragProgressFraction else actualProgressFraction
    val displayPositionMs = (displayFraction * effectiveDuration).toLong()

    // Smooth animations for track height and thumb radius
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 3.5.dp,
        animationSpec = tween(durationMillis = 150),
        label = "trackHeight",
    )

    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 5.dp,
        animationSpec = tween(durationMillis = 150),
        label = "thumbRadius",
    )

    val primaryColor = MaterialTheme.customColors.progressPlayed
    val tertiaryColor = MaterialTheme.customColors.progressThumb
    val inactiveTrackColor = MaterialTheme.customColors.progressTrack
    val bufferedTrackColor = MaterialTheme.customColors.progressTrack.copy(alpha = 0.7f)

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Floating Time Tooltip during Scrubbing
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.customColors.dialogBackground,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        text = formatPlayerTime(displayPositionMs),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Custom Canvas Seek Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(effectiveDuration) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (effectiveDuration > 0L) {
                                isDragging = true
                                onScrubbingChanged(true)
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                dragProgressFraction = fraction
                                try {
                                    awaitRelease()
                                } finally {
                                    val targetMs = (dragProgressFraction * effectiveDuration).toLong()
                                    onSeekTo(targetMs)
                                    isDragging = false
                                    onScrubbingChanged(false)
                                }
                            }
                        },
                    )
                }
                .pointerInput(effectiveDuration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (effectiveDuration > 0L) {
                                isDragging = true
                                onScrubbingChanged(true)
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                dragProgressFraction = fraction
                            }
                        },
                        onDragEnd = {
                            if (effectiveDuration > 0L) {
                                val targetMs = (dragProgressFraction * effectiveDuration).toLong()
                                onSeekTo(targetMs)
                            }
                            isDragging = false
                            onScrubbingChanged(false)
                        },
                        onDragCancel = {
                            isDragging = false
                            onScrubbingChanged(false)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (effectiveDuration > 0L) {
                                val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                dragProgressFraction = fraction
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight + (thumbRadius * 2)),
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val trackHeightPx = trackHeight.toPx()
                val thumbRadiusPx = thumbRadius.toPx()
                val centerY = canvasHeight / 2f
                val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

                // 1. Inactive Background Track
                drawRoundRect(
                    color = inactiveTrackColor,
                    topLeft = Offset(0f, centerY - (trackHeightPx / 2f)),
                    size = Size(canvasWidth, trackHeightPx),
                    cornerRadius = cornerRadius,
                )

                // 2. Buffered Track
                if (bufferedFraction > 0f) {
                    val bufferedWidth = canvasWidth * bufferedFraction
                    drawRoundRect(
                        color = bufferedTrackColor,
                        topLeft = Offset(0f, centerY - (trackHeightPx / 2f)),
                        size = Size(bufferedWidth, trackHeightPx),
                        cornerRadius = cornerRadius,
                    )
                }

                // 3. Played Progress Track (Gradient)
                val playedWidth = canvasWidth * displayFraction
                if (playedWidth > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(primaryColor, tertiaryColor),
                            startX = 0f,
                            endX = canvasWidth.coerceAtLeast(1f),
                        ),
                        topLeft = Offset(0f, centerY - (trackHeightPx / 2f)),
                        size = Size(playedWidth, trackHeightPx),
                        cornerRadius = cornerRadius,
                    )
                }

                // 4. Thumb Indicator
                val thumbCenterX = (canvasWidth * displayFraction).coerceIn(thumbRadiusPx, canvasWidth - thumbRadiusPx)
                
                // Outer glow circle if dragging
                if (isDragging) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.25f),
                        radius = thumbRadiusPx + 4.dp.toPx(),
                        center = Offset(thumbCenterX, centerY),
                    )
                }

                // Main Thumb Circle
                drawCircle(
                    color = tertiaryColor,
                    radius = thumbRadiusPx,
                    center = Offset(thumbCenterX, centerY),
                )
            }
        }

        // Time Labels Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatPlayerTime(displayPositionMs),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatPlayerTime(effectiveDuration),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Formats milliseconds into clean [hh:mm:ss] or [mm:ss] format.
 */
fun formatPlayerTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0) {
        String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
