package com.mediaflow.data.media

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus

/**
 * Combines MediaStore gallery rows with completed downloads so Biblioteca
 * shows items even when a gallery query misses a just-finished file.
 */
object LibraryMediaMerge {
    fun merge(gallery: List<DownloadItem>, downloads: List<DownloadItem>): List<DownloadItem> {
        val completed = downloads.filter {
            it.status == DownloadStatus.COMPLETED && !it.localUri.isNullOrBlank()
        }
        if (gallery.isEmpty()) return completed.distinctBy(::identity)
        if (completed.isEmpty()) return gallery.distinctBy(::identity)

        val byKey = HashMap<String, DownloadItem>()
        completed.forEach { item -> keys(item).forEach { key -> byKey.putIfAbsent(key, item) } }

        val used = HashSet<String>()
        val mergedGallery = gallery.map { item ->
            val match = keys(item).firstNotNullOfOrNull(byKey::get)
            if (match != null) {
                used.add(match.id)
                enrich(item, match)
            } else item
        }

        val extras = completed.filter { download ->
            if (download.id in used) return@filter false
            val downloadKeys = keys(download)
            mergedGallery.none { galleryItem -> keys(galleryItem).any(downloadKeys::contains) }
        }

        return (mergedGallery + extras).distinctBy(::identity)
    }

    internal fun enrich(gallery: DownloadItem, download: DownloadItem): DownloadItem {
        val title = preferredTitle(download.title, gallery.title)
        val fileName = preferredTitle(download.fileName, gallery.fileName)
        val thumbnail = listOf(download.thumbnailUri, gallery.thumbnailUri)
            .firstOrNull { uri -> !uri.isNullOrBlank() && !VideoFrameThumbnail.needsFrame(uri) }
            ?: gallery.thumbnailUri.takeUnless { VideoFrameThumbnail.needsFrame(it) }
            ?: download.thumbnailUri.takeUnless { VideoFrameThumbnail.needsFrame(it) }
        val duration = download.durationSeconds?.takeIf { it > 0 } ?: gallery.durationSeconds
        val size = download.totalBytes?.takeIf { it > 0 }
            ?: download.selectedFormat?.fileSize
            ?: gallery.selectedFormat?.fileSize
        val galleryFormat = gallery.selectedFormat
        val format = galleryFormat?.copy(fileSize = size ?: galleryFormat.fileSize)
            ?: download.selectedFormat
        return gallery.copy(
            title = title,
            fileName = fileName,
            thumbnailUri = thumbnail,
            durationSeconds = duration,
            selectedFormat = format,
            totalBytes = size ?: gallery.totalBytes,
            width = gallery.width ?: download.width,
            height = gallery.height ?: download.height,
        )
    }

    internal fun preferredTitle(primary: String?, fallback: String?): String? {
        val a = primary?.takeIf { it.isNotBlank() && !isNumericIdTitle(it) }
        val b = fallback?.takeIf { it.isNotBlank() && !isNumericIdTitle(it) }
        return a ?: b ?: primary?.takeIf { it.isNotBlank() } ?: fallback
    }

    internal fun isNumericIdTitle(title: String): Boolean {
        val stem = title.substringAfterLast('/').substringBeforeLast('.')
        return stem.isNotEmpty() && stem.all { it.isDigit() }
    }

    private fun identity(item: DownloadItem): String {
        MediaFlowLibraryStore.mediaStoreId(item.localUri ?: item.id)?.let { return "ms:$it" }
        item.localUri?.takeIf { it.isNotBlank() }?.let { return it }
        item.fileName?.takeIf { it.isNotBlank() }?.let { return "name:${normalizeName(it)}" }
        return item.id
    }

    private fun keys(item: DownloadItem): Set<String> = buildSet {
        add(item.id)
        item.localUri?.takeIf { it.isNotBlank() }?.let { uri ->
            add(uri)
            add(uri.substringAfterLast('/'))
        }
        item.fileName?.takeIf { it.isNotBlank() }?.let { add("name:${normalizeName(it)}") }
        MediaFlowLibraryStore.mediaStoreId(item.localUri ?: item.id)?.let { add("ms:$it") }
        MediaFlowLibraryStore.mediaStoreId(item.id)?.let { add("ms:$it") }
    }

    private fun normalizeName(name: String): String =
        name.replace(Regex(" \\([0-9]+\\)(?=\\.[^.]+$)"), "").lowercase()
}
