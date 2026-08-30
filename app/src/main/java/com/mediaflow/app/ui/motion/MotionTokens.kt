package com.mediaflow.app.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Shared motion timings and springs for Mini Player ↔ Now Playing.
 * Fast, slightly underdamped — Spotify-like, not a fade.
 */
object MotionTokens {
    const val PLAYER_DURATION_MS = 380
    const val PLAYER_FADE_MS = 160
    const val MINI_DURATION_MS = 280

    /** Full player grows from this scale while sliding up. */
    const val PLAYER_ENTER_SCALE = 0.92f

    /** Full player shrinks toward this scale while sliding down. */
    const val PLAYER_EXIT_SCALE = 0.92f

    /** Mini player shrinks slightly as Now Playing covers it. */
    const val MINI_EXIT_SCALE = 0.96f

    const val PLAYER_SLIDE_FRACTION = 0.22f
    const val MINI_SLIDE_FRACTION = 0.35f

    val playerSpring = spring<IntOffset>(
        dampingRatio = 0.86f,
        stiffness = 420f,
    )

    val playerScaleSpring = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 420f,
    )

    val miniSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 500f,
    )

    val miniScaleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 500f,
    )

    fun playerFadeSpec() = tween<Float>(durationMillis = PLAYER_FADE_MS)

    fun miniFadeSpec() = tween<Float>(durationMillis = MINI_DURATION_MS / 2)
}
