package com.mediaflow.data.provider.x.live

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.live.PendingLiveDownload
import com.mediaflow.domain.live.PendingLiveDownloadStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceDownloadDedupTest {

    private val spaceId = "1jGXgBDyzpNKZ"

    @Test
    fun `skips when pending is already DOWNLOADING`() {
        val pending = PendingLiveDownload(
            spaceId = spaceId,
            title = "Space",
            hostHandle = "@host",
            sourceUrl = "https://x.com/i/spaces/$spaceId",
            status = PendingLiveDownloadStatus.DOWNLOADING,
            downloadId = "abc",
        )
        assertTrue(SpaceDownloadDedup.shouldSkipDownload(spaceId, pending, emptyList()))
    }

    @Test
    fun `skips existing download whose fileName contains spaceId`() {
        val downloads = listOf(
            DownloadItem(
                id = "deadbeef",
                sourceUrl = "https://stream.pscp.tv/replay.m3u8",
                fileName = "Space_host_$spaceId.m4a",
                mediaType = MediaType.AUDIO,
            ),
        )
        assertTrue(SpaceDownloadDedup.shouldSkipDownload(spaceId, pending = null, downloads = downloads))
    }

    @Test
    fun `skips existing download whose sourceUrl contains spaceId`() {
        val downloads = listOf(
            DownloadItem(
                id = "deadbeef",
                sourceUrl = "https://x.com/i/spaces/$spaceId",
                fileName = "audio.m4a",
                mediaType = MediaType.AUDIO,
            ),
        )
        assertTrue(SpaceDownloadDedup.shouldSkipDownload(spaceId, pending = null, downloads = downloads))
    }

    @Test
    fun `skips when pending downloadId already exists in downloads`() {
        val downloads = listOf(
            DownloadItem(
                id = "pending-dl-1",
                sourceUrl = "https://stream.pscp.tv/replay.m3u8",
                fileName = "other.m4a",
                mediaType = MediaType.AUDIO,
            ),
        )
        val pending = PendingLiveDownload(
            spaceId = spaceId,
            title = "Space",
            hostHandle = "@host",
            sourceUrl = "https://x.com/i/spaces/$spaceId",
            status = PendingLiveDownloadStatus.WAITING_FOR_END,
            downloadId = "pending-dl-1",
        )
        assertTrue(SpaceDownloadDedup.shouldSkipDownload(spaceId, pending, downloads))
    }

    @Test
    fun `does not skip SHA download id that does not contain spaceId`() {
        val downloads = listOf(
            DownloadItem(
                id = spaceId.take(8) + "deadbeefcafebabe",
                sourceUrl = "https://youtube.com/watch?v=1",
                fileName = "video.mp4",
                mediaType = MediaType.VIDEO,
            ),
        )
        assertFalse(SpaceDownloadDedup.shouldSkipDownload(spaceId, pending = null, downloads = downloads))
    }

    @Test
    fun `does not skip when nothing matches`() {
        val downloads = listOf(
            DownloadItem(
                id = "other",
                sourceUrl = "https://youtube.com/watch?v=1",
                fileName = "video.mp4",
                mediaType = MediaType.VIDEO,
            ),
        )
        assertFalse(SpaceDownloadDedup.shouldSkipDownload(spaceId, pending = null, downloads = downloads))
    }
}
