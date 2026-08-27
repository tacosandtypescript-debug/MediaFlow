package com.mediaflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaModelsTest {

    @Test
    fun `media types exist`() {
        assertEquals(MediaType.VIDEO, MediaType.VIDEO)
        assertEquals(MediaType.AUDIO, MediaType.AUDIO)
        assertEquals(setOf(MediaType.VIDEO, MediaType.AUDIO), MediaType.entries.toSet())
    }

    @Test
    fun `download statuses exist`() {
        val expected = listOf(
            DownloadStatus.IDLE,
            DownloadStatus.QUEUED,
            DownloadStatus.ANALYZING,
            DownloadStatus.PREPARING,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PAUSED,
            DownloadStatus.COMPLETED,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELED,
        )
        assertEquals(expected, DownloadStatus.entries)
    }

    @Test
    fun `media format with full fields`() {
        val format = MediaFormat(
            formatId = "137",
            extension = "mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            qualityLabel = "1080p",
            width = 1920,
            height = 1080,
            bitrate = 5_000_000L,
            fileSize = 123_456L,
            isProgressive = true,
            requiresMuxing = false,
        )
        assertEquals("137", format.formatId)
        assertEquals("mp4", format.extension)
        assertEquals("video/mp4", format.mimeType)
        assertEquals(MediaType.VIDEO, format.mediaType)
        assertEquals("1080p", format.qualityLabel)
        assertEquals(1920, format.width)
        assertEquals(1080, format.height)
        assertEquals(5_000_000L, format.bitrate)
        assertEquals(123_456L, format.fileSize)
        assertTrue(format.isProgressive)
        assertFalse(format.requiresMuxing)
    }

    @Test
    fun `media format with empty optional fields`() {
        val format = MediaFormat(formatId = "251", mediaType = MediaType.AUDIO)
        assertEquals("251", format.formatId)
        assertEquals(MediaType.AUDIO, format.mediaType)
        assertNull(format.extension)
        assertNull(format.mimeType)
        assertNull(format.qualityLabel)
        assertNull(format.width)
        assertNull(format.height)
        assertNull(format.bitrate)
        assertNull(format.fileSize)
        assertFalse(format.isProgressive)
        assertFalse(format.requiresMuxing)
    }

    @Test
    fun `create pending download item`() {
        val item = DownloadItem(
            id = "d-1",
            sourceUrl = "https://example.com/video",
            mediaType = MediaType.VIDEO,
        )
        assertEquals(DownloadStatus.IDLE, item.status)
        assertEquals(0f, item.progress, 0f)
        assertEquals(0L, item.downloadedBytes)
        assertNull(item.localUri)
        assertNull(item.completedAt)
    }

    @Test
    fun `create completed download item preserving values`() {
        val format = MediaFormat(formatId = "22", mediaType = MediaType.VIDEO)
        val item = DownloadItem(
            id = "d-2",
            sourceUrl = "https://example.com/audio",
            title = "Mi canción",
            fileName = "mi_cancion.m4a",
            mediaType = MediaType.AUDIO,
            selectedFormat = format,
            localUri = "file:///storage/emulated/0/MediaFlow/mi_cancion.m4a",
            thumbnailUri = null,
            progress = 1f,
            downloadedBytes = 2048L,
            totalBytes = 2048L,
            speedBytesPerSecond = 0L,
            status = DownloadStatus.COMPLETED,
            errorMessage = null,
            createdAt = 100L,
            completedAt = 200L,
        )
        assertEquals("d-2", item.id)
        assertEquals("https://example.com/audio", item.sourceUrl)
        assertEquals("Mi canción", item.title)
        assertEquals("mi_cancion.m4a", item.fileName)
        assertEquals(MediaType.AUDIO, item.mediaType)
        assertEquals(format, item.selectedFormat)
        assertEquals("file:///storage/emulated/0/MediaFlow/mi_cancion.m4a", item.localUri)
        assertEquals(1f, item.progress, 0f)
        assertEquals(2048L, item.downloadedBytes)
        assertEquals(2048L, item.totalBytes)
        assertEquals(DownloadStatus.COMPLETED, item.status)
        assertEquals(100L, item.createdAt)
        assertEquals(200L, item.completedAt)
    }

    @Test
    fun `download item failed holds error and partial data`() {
        val item = DownloadItem(
            id = "d-3",
            sourceUrl = "https://example.com/v",
            mediaType = MediaType.VIDEO,
            progress = 0.4f,
            downloadedBytes = 400L,
            totalBytes = 1000L,
            status = DownloadStatus.FAILED,
            errorMessage = "sin conexión",
        )
        assertEquals(DownloadStatus.FAILED, item.status)
        assertEquals("sin conexión", item.errorMessage)
        assertEquals(0.4f, item.progress, 0f)
        assertEquals(400L, item.downloadedBytes)
        assertEquals(1000L, item.totalBytes)
    }
}
