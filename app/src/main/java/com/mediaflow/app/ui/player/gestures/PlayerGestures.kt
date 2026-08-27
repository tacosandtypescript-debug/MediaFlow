package com.mediaflow.app.ui.player.gestures

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Unified pointer gesture detector for the media player screen.
 *
 * Centralizes:
 * - Single tap: toggle controls overlay visibility
 * - Double tap left (< 42% width): rewind -10s
 * - Double tap right (> 58% width): forward +10s
 * - Double tap center: toggle play/pause
 */
fun Modifier.playerGestures(
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onDoubleTapCenter: () -> Unit = onSingleTap,
): Modifier = pointerInput(Unit) {
    detectTapGestures(
        onTap = {
            onSingleTap()
        },
        onDoubleTap = { offset ->
            val width = size.width.toFloat()
            when {
                offset.x < width * 0.42f -> onDoubleTapLeft()
                offset.x > width * 0.58f -> onDoubleTapRight()
                else -> onDoubleTapCenter()
            }
        },
    )
}
