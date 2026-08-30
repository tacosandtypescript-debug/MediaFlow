package com.mediaflow.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryAudioQueueBuilderTest {

    @Test
    fun playAll_preservesVisibleOrder_startsAtZero_shuffleFalse() {
        val tracks = listOf("A", "B", "C", "D")
        val queue = LibraryAudioQueueBuilder.playAll(tracks)
        assertEquals(listOf("A", "B", "C", "D"), queue.items)
        assertEquals(0, queue.currentIndex)
        assertFalse(queue.shuffle)
    }
}
