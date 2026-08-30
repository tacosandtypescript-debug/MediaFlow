package com.mediaflow.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LibraryAudioQueueBuilderTest {

    @Test
    fun playAll_preservesVisibleOrder_startsAtZero_shuffleFalse() {
        val tracks = listOf("A", "B", "C", "D")
        val queue = LibraryAudioQueueBuilder.playAll(tracks)
        assertEquals(listOf("A", "B", "C", "D"), queue.items)
        assertEquals(0, queue.currentIndex)
        assertFalse(queue.shuffle)
    }

    @Test
    fun shuffleAll_sameSet_orderDiffersWhenMultipleUniqueTracks() {
        val tracks = listOf("A", "B", "C", "D")
        val queue = LibraryAudioQueueBuilder.shuffleAll(tracks, Random(7))
        assertEquals(tracks.toSet(), queue.items.toSet())
        assertEquals(0, queue.currentIndex)
        assertTrue(queue.shuffle)
        assertNotEquals(tracks, queue.items)
    }

    @Test
    fun reorder_movesBPastC() {
        val original = listOf("A", "B", "C", "D")
        val queue = LibraryAudioQueueBuilder.reorder(
            items = original,
            fromIndex = 1,
            toIndex = 2,
            currentIndex = 0,
            shuffle = false,
        )
        assertEquals(listOf("A", "C", "B", "D"), queue.items)
        assertEquals(0, queue.currentIndex)
    }
}
