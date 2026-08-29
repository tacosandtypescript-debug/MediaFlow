package com.mediaflow.data.provider.x.live

import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import com.mediaflow.domain.live.PendingLiveDownload
import com.mediaflow.domain.live.PendingLiveDownloadRepository
import com.mediaflow.domain.live.PendingLiveDownloadStatus
import com.mediaflow.domain.live.ReplayResolutionResult
import com.mediaflow.domain.repository.DownloadRepository
import com.mediaflow.domain.repository.DownloadRequest
import com.mediaflow.domain.repository.XSpaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSpaceEndHandlerTest {

    private val spaceId = "1jGXgBDyzpNKZ"
    private val spaceUrl = "https://x.com/i/spaces/$spaceId"
    private val host = XParticipant(
        displayName = "Host",
        username = "host",
        userId = "1",
        avatarUrl = "https://pbs.twimg.com/profile_images/host_400x400.jpg",
        role = ParticipantRole.HOST,
    )

    @Test
    fun `end event twice starts a single download`() = runBlocking {
        val ended = endedSpace(replayUrl = "https://stream.pscp.tv/replay.m3u8")
        val downloads = CountingDownloadRepository()
        val pending = InMemoryPendingRepo().apply {
            savePendingDownload(
                PendingLiveDownload(
                    spaceId = spaceId,
                    title = ended.title,
                    hostHandle = ended.host.formattedHandle,
                    sourceUrl = spaceUrl,
                    autoDownloadAfterEnd = true,
                    status = PendingLiveDownloadStatus.WAITING_FOR_END,
                ),
            )
        }
        val handler = handler(endedSpace = ended, pending = pending, downloads = downloads)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())

        val first = handler.handleStreamEnded(spaceId, spaceUrl, autoDownloadWhenEnded = true, scope = scope)
        val second = handler.handleStreamEnded(spaceId, spaceUrl, autoDownloadWhenEnded = true, scope = scope)
        first?.join()
        second?.join()

        assertEquals(1, downloads.startCount)
        assertEquals(first, second)
        assertEquals(PendingLiveDownloadStatus.DOWNLOADING, pending.getPendingDownload(spaceId)?.status)
    }

    @Test
    fun `auto download off does not startDownload`() = runBlocking {
        val ended = endedSpace(replayUrl = "https://stream.pscp.tv/replay.m3u8")
        val downloads = CountingDownloadRepository()
        val pending = InMemoryPendingRepo().apply {
            savePendingDownload(
                PendingLiveDownload(
                    spaceId = spaceId,
                    title = ended.title,
                    hostHandle = ended.host.formattedHandle,
                    sourceUrl = spaceUrl,
                    autoDownloadAfterEnd = false,
                    status = PendingLiveDownloadStatus.WAITING_FOR_END,
                ),
            )
        }
        val handler = handler(endedSpace = ended, pending = pending, downloads = downloads)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        handler.handleStreamEnded(spaceId, spaceUrl, autoDownloadWhenEnded = false, scope = scope)?.join()

        assertEquals(0, downloads.startCount)
        assertEquals(PendingLiveDownloadStatus.READY_TO_DOWNLOAD, pending.getPendingDownload(spaceId)?.status)
    }

    @Test
    fun `processing then Available starts one download with host thumbnail`() = runBlocking {
        var hits = 0
        val processing = endedSpace(replayUrl = null, recordingAvailable = true)
        val ready = endedSpace(replayUrl = "https://stream.pscp.tv/replay.m3u8", recordingAvailable = true)
        val resolver = object : XSpaceMetadataResolver() {
            override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                hits++
                return if (hits >= 3) ready else processing
            }
        }
        val downloads = CountingDownloadRepository()
        val pending = InMemoryPendingRepo().apply {
            savePendingDownload(
                PendingLiveDownload(
                    spaceId = spaceId,
                    title = ready.title,
                    hostHandle = ready.host.formattedHandle,
                    sourceUrl = spaceUrl,
                    autoDownloadAfterEnd = true,
                    status = PendingLiveDownloadStatus.WAITING_FOR_END,
                ),
            )
        }
        val monitor = LiveSpaceEndMonitor(
            metadataResolver = resolver,
            maxRetries = 1,
            sleeper = {},
            replayWaitDelaysMs = listOf(1L, 1L, 1L),
            maxReplayWaitAttempts = 8,
        )
        val handler = LiveSpaceEndHandler(
            liveEndMonitor = monitor,
            pendingDownloadRepo = pending,
            spaceRepository = InMemorySpaceRepo(),
            downloadRepository = downloads,
        )
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        handler.handleStreamEnded(spaceId, spaceUrl, autoDownloadWhenEnded = true, scope = scope)?.join()

        assertEquals(1, downloads.startCount)
        assertEquals(host.avatarUrl, downloads.lastRequest?.thumbnailUrl)
        assertEquals(spaceUrl, downloads.lastRequest?.sourceUrl)
        assertTrue(downloads.lastRequest?.fileName?.contains(spaceId) == true)
    }

    @Test
    fun `resume RESOLVING_REPLAY with autoDownload starts download`() = runBlocking {
        val ended = endedSpace(replayUrl = "https://stream.pscp.tv/replay.m3u8")
        val downloads = CountingDownloadRepository()
        val pending = InMemoryPendingRepo().apply {
            savePendingDownload(
                PendingLiveDownload(
                    spaceId = spaceId,
                    title = ended.title,
                    hostHandle = ended.host.formattedHandle,
                    sourceUrl = spaceUrl,
                    autoDownloadAfterEnd = true,
                    status = PendingLiveDownloadStatus.RESOLVING_REPLAY,
                    attemptCount = 2,
                ),
            )
        }
        val handler = handler(endedSpace = ended, pending = pending, downloads = downloads)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        handler.resumeInterruptedWaits(scope)
        while (handler.isHandling(spaceId)) {
            kotlinx.coroutines.delay(10)
        }

        assertEquals(1, downloads.startCount)
    }

    @Test
    fun `still LIVE after glitch does not mark ended or download`() = runBlocking {
        val live = XSpace(
            id = spaceId,
            url = spaceUrl,
            title = "Live",
            state = XSpaceState.LIVE,
            host = host,
            audioStreamUrl = "https://stream.pscp.tv/live.m3u8",
        )
        val downloads = CountingDownloadRepository()
        val pending = InMemoryPendingRepo().apply {
            savePendingDownload(
                PendingLiveDownload(
                    spaceId = spaceId,
                    title = live.title,
                    hostHandle = live.host.formattedHandle,
                    sourceUrl = spaceUrl,
                    autoDownloadAfterEnd = true,
                    status = PendingLiveDownloadStatus.WAITING_FOR_END,
                ),
            )
        }
        val spaces = InMemorySpaceRepo()
        val handler = handler(endedSpace = live, pending = pending, downloads = downloads, spaces = spaces)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val verified = LiveSpaceEndMonitor(
            metadataResolver = object : XSpaceMetadataResolver() {
                override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace = live
            },
            maxRetries = 1,
            sleeper = {},
        ).verifySpaceEnded(spaceId, spaceUrl)
        assertTrue(verified is ReplayResolutionResult.Processing)
        handler.handleStreamEnded(spaceId, spaceUrl, autoDownloadWhenEnded = true, scope = scope)?.join()

        assertEquals(0, downloads.startCount)
        assertEquals(PendingLiveDownloadStatus.WAITING_FOR_END, pending.getPendingDownload(spaceId)?.status)
        assertNull(spaces.getSpace(spaceId))
    }

    @Test
    fun `recordingAvailable false and no url is NotAvailable without download`() = runBlocking {
        val ended = endedSpace(replayUrl = null, recordingAvailable = false)
        val downloads = CountingDownloadRepository()
        val pending = InMemoryPendingRepo().apply {
            savePendingDownload(
                PendingLiveDownload(
                    spaceId = spaceId,
                    title = ended.title,
                    hostHandle = ended.host.formattedHandle,
                    sourceUrl = spaceUrl,
                    autoDownloadAfterEnd = true,
                    status = PendingLiveDownloadStatus.WAITING_FOR_END,
                ),
            )
        }
        val handler = handler(endedSpace = ended, pending = pending, downloads = downloads)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        handler.handleStreamEnded(spaceId, spaceUrl, autoDownloadWhenEnded = true, scope = scope)?.join()

        assertEquals(0, downloads.startCount)
        assertEquals(PendingLiveDownloadStatus.FAILED, pending.getPendingDownload(spaceId)?.status)
    }

    private fun endedSpace(
        replayUrl: String?,
        recordingAvailable: Boolean = replayUrl != null,
    ) = XSpace(
        id = spaceId,
        url = spaceUrl,
        title = "Space Finalizado",
        state = XSpaceState.ENDED,
        host = host,
        audioStreamUrl = replayUrl,
        recordingAvailable = recordingAvailable,
    )

    private fun handler(
        endedSpace: XSpace,
        pending: PendingLiveDownloadRepository,
        downloads: DownloadRepository,
        spaces: XSpaceRepository = InMemorySpaceRepo(),
    ): LiveSpaceEndHandler {
        val monitor = LiveSpaceEndMonitor(
            metadataResolver = object : XSpaceMetadataResolver() {
                override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace = endedSpace
            },
            maxRetries = 1,
            sleeper = {},
            maxReplayWaitAttempts = 1,
        )
        return LiveSpaceEndHandler(
            liveEndMonitor = monitor,
            pendingDownloadRepo = pending,
            spaceRepository = spaces,
            downloadRepository = downloads,
        )
    }

    private class InMemoryPendingRepo : PendingLiveDownloadRepository {
        private val items = MutableStateFlow<List<PendingLiveDownload>>(emptyList())
        override fun observePendingDownloads(): Flow<List<PendingLiveDownload>> = items.asStateFlow()
        override suspend fun getPendingDownload(spaceId: String): PendingLiveDownload? =
            items.value.firstOrNull { it.spaceId == spaceId }
        override suspend fun savePendingDownload(download: PendingLiveDownload) {
            val next = items.value.toMutableList()
            val index = next.indexOfFirst { it.spaceId == download.spaceId }
            if (index >= 0) next[index] = download else next.add(download)
            items.value = next
        }
        override suspend fun removePendingDownload(spaceId: String) {
            items.value = items.value.filter { it.spaceId != spaceId }
        }
        override suspend fun isAutoDownloadEnabled(spaceId: String): Boolean =
            items.value.firstOrNull { it.spaceId == spaceId }?.autoDownloadAfterEnd == true
        override suspend fun setAutoDownloadEnabled(
            spaceId: String,
            title: String,
            hostHandle: String,
            sourceUrl: String,
            enabled: Boolean,
        ) {
            val existing = getPendingDownload(spaceId)
            savePendingDownload(
                (existing ?: PendingLiveDownload(
                    spaceId = spaceId,
                    title = title,
                    hostHandle = hostHandle,
                    sourceUrl = sourceUrl,
                )).copy(autoDownloadAfterEnd = enabled),
            )
        }
    }

    private class InMemorySpaceRepo : XSpaceRepository {
        private val items = MutableStateFlow<Map<String, XSpace>>(emptyMap())
        override suspend fun saveSpace(space: XSpace, mediaId: String?) {
            items.value = items.value + (space.id to space)
        }
        override suspend fun getSpace(spaceId: String): XSpace? = items.value[spaceId]
        override suspend fun getSpaceForMedia(mediaId: String): XSpace? = items.value.values.firstOrNull {
            it.id == mediaId || it.url == mediaId || it.audioStreamUrl == mediaId
        }
        override fun observeAllSpaces(): Flow<Map<String, XSpace>> = items.asStateFlow()
        override fun observeSpaceForMedia(mediaId: String): Flow<XSpace?> =
            MutableStateFlow(null)
    }

    private class CountingDownloadRepository : DownloadRepository {
        var startCount = 0
        var lastRequest: DownloadRequest? = null
        private val items = MutableStateFlow<List<DownloadItem>>(emptyList())
        override fun observeDownloads(): Flow<List<DownloadItem>> = items
        override suspend fun getDownloadById(id: String): DownloadItem? = items.value.firstOrNull { it.id == id }
        override suspend fun startDownload(request: DownloadRequest): String {
            startCount++
            lastRequest = request
            val id = "dl-$startCount"
            items.value = items.value + DownloadItem(
                id = id,
                sourceUrl = request.sourceUrl,
                fileName = request.fileName,
                mediaType = request.mediaType,
                thumbnailUri = request.thumbnailUrl,
            )
            return id
        }
        override suspend fun pauseDownload(id: String) {}
        override suspend fun resumeDownload(id: String) {}
        override suspend fun cancelDownload(id: String) {}
        override suspend fun retryDownload(id: String) {}
        override suspend fun removeDownload(id: String) {}
    }
}
