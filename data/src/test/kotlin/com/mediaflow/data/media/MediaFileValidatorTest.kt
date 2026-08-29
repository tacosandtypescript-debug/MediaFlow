package com.mediaflow.data.media

import android.media.MediaFormat
import com.mediaflow.core.model.MediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaFileValidatorTest {

    private lateinit var tempDir: File
    private val originalFactory = MediaFileValidator.extractorFactory

    @Before
    fun setUp() {
        tempDir = File.createTempFile("validator_test", "").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        MediaFileValidator.extractorFactory = originalFactory
        tempDir.deleteRecursively()
    }

    private fun createDummyFile(name: String, sizeBytes: Long = 1024L): File {
        val file = File(tempDir, name)
        file.writeBytes(ByteArray(sizeBytes.toInt()) { 1 })
        return file
    }

    private fun mockExtractor(
        tracks: List<MediaFormat>,
    ) {
        MediaFileValidator.extractorFactory = { _ ->
            object : MediaExtractorAdapter {
                override val trackCount: Int = tracks.size
                override fun getTrackFormat(index: Int): MediaFormat = tracks[index]
                override fun release() {}
            }
        }
    }

    @Test
    fun `validate identical duration succeeds and adopts duration`() {
        val file = createDummyFile("stream.mp4")
        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2).apply {
            setLong(MediaFormat.KEY_DURATION, 5413_000_000L) // 5413 seconds in us
        }
        mockExtractor(listOf(audioFormat))

        val result = MediaFileValidator.validate(
            file = file,
            expectedType = MediaType.AUDIO,
            expectedExtension = "mp4",
            expectedDurationSeconds = 5413L,
        )

        assertTrue(result.isSuccess)
        assertEquals(5413L, result.getOrThrow().durationSeconds)
    }

    @Test
    fun `validate small duration difference succeeds and returns authoritative duration`() {
        val file = createDummyFile("stream.mp4")
        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2).apply {
            setLong(MediaFormat.KEY_DURATION, 105_000_000L) // 105 seconds in us
        }
        mockExtractor(listOf(audioFormat))

        val result = MediaFileValidator.validate(
            file = file,
            expectedType = MediaType.AUDIO,
            expectedExtension = "mp4",
            expectedDurationSeconds = 100L,
        )

        assertTrue(result.isSuccess)
        assertEquals(105L, result.getOrThrow().durationSeconds)
    }

    @Test
    fun `validate large duration difference with valid file succeeds and adopts real duration`() {
        // Real X Space case: expected from metadata = 5413s, real HLS container = 6034s (delta = 621s)
        val file = createDummyFile("fakekiffs_space.mp4")
        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2).apply {
            setLong(MediaFormat.KEY_DURATION, 6034_000_000L) // 6034 seconds in us
        }
        mockExtractor(listOf(audioFormat))

        val result = MediaFileValidator.validate(
            file = file,
            expectedType = MediaType.AUDIO,
            expectedExtension = "mp4",
            expectedDurationSeconds = 5413L,
        )

        assertTrue("Large duration discrepancy in valid stream must not fail", result.isSuccess)
        val validated = result.getOrThrow()
        assertEquals(6034L, validated.durationSeconds)
        assertEquals(file.length(), validated.sizeBytes)
    }

    @Test
    fun `validate X Space HLS container audio format`() {
        val file = createDummyFile("space_replay.mp4")
        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 48000, 2).apply {
            setLong(MediaFormat.KEY_DURATION, 3600_000_000L)
        }
        mockExtractor(listOf(audioFormat))

        val result = MediaFileValidator.validate(
            file = file,
            expectedType = MediaType.AUDIO,
            expectedExtension = "mp4",
            expectedDurationSeconds = 3500L,
        )

        assertTrue(result.isSuccess)
        assertEquals(3600L, result.getOrThrow().durationSeconds)
    }

    @Test
    fun `validate incorrect duration metadata in request adopts file duration`() {
        val file = createDummyFile("podcast.mp3")
        val audioFormat = MediaFormat.createAudioFormat("audio/mpeg", 44100, 2).apply {
            setLong(MediaFormat.KEY_DURATION, 120_000_000L) // 120 seconds in us
        }
        mockExtractor(listOf(audioFormat))

        val result = MediaFileValidator.validate(
            file = file,
            expectedType = MediaType.AUDIO,
            expectedExtension = "mp3",
            expectedDurationSeconds = 9999L, // wrong metadata
        )

        assertTrue(result.isSuccess)
        assertEquals(120L, result.getOrThrow().durationSeconds)
    }

    @Test
    fun `validate non existent file fails`() {
        val nonExistent = File(tempDir, "missing.mp4")
        val result = MediaFileValidator.validate(
            file = nonExistent,
            expectedType = MediaType.VIDEO,
            expectedExtension = "mp4",
        )

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("está vacío o no existe") == true)
    }

    @Test
    fun `validate zero byte file fails`() {
        val emptyFile = File(tempDir, "empty.mp4").apply { createNewFile() }
        val result = MediaFileValidator.validate(
            file = emptyFile,
            expectedType = MediaType.VIDEO,
            expectedExtension = "mp4",
        )

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("está vacío o no existe") == true)
    }

    @Test
    fun `validate corrupt container with zero tracks fails`() {
        val file = createDummyFile("corrupt.mp4")
        mockExtractor(emptyList()) // 0 tracks

        val result = MediaFileValidator.validate(
            file = file,
            expectedType = MediaType.VIDEO,
            expectedExtension = "mp4",
        )

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("no contiene pistas multimedia o está corrupto") == true)
    }

    @Test
    fun `validate missing expected video track fails`() {
        val file = createDummyFile("audio_only.mp4")
        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2)
        mockExtractor(listOf(audioFormat))

        val result = MediaFileValidator.validate(
            file = file,
            expectedType = MediaType.VIDEO, // requires video track
            expectedExtension = "mp4",
        )

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("no contiene una pista video válida") == true)
    }

    @Test
    fun `validate playable file succeeds when extension differs from expected`() {
        val file = createDummyFile("stream.webm")
        val videoFormat = MediaFormat.createVideoFormat("video/avc", 1920, 1080).apply {
            setLong(MediaFormat.KEY_DURATION, 12_000_000L)
        }
        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2)
        mockExtractor(listOf(videoFormat, audioFormat))

        val result = MediaFileValidator.validate(
            file = file,
            expectedType = MediaType.VIDEO,
            expectedExtension = "mp4",
            expectedDurationSeconds = 12L,
            expectedWidth = 1920,
            expectedHeight = 1080,
        )

        assertTrue("A playable webm must not fail solely because mp4 was expected", result.isSuccess)
        assertEquals(12L, result.getOrThrow().durationSeconds)
    }

    @Test
    fun `validate avc1 profile string matches video avc mime`() {
        val file = createDummyFile("clip.mp4")
        val videoFormat = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        mockExtractor(listOf(videoFormat))

        val result = MediaFileValidator.validate(
            file = file,
            expectedType = MediaType.VIDEO,
            expectedExtension = "mp4",
            expectedVideoCodec = "avc1.640028",
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `validate unknown expected codec does not fail a playable file`() {
        val file = createDummyFile("clip.mp4")
        val videoFormat = MediaFormat.createVideoFormat("video/avc", 640, 360)
        mockExtractor(listOf(videoFormat))

        val result = MediaFileValidator.validate(
            file = file,
            expectedType = MediaType.VIDEO,
            expectedExtension = "mp4",
            expectedVideoCodec = "mystery-codec",
        )

        assertTrue(result.isSuccess)
    }
}
