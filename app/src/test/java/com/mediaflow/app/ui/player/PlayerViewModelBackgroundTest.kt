package com.mediaflow.app.ui.player

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.player.background.PlayerSessionHolder
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.EngineState
import com.mediaflow.domain.player.PlaybackEngine
import com.mediaflow.domain.player.PlaybackEvent
import com.mediaflow.domain.player.PlayerService
import com.mediaflow.domain.repository.ProgressRepository
import com.mediaflow.domain.repository.XSpaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
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
        host = XParticipant(displayName = "Elon", username = "elon", userId = "1", role = ParticipantRole.HOST),
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
}
