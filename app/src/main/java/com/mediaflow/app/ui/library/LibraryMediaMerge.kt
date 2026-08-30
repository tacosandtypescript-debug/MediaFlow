package com.mediaflow.app.ui.library

import com.mediaflow.app.ui.common.media.isLoadableArtworkUrl
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus

/**
 * Combines MediaStore gallery rows with completed yt-dlp downloads so Biblioteca
 * shows a file even before MediaStore indexes it, without duplicating rows.
 */
object LibraryMediaMerge {
    fun merge(gallery: List<DownloadItem>, downloads: List<DownloadItem>): List<DownloadItem> {
        val completed = downloads.filter {
            it.status == DownloadStatus.COMPLETED && !it.localUri.isNullOrBlank()
        }
        if (gallery.isEmpty()) return completed
        val thumbs = HashMap<String, String>()
        val extrasByKey = HashMap<String, DownloadItem>()
        downloads.forEach { item ->
            item.thumbnailUri?.takeIf(::isLoadableArtworkUrl)?.let { uri ->
                keys(item).forEach { key -> thumbs[key] = uri }
            }
        }
        completed.forEach { item ->
            keys(item).forEach { key -> extrasByKey[key] = item }
        }
        val mergedGallery = gallery.map { item ->
            val overlay = keys(item).firstNotNullOfOrNull(thumbs::get)
            val fromDownload = keys(item).firstNotNullOfOrNull(extrasByKey::get)
            var next = item
            if (!isLoadableArtworkUrl(item.thumbnailUri) && overlay != null) {
                next = next.copy(thumbnailUri = overlay)
            }
            if (fromDownload != null) {
                next = next.copy(
                    title = item.title.takeUnless { it.isNullOrBlank() || it.all(Char::isDigit) }
                        ?: fromDownload.title ?: item.title,
                    durationSeconds = item.durationSeconds ?: fromDownload.durationSeconds,
                    width = item.width ?: fromDownload.width ?: fromDownload.selectedFormat?.width,
                    height = item.height ?: fromDownload.height ?: fromDownload.selectedFormat?.height,
                    totalBytes = item.totalBytes ?: fromDownload.totalBytes ?: fromDownload.selectedFormat?.fileSize,
                )
            }
            next
        }
        val galleryKeys = gallery.flatMap { keys(it) }.toSet()
        val extras = completed.filter { item -> keys(item).none { it in galleryKeys } }
        return mergedGallery + extras
    }

    fun keys(item: DownloadItem): List<String> = listOfNotNull(
        item.id.takeIf { it.isNotBlank() },
        item.localUri?.takeIf { it.isNotBlank() },
        item.sourceUrl.takeIf { it.isNotBlank() },
        item.fileName?.takeIf { it.isNotBlank() },
    )
}
