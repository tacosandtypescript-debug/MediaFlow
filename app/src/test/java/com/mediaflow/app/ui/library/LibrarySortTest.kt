package com.mediaflow.app.ui.library

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {

    private fun item(
        id: String,
        title: String? = id,
        createdAt: Long = 0L,
        totalBytes: Long? = null,
        downloadedBytes: Long = 0L,
        durationSeconds: Long? = null,
        fileName: String? = null,
    ) = DownloadItem(
        id = id,
        sourceUrl = "https://example.com/$id",
        title = title,
        fileName = fileName,
        mediaType = MediaType.VIDEO,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        durationSeconds = durationSeconds,
        createdAt = createdAt,
    )

    private val items = listOf(
        item("b", title = "Beta", createdAt = 20, totalBytes = 100, durationSeconds = 30),
        item("a", title = "Alpha", createdAt = 10, totalBytes = 300, durationSeconds = 90),
        item("c", title = "Gamma", createdAt = 30, totalBytes = null, durationSeconds = null),
    )

    @Test
    fun newestThenOldest() {
        assertEquals(listOf("c", "b", "a"), ids(LibrarySort.NEWEST))
        assertEquals(listOf("a", "b", "c"), ids(LibrarySort.OLDEST))
    }

    @Test
    fun sizeTreatsMissingAsZero() {
        assertEquals(listOf("a", "b", "c"), ids(LibrarySort.HEAVIEST))
        assertEquals(listOf("c", "b", "a"), ids(LibrarySort.LIGHTEST))
    }

    @Test
    fun durationTreatsMissingAsZero() {
        assertEquals(listOf("a", "b", "c"), ids(LibrarySort.LONGEST))
        assertEquals(listOf("c", "b", "a"), ids(LibrarySort.SHORTEST))
    }

    @Test
    fun nameAzZa() {
        assertEquals(listOf("a", "b", "c"), ids(LibrarySort.NAME_AZ))
        assertEquals(listOf("c", "b", "a"), ids(LibrarySort.NAME_ZA))
    }

    @Test
    fun sizeFallsBackToDownloadedBytes() {
        val mixed = listOf(
            item("small", totalBytes = null, downloadedBytes = 5),
            item("big", totalBytes = 50),
        )
        assertEquals(
            listOf("big", "small"),
            LibrarySort.apply(mixed, LibrarySort.HEAVIEST).map { it.id },
        )
    }

    private fun ids(sort: LibrarySort) = LibrarySort.apply(items, sort).map { it.id }
}
