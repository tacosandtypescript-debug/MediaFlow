package com.mediaflow.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAudioDragMathTest {
    @Test
    fun targetIndexFollowsFingerPastOneRow() {
        val row = 72f
        assertEquals(1, LibraryAudioDragMath.targetIndex(0, row * 0.6f, row, 3))
        assertEquals(2, LibraryAudioDragMath.targetIndex(1, row, row, 3))
        assertEquals(0, LibraryAudioDragMath.targetIndex(1, -row, row, 3))
        assertEquals(3, LibraryAudioDragMath.targetIndex(0, row * 10f, row, 3))
    }

    @Test
    fun leftoverOffsetKeepsRowUnderFingerAfterJump() {
        val row = 80f
        val drag = 90f
        val leftover = LibraryAudioDragMath.leftoverOffset(drag, fromIndex = 1, toIndex = 2, rowHeightPx = row)
        assertEquals(10f, leftover, 0.01f)
    }
}
