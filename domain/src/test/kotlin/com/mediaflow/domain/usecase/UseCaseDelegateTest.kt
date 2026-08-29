package com.mediaflow.domain.usecase

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.DownloadRepository
import com.mediaflow.domain.repository.DownloadRequest
import com.mediaflow.domain.repository.GalleryRepository
import com.mediaflow.domain.repository.SourceInfo
import com.mediaflow.domain.repository.SourceResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test doubles used only within tests.
 */
private class FakeDownloadRepository : DownloadRepository {
    val requests = mutableListOf<DownloadRequest>()
    var pausedId: String? = null
    var resumedId: String? = null
    var canceledId: String? = null
    var retriedId: String? = null
    var removedId: String? = null
    val downloads = listOf(
        DownloadItem(id = "d-1", sourceUrl = "https://example.com/v", mediaType = MediaType.VIDEO),
    )

    override fun observeDownloads(): Flow<List<DownloadItem>> = flowOf(downloads)
    override suspend fun getDownloadById(id: String): DownloadItem? = downloads.firstOrNull { it.id == id }
    override suspend fun startDownload(request: DownloadRequest): String {
        requests += request
        return "new-id"
    }

    override suspend fun pauseDownload(id: String) { pausedId = id }
    override suspend fun resumeDownload(id: String) { resumedId = id }
    override suspend fun cancelDownload(id: String) { canceledId = id }
    override suspend fun retryDownload(id: String) { retriedId = id }
    override suspend fun removeDownload(id: String) { removedId = id }
}

private class FakeSourceResolver : SourceResolver {
    var lastUrl: String? = null
    override suspend fun analyze(sourceUrl: String): SourceInfo {
        lastUrl = sourceUrl
        return SourceInfo(sourceUrl = sourceUrl, title = "Título de prueba")
    }
}

private class FakeGalleryRepository : GalleryRepository {
    val gallery = listOf(
        DownloadItem(id = "g-1", sourceUrl = "https://example.com/a", mediaType = MediaType.AUDIO),
    )
    override fun observeGallery(): Flow<List<DownloadItem>> = flowOf(gallery)
    override suspend fun getItemById(id: String): DownloadItem? = gallery.firstOrNull { it.id == id }
    override suspend fun deleteItem(id: String): Boolean = true
    override suspend fun renameItem(id: String, newName: String): DownloadItem? = null
    override suspend fun getLocalUri(id: String): String? = "file:///g/$id"
}

class UseCaseDelegateTest {

    @Test
    fun `analyze source delegates to resolver`() = runTest {
        val resolver = FakeSourceResolver()
        val result = AnalyzeSourceUseCase(resolver)("https://example.com/video")

        assertEquals("https://example.com/video", resolver.lastUrl)
        assertEquals("https://example.com/video", result.sourceUrl)
        assertEquals("Título de prueba", result.title)
    }

    @Test
    fun `start download delegates to repository`() = runTest {
        val repository = FakeDownloadRepository()
        val request = DownloadRequest(
            sourceUrl = "https://example.com/v",
            mediaType = MediaType.VIDEO,
            qualityLabel = "720p",
            fileName = "mi_video.mp4",
        )

        val id = StartDownloadUseCase(repository)(request)

        assertEquals("new-id", id)
        assertEquals(listOf(request), repository.requests)
    }

    @Test
    fun `start download preserves thumbnail url`() = runTest {
        val repository = FakeDownloadRepository()
        val request = DownloadRequest(
            sourceUrl = "https://example.com/v",
            mediaType = MediaType.VIDEO,
            thumbnailUrl = "https://example.com/thumb.jpg",
        )

        StartDownloadUseCase(repository)(request)

        assertEquals("https://example.com/thumb.jpg", repository.requests.single().thumbnailUrl)
    }

    @Test
    fun `pause download delegates to repository`() = runTest {
        val repository = FakeDownloadRepository()
        PauseDownloadUseCase(repository)("d-9")
        assertEquals("d-9", repository.pausedId)
    }

    @Test
    fun `resume download delegates to repository`() = runTest {
        val repository = FakeDownloadRepository()
        ResumeDownloadUseCase(repository)("d-9")
        assertEquals("d-9", repository.resumedId)
    }

    @Test
    fun `cancel download delegates to repository`() = runTest {
        val repository = FakeDownloadRepository()
        CancelDownloadUseCase(repository)("d-9")
        assertEquals("d-9", repository.canceledId)
    }

    @Test
    fun `retry download delegates to repository`() = runTest {
        val repository = FakeDownloadRepository()
        RetryDownloadUseCase(repository)("d-9")
        assertEquals("d-9", repository.retriedId)
    }

    @Test
    fun `get downloads returns repository flow`() = runTest {
        val repository = FakeDownloadRepository()
        val items = GetDownloadsUseCase(repository)().first()
        assertEquals(repository.downloads, items)
    }

    @Test
    fun `get gallery items returns gallery flow`() = runTest {
        val gallery = FakeGalleryRepository()
        val items = GetGalleryItemsUseCase(gallery)().first()
        assertEquals(gallery.gallery, items)
        assertTrue(items.isNotEmpty())
        assertEquals(MediaType.AUDIO, items.first().mediaType)
    }
}
