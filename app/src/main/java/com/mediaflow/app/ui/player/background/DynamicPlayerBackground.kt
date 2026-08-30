package com.mediaflow.app.ui.player.background

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mediaflow.app.ui.player.palette.PlayerColorPalette

/** Non-reactive cover wash (Visualizer OFF). */
@Composable
fun DynamicPlayerBackground(
    palette: PlayerColorPalette,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(palette.background, tween(700), label = "dyn_bg")
    val primary by animateColorAsState(palette.primary, tween(700), label = "dyn_p")
    val accent by animateColorAsState(palette.accent, tween(700), label = "dyn_a")
    Canvas(modifier.fillMaxSize()) {
        drawRect(bg)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(primary.copy(alpha = 0.38f), Color.Transparent, Color.Transparent),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.28f),
                radius = size.minDimension * 0.7f,
            ),
            radius = size.minDimension * 0.7f,
            center = Offset(size.width * 0.5f, size.height * 0.28f),
        )
    }
}
