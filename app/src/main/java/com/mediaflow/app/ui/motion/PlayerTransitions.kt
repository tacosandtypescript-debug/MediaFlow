package com.mediaflow.app.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin

/** NavHost enter/exit for the full Now Playing route. */
object PlayerTransitions {

    private val fromMiniBar = TransformOrigin(0.5f, 1f)

    fun enter(): EnterTransition =
        slideInVertically(
            animationSpec = MotionTokens.playerSpring,
            initialOffsetY = { height -> (height * MotionTokens.PLAYER_SLIDE_FRACTION).toInt() },
        ) + scaleIn(
            animationSpec = MotionTokens.playerScaleSpring,
            initialScale = MotionTokens.PLAYER_ENTER_SCALE,
            transformOrigin = fromMiniBar,
        ) + fadeIn(animationSpec = MotionTokens.playerFadeSpec())

    fun exit(): ExitTransition =
        fadeOut(animationSpec = MotionTokens.playerFadeSpec())

    fun popEnter(): EnterTransition =
        fadeIn(animationSpec = MotionTokens.playerFadeSpec())

    fun popExit(): ExitTransition =
        slideOutVertically(
            animationSpec = MotionTokens.playerSpring,
            targetOffsetY = { height -> (height * MotionTokens.PLAYER_SLIDE_FRACTION).toInt() },
        ) + scaleOut(
            animationSpec = MotionTokens.playerScaleSpring,
            targetScale = MotionTokens.PLAYER_EXIT_SCALE,
            transformOrigin = fromMiniBar,
        ) + fadeOut(animationSpec = MotionTokens.playerFadeSpec())

    fun slideOffsetPx(fullHeight: Int): Int =
        (fullHeight * MotionTokens.PLAYER_SLIDE_FRACTION).toInt()
}
