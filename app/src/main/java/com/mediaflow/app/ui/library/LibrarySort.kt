package com.mediaflow.app.ui.library

import com.mediaflow.core.model.DownloadItem

enum class LibrarySort {
    NEWEST,
    OLDEST,
    HEAVIEST,
    LIGHTEST,
    LONGEST,
    SHORTEST,
    NAME_AZ,
    NAME_ZA,
    ;

    companion object {
        fun apply(items: List<DownloadItem>, sort: LibrarySort): List<DownloadItem> =
            LibrarySorter.apply(items, sort)
    }
}

object LibrarySorter {
    fun apply(items: List<DownloadItem>, sort: LibrarySort): List<DownloadItem> = when (sort) {
        LibrarySort.NEWEST -> items.sortedByDescending { it.createdAt }
        LibrarySort.OLDEST -> items.sortedBy { it.createdAt }
        LibrarySort.HEAVIEST -> items.sortedByDescending { sizeOf(it) }
        LibrarySort.LIGHTEST -> items.sortedBy { sizeOf(it) }
        LibrarySort.LONGEST -> items.sortedByDescending { it.durationSeconds ?: 0L }
        LibrarySort.SHORTEST -> items.sortedBy { it.durationSeconds ?: 0L }
        LibrarySort.NAME_AZ -> items.sortedBy { displayName(it).lowercase() }
        LibrarySort.NAME_ZA -> items.sortedByDescending { displayName(it).lowercase() }
    }

    fun sizeOf(item: DownloadItem): Long =
        item.totalBytes
            ?: item.selectedFormat?.fileSize
            ?: item.downloadedBytes

    fun displayName(item: DownloadItem): String =
        item.title?.takeIf { it.isNotBlank() }
            ?: item.fileName?.takeIf { it.isNotBlank() }
            ?: item.id
}
