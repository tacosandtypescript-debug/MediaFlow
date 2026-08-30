package com.mediaflow.app.ui.library

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
}
