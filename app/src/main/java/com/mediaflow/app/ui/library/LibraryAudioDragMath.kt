package com.mediaflow.app.ui.library

import kotlin.math.roundToInt

/**
 * Maps long-press drag distance to a list index and the leftover offset so the
 * dragged row can follow the finger after a reorder jump.
 */
object LibraryAudioDragMath {
    fun targetIndex(fromIndex: Int, dragYPx: Float, rowHeightPx: Float, lastIndex: Int): Int {
        if (lastIndex < 0) return 0
        if (rowHeightPx <= 0f) return fromIndex.coerceIn(0, lastIndex)
        val steps = (dragYPx / rowHeightPx).roundToInt()
        return (fromIndex + steps).coerceIn(0, lastIndex)
    }

    /** Offset remaining after applying an index jump so the row stays under the finger. */
    fun leftoverOffset(dragYPx: Float, fromIndex: Int, toIndex: Int, rowHeightPx: Float): Float {
        if (rowHeightPx <= 0f) return dragYPx
        return dragYPx - (toIndex - fromIndex) * rowHeightPx
    }
}
