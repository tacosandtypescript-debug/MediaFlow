package com.mediaflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Subtle, purely decorative background gradient (deep blue -> violet ->
 * turquoise in dark, soft sky -> lavender -> mint in light) placed behind the
 * whole app to add visual depth. Used once, not behind every component.
 */
@Composable
fun MediaFlowBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Resolve the effective theme from the active color scheme instead of the
    // system setting, so a manual LIGHT/DARK choice is respected.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gradientColors = if (isDark) {
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors)),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
    }
}
