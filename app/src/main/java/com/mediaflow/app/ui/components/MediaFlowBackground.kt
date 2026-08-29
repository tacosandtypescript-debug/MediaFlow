package com.mediaflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance

/**
 * Subtle ink/paper wash behind the app. Not a decorative purple gradient.
 */
@Composable
fun MediaFlowBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gradientColors = if (isDark) {
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
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
