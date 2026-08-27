package com.mediaflow.data

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.data.download.PlatformDownloadStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.io.File
import org.junit.runner.RunWith

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class PlatformDownloadStoreTest {
    @Test
    fun `platform history survives a store reload with technical metadata`() {
        val file = File.createTempFile("mediaflow-platform", ".json")
        file.delete()
        val item = DownloadItem(
            id = "platform-1",
            sourceUrl = "https://example.test/reel/1",
            title = "Vídeo legal",
            fileName = "video.mp4",
            mediaType = MediaType.VIDEO,
            selectedFormat = MediaFormat(
                formatId = "137",
                extension = "mp4",
                mimeType = "video/mp4",
                mediaType = MediaType.VIDEO,
                qualityLabel = "1080p",
                width = 1920,
                height = 1080,
                fps = 30.0,
                container = "mp4",
                videoCodec = "avc1",
                audioCodec = null,
                durationSeconds = 42,
                fileSize = 10_000,
                isProgressive = false,
                requiresMuxing = true,
            ),
            durationSeconds = 42,
            progress = 1f,
            isProgressKnown = true,
            downloadedBytes = 10_000,
            totalBytes = 10_000,
            status = DownloadStatus.COMPLETED,
            createdAt = 100,
            completedAt = 200,
        )

        PlatformDownloadStore(file).save(listOf(item))
        val restored = PlatformDownloadStore(file).load()
        assertTrue("restored=${restored.size}; json=${file.readText()}", restored.isNotEmpty())

        assertEquals(item.id, restored.single().id)
        assertEquals(item.status, restored.single().status)
        assertEquals(item.durationSeconds, restored.single().durationSeconds)
        assertEquals(item.selectedFormat?.height, restored.single().selectedFormat?.height)
        assertEquals(item.selectedFormat?.requiresMuxing, restored.single().selectedFormat?.requiresMuxing)
        assertTrue(restored.single().selectedFormat?.videoCodec == "avc1")
    }
}
