package com.mediaflow.app.ui.home

import com.mediaflow.app.R
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.SourceInfo
import com.mediaflow.domain.repository.SourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = HomeViewModel()

    @Test
    fun `initial state has empty url and disabled button`() {
        val vm = newViewModel()
        val s = vm.uiState.value
        assertEquals("", s.url)
        assertEquals(ContentType.VIDEO, s.mediaType)
        assertEquals(listOf(QualityOption.AUTO), s.qualityOptions)
        assertEquals(QualityOption.AUTO, s.quality)
        assertEquals("", s.fileName)
        assertEquals(ValidationState.Empty, s.validationState)
        assertFalse(s.isDownloadButtonEnabled)
        assertNull(s.errorMessage)
        assertNull(s.infoMessage)
    }

    @Test
    fun `empty url shows empty message and disables button`() {
        val vm = newViewModel()
        vm.onUrlChanged("   ")
        val s = vm.uiState.value
        assertEquals(ValidationState.Empty, s.validationState)
        assertFalse(s.isDownloadButtonEnabled)
        assertEquals(R.string.home_error_empty, s.errorMessage)
    }

    @Test
    fun `non URL text is rejected`() {
        val vm = newViewModel()
        vm.onUrlChanged("hola mundo")
        val s = vm.uiState.value
        assertEquals(ValidationState.Invalid, s.validationState)
        assertFalse(s.isDownloadButtonEnabled)
        assertEquals(R.string.home_error_invalid, s.errorMessage)
        assertNull(s.infoMessage)
    }

    @Test
    fun `http url is rejected with secure message`() {
        val vm = newViewModel()
        vm.onUrlChanged("http://example.com/video")
        val s = vm.uiState.value
        assertEquals(ValidationState.NotHttps, s.validationState)
        assertFalse(s.isDownloadButtonEnabled)
        assertEquals(R.string.home_error_http, s.errorMessage)
    }

    @Test
    fun `same url does not reset a completed analysis`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "Clip",
                availableFormats = listOf(
                    MediaFormat("22", "mp4", "video/mp4", MediaType.VIDEO, "720p", height = 720),
                ),
            )
        })
        assertEquals(AnalysisState.READY, vm.uiState.value.analysisState)

        vm.onUrlChanged("https://example.com/video")
        vm.onUrlChanged("  https://example.com/video  ")

        assertEquals(AnalysisState.READY, vm.uiState.value.analysisState)
        assertEquals("Clip", vm.uiState.value.sourceInfo?.title)
        assertEquals("22", vm.uiState.value.selectedFormatId)
    }

    @Test
    fun `https url is accepted`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/watch?v=abc")
        val s = vm.uiState.value
        assertEquals(ValidationState.Valid, s.validationState)
        assertTrue(s.isDownloadButtonEnabled)
        assertNull(s.errorMessage)
        assertEquals(R.string.home_info_valid, s.infoMessage)
    }

    @Test
    fun `YouTube Music share url is accepted as https`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://music.youtube.com/watch?v=jf6tbohQG_E&si=P9Ig0EuVhBe2tRO7")
        val s = vm.uiState.value
        assertEquals(ValidationState.Valid, s.validationState)
        assertTrue(s.isDownloadButtonEnabled)
        assertNull(s.errorMessage)
    }

    @Test
    fun `https without host is rejected as invalid`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://")
        vm.onUrlChanged("https://soloespacios   ")
        val s = vm.uiState.value
        assertEquals(ValidationState.Invalid, s.validationState)
        assertFalse(s.isDownloadButtonEnabled)
    }

    @Test
    fun `switching to audio updates media type and quality options`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/audio")
        vm.onMediaTypeSelected(ContentType.AUDIO)
        val s = vm.uiState.value
        assertEquals(ContentType.AUDIO, s.mediaType)
        assertEquals(QualityOption.audioOptions, s.qualityOptions)
        assertEquals(QualityOption.AUTO, s.quality)
        // Switching type keeps a valid URL valid.
        assertTrue(s.isDownloadButtonEnabled)
    }

    @Test
    fun `switching back to video restores video quality options`() {
        val vm = newViewModel()
        vm.onMediaTypeSelected(ContentType.AUDIO)
        vm.onMediaTypeSelected(ContentType.VIDEO)
        val s = vm.uiState.value
        assertEquals(listOf(QualityOption.AUTO), s.qualityOptions)
        assertEquals(QualityOption.AUTO, s.quality)
    }

    @Test
    fun `quality selection updates state after real 720p analysis`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                availableFormats = listOf(
                    MediaFormat("22", "mp4", "video/mp4", MediaType.VIDEO, "720p", height = 720),
                ),
            )
        })
        vm.onQualitySelected(QualityOption.P720)
        assertEquals(QualityOption.P720, vm.uiState.value.quality)
        assertFalse(QualityOption.P480 in vm.uiState.value.qualityOptions)
        assertFalse(QualityOption.P1080 in vm.uiState.value.qualityOptions)
    }

    @Test
    fun `quality invalid for current type is ignored`() {
        val vm = newViewModel()
        vm.onMediaTypeSelected(ContentType.AUDIO)
        vm.onQualitySelected(QualityOption.P1080) // not valid for audio
        assertEquals(QualityOption.AUTO, vm.uiState.value.quality)
    }

    @Test
    fun `file name is sanitized`() {
        val vm = newViewModel()
        vm.onFileNameChanged("Mi/Vídeo:1?")
        assertEquals("MiVídeo1", vm.uiState.value.fileName)
    }

    @Test
    fun `empty file name stays empty (auto name)`() {
        val vm = newViewModel()
        vm.onFileNameChanged("mi video")
        vm.onFileNameChanged("")
        assertEquals("", vm.uiState.value.fileName)
    }

    @Test
    fun `button stays disabled without valid https url`() {
        val vm = newViewModel()
        listOf("", "texto", "http://x.com/y", "https://").forEach {
            vm.onUrlChanged(it)
            assertFalse("should be disabled for input: $it", vm.uiState.value.isDownloadButtonEnabled)
        }
    }

    @Test
    fun `button enabled with valid https url`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://www.example.com/videos/1")
        assertTrue(vm.uiState.value.isDownloadButtonEnabled)
    }

    @Test
    fun `download now does not create any download`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        val before = vm.uiState.value
        vm.onDownloadNow()
        val s = vm.uiState.value
        // No fake download state is introduced; nothing new is created.
        assertEquals(before.url, s.url)
        assertEquals(before.mediaType, s.mediaType)
        assertEquals(before.quality, s.quality)
        assertEquals(before.fileName, s.fileName)
        assertNull(s.errorMessage)
        assertTrue(s.isDownloadButtonEnabled)
    }

    @Test
    fun `download now with no valid url does nothing`() {
        val vm = newViewModel()
        vm.onDownloadNow()
        val s = vm.uiState.value
        assertNull(s.infoMessage)
        assertFalse(s.isDownloadButtonEnabled)
    }

    @Test
    fun `clear url returns to empty state`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        assertTrue(vm.uiState.value.isDownloadButtonEnabled)
        vm.onClearUrl()
        val s = vm.uiState.value
        assertEquals("", s.url)
        assertEquals(ValidationState.Empty, s.validationState)
        assertEquals(R.string.home_error_empty, s.errorMessage)
        assertFalse(s.isDownloadButtonEnabled)
    }

    @Test
    fun `real analysis exposes only matching formats and selected id`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        val resolver = object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "Legal sample",
                durationSeconds = 42,
                availableFormats = listOf(
                    MediaFormat("137", "mp4", "video/mp4", MediaType.VIDEO, "1080p", height = 1080, videoCodec = "avc1", requiresMuxing = true),
                    MediaFormat("140", "m4a", "audio/mp4", MediaType.AUDIO, "128k", bitrate = 128_000L, audioCodec = "mp4a"),
                ),
            )
        }

        vm.analyze(resolver)

        val state = vm.uiState.value
        assertEquals(AnalysisState.READY, state.analysisState)
        assertEquals("Legal sample", state.sourceInfo?.title)
        assertEquals(1, state.availableFormats.size)
        assertEquals("137", state.selectedFormatId)
        assertTrue(state.isDownloadButtonEnabled)
        vm.onFormatSelected("137")
        assertEquals("137", vm.uiState.value.selectedFormatId)
    }

    @Test
    fun `auto analysis prefers muxable avc over vp9 at the same height`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "YouTube sample",
                availableFormats = listOf(
                    MediaFormat(
                        "248", "webm", "video/webm", MediaType.VIDEO, "1080p",
                        height = 1080, videoCodec = "vp9", requiresMuxing = true,
                    ),
                    MediaFormat(
                        "137", "mp4", "video/mp4", MediaType.VIDEO, "1080p",
                        height = 1080, videoCodec = "avc1", requiresMuxing = true,
                    ),
                ),
            )
        })

        assertEquals("137", vm.uiState.value.selectedFormatId)
    }

    @Test
    fun `auto analysis prefers progressive over higher mux-only rungs`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "YouTube sample",
                availableFormats = listOf(
                    MediaFormat(
                        "137", "mp4", "video/mp4", MediaType.VIDEO, "1080p",
                        height = 1080, videoCodec = "avc1", requiresMuxing = true,
                    ),
                    MediaFormat(
                        "22", "mp4", "video/mp4", MediaType.VIDEO, "720p",
                        height = 720, videoCodec = "avc1", audioCodec = "mp4a.40.2",
                        isProgressive = true, requiresMuxing = false,
                    ),
                ),
            )
        })

        assertEquals("22", vm.uiState.value.selectedFormatId)
    }

    @Test
    fun `auto analysis prefers progressive when highest formats are not muxable`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "YouTube sample",
                availableFormats = listOf(
                    MediaFormat(
                        "248", "webm", "video/webm", MediaType.VIDEO, "1080p",
                        height = 1080, videoCodec = "vp9", requiresMuxing = true,
                    ),
                    MediaFormat(
                        "22", "mp4", "video/mp4", MediaType.VIDEO, "720p",
                        height = 720, videoCodec = "avc1", audioCodec = "mp4a.40.2",
                        isProgressive = true, requiresMuxing = false,
                    ),
                ),
            )
        })

        assertEquals("22", vm.uiState.value.selectedFormatId)
    }

    @Test
    fun `quality option selects format by height`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "YouTube sample",
                availableFormats = listOf(
                    MediaFormat(
                        "137", "mp4", "video/mp4", MediaType.VIDEO, "1080p",
                        height = 1080, videoCodec = "avc1", requiresMuxing = true,
                    ),
                    MediaFormat(
                        "22", "mp4", "video/mp4", MediaType.VIDEO, "720p",
                        height = 720, videoCodec = "avc1", audioCodec = "mp4a.40.2",
                        isProgressive = true, requiresMuxing = false,
                    ),
                    MediaFormat(
                        "18", "mp4", "video/mp4", MediaType.VIDEO, "360p",
                        height = 360, videoCodec = "avc1", audioCodec = "mp4a.40.2",
                        isProgressive = true, requiresMuxing = false,
                    ),
                ),
            )
        })

        vm.onQualitySelected(QualityOption.P360)
        assertEquals("18", vm.uiState.value.selectedFormatId)
        vm.onQualitySelected(QualityOption.P720)
        assertEquals("22", vm.uiState.value.selectedFormatId)
        vm.onQualitySelected(QualityOption.P1080)
        assertEquals("137", vm.uiState.value.selectedFormatId)
    }

    @Test
    fun `manual format id is kept when the user picks a specific format`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "YouTube sample",
                availableFormats = listOf(
                    MediaFormat(
                        "137", "mp4", "video/mp4", MediaType.VIDEO, "1080p",
                        height = 1080, videoCodec = "avc1", requiresMuxing = true,
                    ),
                    MediaFormat(
                        "248", "webm", "video/webm", MediaType.VIDEO, "1080p",
                        height = 1080, videoCodec = "vp9", requiresMuxing = true,
                    ),
                ),
            )
        })

        vm.onFormatSelected("248")
        assertEquals("248", vm.uiState.value.selectedFormatId)
        assertEquals("248", PreferredDownloadFormat.select(vm.uiState.value.availableFormats, QualityOption.AUTO, "248")?.formatId)
    }

    @Test
    fun `analysis with no matching formats disables download`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/audio")
        vm.onMediaTypeSelected(ContentType.VIDEO)
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "Audio only",
                availableFormats = listOf(
                    MediaFormat("140", "m4a", "audio/mp4", MediaType.AUDIO, "128k", audioCodec = "mp4a"),
                ),
            )
        })

        assertEquals(AnalysisState.FAILED, vm.uiState.value.analysisState)
        assertFalse(vm.uiState.value.isDownloadButtonEnabled)
        assertTrue(vm.uiState.value.availableFormats.isEmpty())

        vm.onMediaTypeSelected(ContentType.AUDIO)
        assertEquals(AnalysisState.READY, vm.uiState.value.analysisState)
        assertTrue(vm.uiState.value.isDownloadButtonEnabled)
        assertEquals("140", vm.uiState.value.selectedFormatId)
        assertEquals(ContentType.AUDIO, vm.uiState.value.mediaType)
    }

    @Test
    fun `switching type after analysis refilters without clearing source`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "Clip mixto",
                availableFormats = listOf(
                    MediaFormat("137", "mp4", "video/mp4", MediaType.VIDEO, "1080p", height = 1080, videoCodec = "avc1"),
                    MediaFormat("140", "m4a", "audio/mp4", MediaType.AUDIO, "128k", audioCodec = "mp4a"),
                ),
            )
        })

        assertEquals("137", vm.uiState.value.selectedFormatId)
        assertEquals("Clip mixto", vm.uiState.value.sourceInfo?.title)

        vm.onMediaTypeSelected(ContentType.AUDIO)
        assertEquals(AnalysisState.READY, vm.uiState.value.analysisState)
        assertEquals("Clip mixto", vm.uiState.value.sourceInfo?.title)
        assertEquals(ContentType.AUDIO, vm.uiState.value.mediaType)
        assertEquals(1, vm.uiState.value.availableFormats.size)
        assertEquals("140", vm.uiState.value.selectedFormatId)
        assertTrue(vm.uiState.value.isDownloadButtonEnabled)

        vm.onMediaTypeSelected(ContentType.VIDEO)
        assertEquals(ContentType.VIDEO, vm.uiState.value.mediaType)
        assertEquals("137", vm.uiState.value.selectedFormatId)
        assertEquals(AnalysisState.READY, vm.uiState.value.analysisState)
    }

    @Test
    fun `switching to audio synthesizes bestaudio when source is video only`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://example.com/video")
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "Solo vídeo",
                availableFormats = listOf(
                    MediaFormat(
                        "anonymous", "mp4", "video/mp4", MediaType.VIDEO, "Automática",
                        isProgressive = true,
                    ),
                ),
            )
        })

        vm.onMediaTypeSelected(ContentType.AUDIO)
        assertEquals(AnalysisState.READY, vm.uiState.value.analysisState)
        assertEquals(HomeViewModel.SYNTHETIC_AUDIO_FORMAT_ID, vm.uiState.value.selectedFormatId)
        assertEquals(MediaType.AUDIO, vm.uiState.value.availableFormats.single().mediaType)
        assertTrue(vm.uiState.value.isDownloadButtonEnabled)
    }

    @Test
    fun `YouTube Music links default to audio after analysis`() {
        val vm = newViewModel()
        vm.onUrlChanged("https://music.youtube.com/watch?v=jf6tbohQG_E&si=P9Ig0EuVhBe2tRO7")
        vm.analyze(object : SourceResolver {
            override suspend fun analyze(sourceUrl: String) = SourceInfo(
                sourceUrl = sourceUrl,
                title = "Cancion de prueba",
                availableFormats = listOf(
                    MediaFormat("18", "mp4", "video/mp4", MediaType.VIDEO, "360p", height = 360, videoCodec = "avc1", audioCodec = "mp4a"),
                    MediaFormat("140", "m4a", "audio/mp4", MediaType.AUDIO, "128k", audioCodec = "mp4a"),
                ),
            )
        })

        assertEquals(ContentType.AUDIO, vm.uiState.value.mediaType)
        assertEquals("140", vm.uiState.value.selectedFormatId)
        assertTrue(vm.uiState.value.isDownloadButtonEnabled)
        assertEquals(AnalysisState.READY, vm.uiState.value.analysisState)

        vm.onMediaTypeSelected(ContentType.VIDEO)
        assertEquals(ContentType.VIDEO, vm.uiState.value.mediaType)
        assertEquals("18", vm.uiState.value.selectedFormatId)
    }
}
