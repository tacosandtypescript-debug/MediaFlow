package com.mediaflow.app.ui.library

import kotlin.random.Random

data class LibraryAudioQueue<T>(
    val items: List<T>,
    val currentIndex: Int,
    val shuffle: Boolean,
)

/**
 * Pure mapper for Biblioteca audio queue actions (Play All, tap).
 */
object LibraryAudioQueueBuilder {

    fun <T> playAll(visible: List<T>): LibraryAudioQueue<T> {
        if (visible.isEmpty()) {
            return LibraryAudioQueue(emptyList(), currentIndex = 0, shuffle = false)
        }
        return LibraryAudioQueue(visible, currentIndex = 0, shuffle = false)
    }

    fun <T> tapIndex(visible: List<T>, index: Int, shuffle: Boolean = false): LibraryAudioQueue<T> {
        if (visible.isEmpty()) {
            return LibraryAudioQueue(emptyList(), currentIndex = 0, shuffle = shuffle)
        }
        val start = index.coerceIn(0, visible.lastIndex)
        return LibraryAudioQueue(visible, currentIndex = start, shuffle = shuffle)
    }

    fun <T> shuffleAll(visible: List<T>, random: Random = Random.Default): LibraryAudioQueue<T> {
        if (visible.isEmpty()) {
            return LibraryAudioQueue(emptyList(), currentIndex = 0, shuffle = true)
        }
        if (visible.size == 1) {
            return LibraryAudioQueue(visible, currentIndex = 0, shuffle = true)
        }
        val shuffled = visible.toMutableList()
        for (i in shuffled.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = tmp
        }
        if (shuffled.first() == visible.first()) {
            val swapWith = 1 + random.nextInt(shuffled.lastIndex)
            val tmp = shuffled[0]
            shuffled[0] = shuffled[swapWith]
            shuffled[swapWith] = tmp
        }
        return LibraryAudioQueue(shuffled, currentIndex = 0, shuffle = true)
    }
}
