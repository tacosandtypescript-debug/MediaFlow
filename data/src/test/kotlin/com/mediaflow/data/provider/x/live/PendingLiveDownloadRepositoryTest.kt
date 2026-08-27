package com.mediaflow.data.provider.x.live

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.domain.live.PendingLiveDownload
import com.mediaflow.domain.live.PendingLiveDownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PendingLiveDownloadRepositoryTest {

    @Test
    fun `savePendingDownload and setAutoDownloadEnabled persist correctly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        File(context.filesDir, "pending_live_downloads.json").delete()

        val repo = PendingLiveDownloadRepositoryImpl(context)

        assertFalse(repo.isAutoDownloadEnabled("space_123"))

        repo.setAutoDownloadEnabled(
            spaceId = "space_123",
            title = "Test Space",
            hostHandle = "@test_host",
            sourceUrl = "https://x.com/i/spaces/space_123",
            enabled = true,
        )

        assertTrue(repo.isAutoDownloadEnabled("space_123"))

        val item = repo.getPendingDownload("space_123")
        assertNotNull(item)
        assertEquals("Test Space", item?.title)
        assertEquals(PendingLiveDownloadStatus.WAITING_FOR_END, item?.status)

        // Reload fresh repository instance from disk to verify persistence
        val reloadedRepo = PendingLiveDownloadRepositoryImpl(context)
        assertTrue(reloadedRepo.isAutoDownloadEnabled("space_123"))
        assertEquals(1, reloadedRepo.observePendingDownloads().first().size)

        repo.removePendingDownload("space_123")
        assertFalse(repo.isAutoDownloadEnabled("space_123"))
    }
}
