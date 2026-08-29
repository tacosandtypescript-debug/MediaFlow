package com.mediaflow.app.ui.player

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.data.player.background.PlayerSessionHolder
import com.mediaflow.data.provider.x.live.LiveSpaceEndMonitor
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import com.mediaflow.domain.live.LiveSpaceEndState
import com.mediaflow.domain.live.PendingLiveDownload
import com.mediaflow.domain.live.PendingLiveDownloadRepository
import com.mediaflow.domain.live.PendingLiveDownloadStatus
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.EngineState
import com.mediaflow.domain.player.PlaybackEngine
import com.mediaflow.domain.player.PlaybackEvent
import com.mediaflow.domain.player.PlayerService
import com.mediaflow.domain.repository.DownloadRepository
import com.mediaflow.domain.repository.DownloadRequest
import com.mediaflow.domain.repository.ProgressRepository
import com.mediaflow.domain.repository.XSpaceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlayerViewModelBackgroundTest {

    private val fakeSpace = XSpace(
        id = "1jGXgBDyzpNKZ",
        url = "https://x.com/i/spaces/1jGXgBDyzpNKZ",
        title = "Space de Prueba",
        state = XSpaceState.LIVE,
        host = XParticipant(
            displayName = "Elon",
            username = "elon",
            userId = "1",
            avatarUrl = "https://pbs.twimg.com/profile_images/elon_400x400.jpg",
            role = ParticipantRole.HOST,
        ),
        audioStreamUrl = "https://stream.pscp.tv/live.m3u8",
    )

    private val fakeSpaceRepo = object : XSpaceRepository {
        override suspend fun saveSpace(space: XSpace, mediaId: String?) {}
        override suspend fun getSpace(spaceId: String): XSpace? = fakeSpace
        override suspend fun getSpaceForMedia(mediaId: String): XSpace? = fakeSpace
        override fun observeAllSpaces(): Flow<Map<String, XSpace>> = MutableStateFlow(mapOf(fakeSpace.id to fakeSpace))
        override fun observeSpaceForMedia(mediaId: String): Flow<XSpace?> = MutableStateFlow(fakeSpace)
    }

    @Before
    fun setUp() {
        PlayerSessionHolder.setForTesting(null) // reset
    }

    @Test
    fun `open does not reload if session is already playing requested uri`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        var loadCount = 0

        val fakeEngine = object : PlaybackEngine {
            private val _state = MutableStateFlow(EngineState())
            override val state: StateFlow<EngineState> = _state.asStateFlow()
            override val events: Flow<PlaybackEvent> = MutableSharedFlow()

            override fun load(mediaSource: String, startPositionMs: Long, autoPlay: Boolean) {
                loadCount++
                _state.value = _state.value.copy(playbackState = EnginePlaybackState.PLAYING)
            }
            override fun play() {}
            override fun pause() {}
            override fun stop() {}
            override fun seekTo(positionMs: Long) {}
            override fun setSpeed(speed: Float) {}
            override fun setVolume(volume: Int) {}
            override fun setMute(muted: Boolean) {}
            override fun attachSurface(surface: Any?) {}
            override fun detachSurface() {}
            override fun release() {}
        }

        val fakeProgressRepo = object : ProgressRepository {
            override suspend fun saveProgress(progress: PlaybackProgress) {}
            override suspend fun getProgress(mediaId: String): PlaybackProgress? = null
            override fun observeProgress(mediaId: String): Flow<PlaybackProgress?> = MutableStateFlow(null)
            override fun observeAllProgress(): Flow<Map<String, PlaybackProgress>> = MutableStateFlow(emptyMap())
            override suspend fun resetProgress(mediaId: String) {}
            override suspend fun markCompleted(mediaId: String, totalDurationMs: Long) {}
        }

        val sharedService = PlayerService(fakeEngine, fakeProgressRepo)
        PlayerSessionHolder.setForTesting(sharedService)

        val vm1 = PlayerViewModel(app, sharedService, fakeSpaceRepo)
        vm1.open("https://stream.pscp.tv/live.m3u8", "Space", isLive = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(1, loadCount)

        // Simulate reopening/reconnecting UI with new ViewModel instance
        val vm2 = PlayerViewModel(app, sharedService, fakeSpaceRepo)
        vm2.open("https://stream.pscp.tv/live.m3u8", "Space", isLive = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // loadCount should remain 1 because it's the identical ongoing media session
        assertEquals(1, loadCount)
    }

    @Test
    fun `toggleAutoDownload toggles state correctly`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = PlayerViewModel(app, PlayerSessionHolder.get(app), fakeSpaceRepo)

        vm.open("https://stream.pscp.tv/live.m3u8", "Space", isLive = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse(vm.uiState.value.isAutoDownloadEnabled)

        vm.toggleAutoDownload()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(vm.uiState.value.isAutoDownloadEnabled)

        vm.toggleAutoDownload()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse(vm.uiState.value.isAutoDownloadEnabled)
    }

    @Test
    fun `PlaybackFinished on live space shows ended overlay`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val ended = fakeSpace.copy(
            state = XSpaceState.ENDED,
            audioStreamUrl = "https://stream.pscp.tv/replay.m3u8",
            recordingAvailable = true,
        )
        val monitor = LiveSpaceEndMonitor(
            metadataResolver = object : XSpaceMetadataResolver() {
                override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace = ended
            },
            maxRetries = 1,
            sleeper = {},
            maxReplayWaitAttempts = 1,
        )
        val events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
        val engine = object : PlaybackEngine {
            private val _state = MutableStateFlow(EngineState())
            override val state: StateFlow<EngineState> = _state.asStateFlow()
            override val events: Flow<PlaybackEvent> = events
            override fun load(mediaSource: String, startPositionMs: Long, autoPlay: Boolean) {
                _state.value = _state.value.copy(playbackState = EnginePlaybackState.PLAYING)
            }
            override fun play() {}
            override fun pause() {}
            override fun stop() {}
            override fun seekTo(positionMs: Long) {}
            override fun setSpeed(speed: Float) {}
            override fun setVolume(volume: Int) {}
            override fun setMute(muted: Boolean) {}
            override fun attachSurface(surface: Any?) {}
            override fun detachSurface() {}
            override fun release() {}
        }
        val service = PlayerService(engine, emptyProgressRepo())
        PlayerSessionHolder.setForTesting(service)
        val vm = PlayerViewModel(
            app = app,
            playerService = service,
            spaceRepository = fakeSpaceRepo,
            liveEndMonitor = monitor,
            downloadRepository = CountingDownloadRepository(),
        )
        val uri = "https://stream.pscp.tv/live.m3u8"
        vm.open(uri, "Space", isLive = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        delay(80)
        events.emit(PlaybackEvent.PlaybackFinished(uri, 0L))
        delay(250)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val endState = vm.uiState.value.liveEndState
        assertTrue(
            "endState=$endState",
            endState is LiveSpaceEndState.EndedReplayAvailable ||
                endState is LiveSpaceEndState.EndedDownloadStarted,
        )
        assertTrue(vm.uiState.value.isLiveSession)
        assertFalse(vm.uiState.value.isBroadcastLive)
    }

    @Test
    fun `downloadSpaceReplay does not enqueue twice for same spaceId`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val counting = CountingDownloadRepository()
        val engine = object : PlaybackEngine {
            private val _state = MutableStateFlow(EngineState())
            override val state: StateFlow<EngineState> = _state.asStateFlow()
            override val events: Flow<PlaybackEvent> = MutableSharedFlow()
            override fun load(mediaSource: String, startPositionMs: Long, autoPlay: Boolean) {
                _state.value = _state.value.copy(playbackState = EnginePlaybackState.PLAYING)
            }
            override fun play() {}
            override fun pause() {}
            override fun stop() {}
            override fun seekTo(positionMs: Long) {}
            override fun setSpeed(speed: Float) {}
            override fun setVolume(volume: Int) {}
            override fun setMute(muted: Boolean) {}
            override fun attachSurface(surface: Any?) {}
            override fun detachSurface() {}
            override fun release() {}
        }
        val service = PlayerService(engine, emptyProgressRepo())
        val vm = PlayerViewModel(
            app = app,
            playerService = service,
            spaceRepository = fakeSpaceRepo,
            downloadRepository = counting,
        )
        vm.open("https://stream.pscp.tv/live.m3u8", "Space", isLive = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        delay(80)
        vm.downloadSpaceReplay("https://stream.pscp.tv/replay.m3u8")
        delay(120)
        vm.downloadSpaceReplay("https://stream.pscp.tv/replay.m3u8")
        delay(120)
        assertEquals(1, counting.startCount)
        assertEquals(fakeSpace.host.avatarUrl, counting.lastRequest?.thumbnailUrl)
        assertTrue(counting.lastRequest?.sourceUrl?.contains(fakeSpace.id) == true)
    }

    @Test
    fun `two PlaybackFinished events verify once and do not auto-download when off`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        var hits = 0
        val ended = fakeSpace.copy(
            state = XSpaceState.ENDED,
            audioStreamUrl = "https://stream.pscp.tv/replay.m3u8",
            recordingAvailable = true,
        )
        val monitor = LiveSpaceEndMonitor(
            metadataResolver = object : XSpaceMetadataResolver() {
                override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace {
                    hits++
                    return ended
                }
            },
            maxRetries = 1,
            sleeper = {},
            maxReplayWaitAttempts = 1,
        )
        val events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
        val counting = CountingDownloadRepository()
        val pending = InMemoryPendingRepo()
        val service = PlayerService(finishedEngine(events), emptyProgressRepo())
        PlayerSessionHolder.setForTesting(service)
        val vm = PlayerViewModel(
            app = app,
            playerService = service,
            spaceRepository = fakeSpaceRepo,
            liveEndMonitor = monitor,
            pendingDownloadRepo = pending,
            downloadRepository = counting,
        )
        val uri = "https://stream.pscp.tv/live.m3u8"
        vm.open(uri, "Space", isLive = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        delay(80)
        events.emit(PlaybackEvent.PlaybackFinished(uri, 0L))
        events.emit(PlaybackEvent.PlaybackFinished(uri, 0L))
        delay(300)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(1, hits)
        assertEquals(0, counting.startCount)
        assertTrue(vm.uiState.value.liveEndState is LiveSpaceEndState.EndedReplayAvailable)
        assertFalse(vm.uiState.value.isBroadcastLive)
    }

    @Test
    fun `LIVE glitch keeps ActiveLive overlay`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val monitor = LiveSpaceEndMonitor(
            metadataResolver = object : XSpaceMetadataResolver() {
                override suspend fun resolve(spaceId: String, originalUrl: String, ytDlpJson: JSONObject?): XSpace =
                    fakeSpace
            },
            maxRetries = 1,
            sleeper = {},
        )
        val events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
        val counting = CountingDownloadRepository()
        val service = PlayerService(finishedEngine(events), emptyProgressRepo())
        val vm = PlayerViewModel(
            app = app,
            playerService = service,
            spaceRepository = fakeSpaceRepo,
            liveEndMonitor = monitor,
            pendingDownloadRepo = InMemoryPendingRepo(),
            downloadRepository = counting,
        )
        val uri = "https://stream.pscp.tv/live.m3u8"
        vm.open(uri, "Space", isLive = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        delay(80)
        events.emit(PlaybackEvent.PlaybackError(uri, "buffer", isFatal = false))
        delay(250)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue(vm.uiState.value.liveEndState is LiveSpaceEndState.ActiveLive)
        assertTrue(vm.uiState.value.isBroadcastLive)
        assertEquals(0, counting.startCount)
    }

    @Test
    fun `toggleAutoDownload persists on pending repo`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val pending = InMemoryPendingRepo()
        val vm = PlayerViewModel(
            app = app,
            playerService = PlayerSessionHolder.get(app),
            spaceRepository = fakeSpaceRepo,
            pendingDownloadRepo = pending,
            downloadRepository = CountingDownloadRepository(),
        )
        vm.open("https://stream.pscp.tv/live.m3u8", "Space", isLive = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        delay(80)
        assertFalse(pending.isAutoDownloadEnabled(fakeSpace.id))
        vm.toggleAutoDownload()
        delay(80)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(pending.isAutoDownloadEnabled(fakeSpace.id))
        assertEquals(PendingLiveDownloadStatus.WAITING_FOR_END, pending.getPendingDownload(fakeSpace.id)?.status)
        vm.toggleAutoDownload()
        delay(80)
        assertFalse(pending.isAutoDownloadEnabled(fakeSpace.id))
    }

    private fun finishedEngine(events: MutableSharedFlow<PlaybackEvent>) = object : PlaybackEngine {
        private val _state = MutableStateFlow(EngineState())
        override val state: StateFlow<EngineState> = _state.asStateFlow()
        override val events: Flow<PlaybackEvent> = events
        override fun load(mediaSource: String, startPositionMs: Long, autoPlay: Boolean) {
            _state.value = _state.value.copy(playbackState = EnginePlaybackState.PLAYING)
        }
        override fun play() {}
        override fun pause() {}
        override fun stop() {}
        override fun seekTo(positionMs: Long) {}
        override fun setSpeed(speed: Float) {}
        override fun setVolume(volume: Int) {}
        override fun setMute(muted: Boolean) {}
        override fun attachSurface(surface: Any?) {}
        override fun detachSurface() {}
        override fun release() {}
    }

    private fun emptyProgressRepo() = object : ProgressRepository {
        override suspend fun saveProgress(progress: PlaybackProgress) {}
        override suspend fun getProgress(mediaId: String): PlaybackProgress? = null
        override fun observeProgress(mediaId: String): Flow<PlaybackProgress?> = MutableStateFlow(null)
        override fun observeAllProgress(): Flow<Map<String, PlaybackProgress>> = MutableStateFlow(emptyMap())
        override suspend fun resetProgress(mediaId: String) {}
        override suspend fun markCompleted(mediaId: String, totalDurationMs: Long) {}
    }

    private class InMemoryPendingRepo : PendingLiveDownloadRepository {
        private val items = MutableStateFlow<List<PendingLiveDownload>>(emptyList())
        override fun observePendingDownloads(): Flow<List<PendingLiveDownload>> = items
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
