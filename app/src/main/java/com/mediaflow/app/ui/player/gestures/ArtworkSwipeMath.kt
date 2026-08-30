package com.mediaflow.app.ui.player.gestures

enum class ArtworkSwipeCommit {
    None,
    Next,
    Previous,
}

object ArtworkSwipeMath {
    const val DistanceFraction = 0.28f
    const val VelocityPx = 850f

    fun commit(offsetPx: Float, widthPx: Float, velocityPx: Float): ArtworkSwipeCommit {
        if (widthPx <= 0f) return ArtworkSwipeCommit.None
        val distance = widthPx * DistanceFraction
        val goNext = offsetPx < -distance || velocityPx < -VelocityPx
        val goPrev = offsetPx > distance || velocityPx > VelocityPx
        return when {
            goNext && !goPrev -> ArtworkSwipeCommit.Next
            goPrev && !goNext -> ArtworkSwipeCommit.Previous
            else -> ArtworkSwipeCommit.None
        }
    }
}
