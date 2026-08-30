package com.mediaflow.app.ui.library

import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryProgressForListTest {

    @Test
    fun bucketsClosePositionsTogether() {
        val a = progress(mediaId = "a", position = 1_000L, total = 100_000L, playedAt = 9L)
        val b = progress(mediaId = "a", position = 2_000L, total = 100_000L, playedAt = 99L)
        val first = libraryProgressForList(mapOf("a" to a))
        val second = libraryProgressForList(mapOf("a" to b))
        assertEquals(first, second)
        assertEquals(0L, first.getValue("a").lastPlayedAt)
    }

    @Test
    fun keepsDistinctBuckets() {
        val early = progress(mediaId = "a", position = 1_000L, total = 100_000L)
        val later = progress(mediaId = "a", position = 40_000L, total = 100_000L)
        assertNotEquals(
            libraryProgressForList(mapOf("a" to early)),
            libraryProgressForList(mapOf("a" to later)),
        )
    }

    private fun progress(
        mediaId: String,
        position: Long,
        total: Long,
        playedAt: Long = 1L,
    ) = PlaybackProgress(
        mediaId = mediaId,
        filePath = mediaId,
        totalDurationMs = total,
        currentPositionMs = position,
        lastPlayedAt = playedAt,
        status = PlaybackStatus.IN_PROGRESS,
    )
}
