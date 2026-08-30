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
}
