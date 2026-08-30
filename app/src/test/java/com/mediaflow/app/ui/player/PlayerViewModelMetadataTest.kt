package com.mediaflow.app.ui.player

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.XSpace
import com.mediaflow.data.media.metadata.EmbeddedTrackTags
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlayerViewModelMetadataTest {

    @Test
    fun `open uses embedded title not numeric MediaStore id`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val uri = "content://media/external/audio/media/20567"
        val vm = PlayerViewModel(
            app = app,
            playerService = PlayerService(idleEngine(), emptyProgressRepo()),
            spaceRepository = emptySpaceRepo(),
            downloadRepository = FakeDownloads(
                DownloadItem(
                    id = "20567",
                    sourceUrl = uri,
                    title = "20567",
                    fileName = "20567.m4a",
                    mediaType = MediaType.AUDIO,
                    localUri = uri,
                ),
            ),
            readEmbeddedMetadata = {
                EmbeddedTrackTags(
                    title = "Turn Down for What",
                    artist = "DJ Snake",
                    album = "Recess",
                    durationMs = 232_000L,
                    artworkUri = "file:///data/user/0/com.mediaflow.app/files/embedded_art/cover.jpg",
                )
            },
        )

        vm.open(uri)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        delay(80)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val state = vm.uiState.value
        assertEquals("Turn Down for What", state.title)
        assertFalse(state.title.all { it.isDigit() })
        assertEquals("DJ Snake", state.artist)
        assertEquals("Recess", state.album)
        assertEquals(232_000L, state.durationMs)
        assertTrue(state.artworkUri.orEmpty().endsWith(".jpg"))
    }

    private fun idleEngine() = object : PlaybackEngine {
        private val _state = MutableStateFlow(EngineState())
        override val state: StateFlow<EngineState> = _state.asStateFlow()
        override val events: Flow<PlaybackEvent> = MutableSharedFlow()
        override fun load(mediaSource: String, startPositionMs: Long, autoPlay: Boolean) {
            _state.value = _state.value.copy(
                playbackState = EnginePlaybackState.PAUSED,
                durationMs = 0L,
            )
        }
        override fun play() {
            _state.value = _state.value.copy(playbackState = EnginePlaybackState.PLAYING)
        }
        override fun pause() {
            _state.value = _state.value.copy(playbackState = EnginePlaybackState.PAUSED)
        }
        override fun stop() {}
        override fun seekTo(positionMs: Long) {
            _state.value = _state.value.copy(currentPositionMs = positionMs)
        }
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

    private fun emptySpaceRepo() = object : XSpaceRepository {
        override suspend fun saveSpace(space: XSpace, mediaId: String?) {}
        override suspend fun getSpace(spaceId: String): XSpace? = null
        override suspend fun getSpaceForMedia(mediaId: String): XSpace? = null
        override fun observeAllSpaces(): Flow<Map<String, XSpace>> = MutableStateFlow(emptyMap())
        override fun observeSpaceForMedia(mediaId: String): Flow<XSpace?> = MutableStateFlow(null)
    }

    private class FakeDownloads(vararg items: DownloadItem) : DownloadRepository {
        private val list = MutableStateFlow(items.toList())
        override fun observeDownloads(): Flow<List<DownloadItem>> = list
        override suspend fun getDownloadById(id: String): DownloadItem? = list.value.firstOrNull { it.id == id }
        override suspend fun startDownload(request: DownloadRequest): String = "dl"
        override suspend fun pauseDownload(id: String) {}
        override suspend fun resumeDownload(id: String) {}
        override suspend fun cancelDownload(id: String) {}
        override suspend fun retryDownload(id: String) {}
        override suspend fun removeDownload(id: String) {}
    }
}
