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

/** Chrome motion for Mini Player + bottom bar when Now Playing opens/closes. */
object MiniPlayerTransitions {

    private val fromBar = TransformOrigin(0.5f, 1f)

    fun enter(): EnterTransition =
        slideInVertically(
            animationSpec = MotionTokens.miniSpring,
            initialOffsetY = { height -> (height * MotionTokens.MINI_SLIDE_FRACTION).toInt() },
        ) + scaleIn(
            animationSpec = MotionTokens.miniScaleSpring,
            initialScale = MotionTokens.MINI_EXIT_SCALE,
            transformOrigin = fromBar,
        ) + fadeIn(animationSpec = MotionTokens.miniFadeSpec())

    fun exit(): ExitTransition =
        slideOutVertically(
            animationSpec = MotionTokens.miniSpring,
            targetOffsetY = { height -> (height * MotionTokens.MINI_SLIDE_FRACTION).toInt() },
        ) + scaleOut(
            animationSpec = MotionTokens.miniScaleSpring,
            targetScale = MotionTokens.MINI_EXIT_SCALE,
            transformOrigin = fromBar,
        ) + fadeOut(animationSpec = MotionTokens.miniFadeSpec())

    fun slideOffsetPx(fullHeight: Int): Int =
        (fullHeight * MotionTokens.MINI_SLIDE_FRACTION).toInt()
}
