package com.mediaflow.data.media

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMediaMergeTest {
    @Test
    fun completedDownloadsAppearWhenGalleryMissesThem() {
        val gallery = emptyList<DownloadItem>()
        val downloads = listOf(
            item(
                id = "dl-audio",
                title = "Song Title",
                localUri = "file:///data/user/0/com.mediaflow.app/files/downloads/song.m4a",
                mediaType = MediaType.AUDIO,
                status = DownloadStatus.COMPLETED,
                durationSeconds = 120,
                totalBytes = 4_000_000,
                thumbnailUri = "file:///data/user/0/com.mediaflow.app/files/thumbs/dl-audio.jpg",
            ),
            item(
                id = "dl-video",
                title = "Clip",
                localUri = "content://media/external/video/media/99",
                mediaType = MediaType.VIDEO,
                status = DownloadStatus.COMPLETED,
                durationSeconds = 15,
                totalBytes = 8_000_000,
            ),
            item(
                id = "dl-running",
                title = "In progress",
                localUri = "file:///tmp/partial.mp4",
                mediaType = MediaType.VIDEO,
                status = DownloadStatus.DOWNLOADING,
            ),
        )
        val merged = LibraryMediaMerge.merge(gallery, downloads)
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.id == "dl-audio" && it.title == "Song Title" && it.durationSeconds == 120L })
        assertTrue(merged.any { it.id == "dl-video" })
    }

    @Test
    fun deduplicatesByLocalUriIdAndFileName() {
        val gallery = listOf(
            item(
                id = "content://media/external/video/media/12",
                title = "12.mp4",
                fileName = "clip.mp4",
                localUri = "content://media/external/video/media/12",
                mediaType = MediaType.VIDEO,
                totalBytes = 1000,
            ),
        )
        val downloads = listOf(
            item(
                id = "dl-1",
                title = "Real Clip Title",
                fileName = "clip.mp4",
                localUri = "content://media/external/video/media/12",
                mediaType = MediaType.VIDEO,
                status = DownloadStatus.COMPLETED,
                durationSeconds = 42,
                thumbnailUri = "file:///data/user/0/com.mediaflow.app/files/thumbs/dl-1.jpg",
            ),
            item(
                id = "dl-dup-name",
                title = "Real Clip Title",
                fileName = "clip (1).mp4",
                localUri = "file:///data/user/0/com.mediaflow.app/files/downloads/clip.mp4",
                mediaType = MediaType.VIDEO,
                status = DownloadStatus.COMPLETED,
            ),
        )
        val merged = LibraryMediaMerge.merge(gallery, downloads)
        assertEquals(1, merged.size)
        val row = merged.single()
        assertEquals("Real Clip Title", row.title)
        assertEquals(42L, row.durationSeconds)
        assertEquals("file:///data/user/0/com.mediaflow.app/files/thumbs/dl-1.jpg", row.thumbnailUri)
        assertEquals("content://media/external/video/media/12", row.localUri)
    }

    @Test
    fun prefersDownloadTitleOverNumericGalleryName() {
        val gallery = listOf(
            item(
                id = "content://media/external/audio/media/7",
                title = "1938844.m4a",
                fileName = "1938844.m4a",
                localUri = "content://media/external/audio/media/7",
                mediaType = MediaType.AUDIO,
            ),
        )
        val downloads = listOf(
            item(
                id = "dl-space",
                title = "Host conversation",
                fileName = "1938844.m4a",
                localUri = "content://media/external/audio/media/7",
                mediaType = MediaType.AUDIO,
                status = DownloadStatus.COMPLETED,
            ),
        )
        val merged = LibraryMediaMerge.merge(gallery, downloads)
        assertEquals("Host conversation", merged.single().title)
    }

    @Test
    fun prefersHumanTitleHelper() {
        assertEquals(
            "Podcast",
            LibraryMediaMerge.preferredTitle("Podcast", "12.m4a"),
        )
        assertTrue(LibraryMediaMerge.isNumericIdTitle("12.mp4"))
        assertTrue(!LibraryMediaMerge.isNumericIdTitle("Clip.mp4"))
    }

    private fun item(
        id: String,
        title: String? = null,
        fileName: String? = title,
        localUri: String? = null,
        mediaType: MediaType,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        durationSeconds: Long? = null,
        totalBytes: Long? = null,
        thumbnailUri: String? = null,
    ) = DownloadItem(
        id = id,
        sourceUrl = "https://example.com/$id",
        title = title,
        fileName = fileName,
        mediaType = mediaType,
        selectedFormat = MediaFormat(
            formatId = "test",
            mediaType = mediaType,
            fileSize = totalBytes,
            isProgressive = true,
        ),
        localUri = localUri,
        thumbnailUri = thumbnailUri,
        durationSeconds = durationSeconds,
        totalBytes = totalBytes,
        status = status,
    )
}
