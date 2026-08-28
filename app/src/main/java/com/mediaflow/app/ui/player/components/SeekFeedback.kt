package com.mediaflow.app.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaflow.app.ui.theme.customColors
import com.mediaflow.app.ui.player.SeekFeedbackEvent

/**
 * Animated floating feedback pill (±10s) triggered on double tap or seek button click.
 */
@Composable
fun SeekFeedback(
    event: SeekFeedbackEvent?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // Rewind Feedback (-10s) on Left Side
        AnimatedVisibility(
            visible = event is SeekFeedbackEvent.Rewind,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.82f, animationSpec = tween(120)),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 1.08f, animationSpec = tween(250)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 40.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.customColors.dialogBackground.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FastRewind,
                        contentDescription = "-10 segundos",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "-10s",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Forward Feedback (+10s) on Right Side
        AnimatedVisibility(
            visible = event is SeekFeedbackEvent.Forward,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.82f, animationSpec = tween(120)),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 1.08f, animationSpec = tween(250)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 40.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.customColors.dialogBackground.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FastForward,
                        contentDescription = "+10 segundos",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "+10s",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
