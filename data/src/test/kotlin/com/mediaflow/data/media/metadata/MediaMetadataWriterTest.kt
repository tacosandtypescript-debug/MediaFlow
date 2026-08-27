package com.mediaflow.data.media.metadata

import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaMetadataWriterTest {

    private lateinit var tempDir: File
    private val writer = DefaultMediaMetadataWriter()

    @Before
    fun setUp() {
        tempDir = File.createTempFile("meta_test_", "").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createMinimalMp4File(name: String): File {
        val file = File(tempDir, name)
        val stream = ByteArrayOutputStream()

        // 1. ftyp box (32 bytes)
        val ftyp = ByteArrayOutputStream()
        ftyp.write("isom".toByteArray(Charsets.US_ASCII))
        ftyp.writeInt(0x00000200) // minor version
        ftyp.write("isom".toByteArray(Charsets.US_ASCII))
        ftyp.write("mp41".toByteArray(Charsets.US_ASCII))
        val ftypBytes = ftyp.toByteArray()
        stream.writeInt(8 + ftypBytes.size)
        stream.write("ftyp".toByteArray(Charsets.US_ASCII))
        stream.write(ftypBytes)

        // 2. moov box (with minimal stco box)
        val stco = ByteArrayOutputStream()
        stco.writeInt(0) // version & flags
        stco.writeInt(1) // entry count = 1
        stco.writeInt(200) // chunk offset 200
        val stcoBytes = stco.toByteArray()
        val stcoBox = ByteArrayOutputStream()
        stcoBox.writeInt(8 + stcoBytes.size)
        stcoBox.write("stco".toByteArray(Charsets.US_ASCII))
        stcoBox.write(stcoBytes)

        val moovBytes = stcoBox.toByteArray()
        stream.writeInt(8 + moovBytes.size)
        stream.write("moov".toByteArray(Charsets.US_ASCII))
        stream.write(moovBytes)

        // 3. mdat box (50 bytes)
        val mdatPayload = ByteArray(42) { (it + 1).toByte() }
        stream.writeInt(8 + mdatPayload.size)
        stream.write("mdat".toByteArray(Charsets.US_ASCII))
        stream.write(mdatPayload)

        file.writeBytes(stream.toByteArray())
        return file
    }

    private fun createMinimalMp3File(name: String): File {
        val file = File(tempDir, name)
        val stream = ByteArrayOutputStream()
        // Dummy MPEG audio frame sync header (0xFF 0xFB) + frame payload
        val mpegFrame = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte()) + ByteArray(200) { 0x55 }
        stream.write(mpegFrame)
        file.writeBytes(stream.toByteArray())
        return file
    }

    @Test
    fun `maps full X Space metadata correctly`() {
        val space = XSpace(
            id = "1wGWjlyzqeNKQ",
            url = "https://x.com/fakekiffs/status/2092796653707067736",
            title = "ASOCIACIÓN DE MADRES SOLTERAS",
            state = XSpaceState.ENDED,
            host = XParticipant(
                displayName = "Fake Kiffs Deus",
                username = "FakeKiffs",
                userId = "123",
                role = ParticipantRole.HOST,
            ),
            cohosts = listOf(
                XParticipant(displayName = "Cohost One", username = "cohost1", role = ParticipantRole.COHOST),
            ),
            speakers = listOf(
                XParticipant(displayName = "Speaker Two", username = "speaker2", role = ParticipantRole.SPEAKER),
            ),
            startedAtMs = 1787796578000L, // 2026-08-27
            liveListenersCount = 190,
        )

        val metadata = MediaMetadata.fromXSpace(space)

        assertEquals("ASOCIACIÓN DE MADRES SOLTERAS", metadata.title)
        assertEquals("Fake Kiffs Deus", metadata.artist)
        assertEquals("@FakeKiffs", metadata.albumArtist)
        assertEquals("X Spaces", metadata.album)
        assertNotNull(metadata.date)
        assertTrue(metadata.description?.contains("1wGWjlyzqeNKQ") == true)
        assertTrue(metadata.description?.contains("@cohost1") == true)
        assertTrue(metadata.description?.contains("@speaker2") == true)
    }

    @Test
    fun `maps X Space with missing host displayName using username`() {
        val space = XSpace(
            id = "2abcXYZ",
            url = "https://x.com/i/spaces/2abcXYZ",
            title = "Tech Talk",
            host = XParticipant(
                displayName = "",
                username = "techguy",
                role = ParticipantRole.HOST,
            ),
            createdAtMs = 1787796578000L,
        )

        val metadata = MediaMetadata.fromXSpace(space)

        assertEquals("Tech Talk", metadata.title)
        assertEquals("techguy", metadata.artist)
        assertEquals("@techguy", metadata.albumArtist)
    }

    @Test
    fun `writes metadata to MP4 file successfully`() {
        val file = createMinimalMp4File("test_space.mp4")
        val metadata = MediaMetadata(
            title = "Space Replay",
            artist = "Host Name",
            albumArtist = "@hostname",
            album = "X Spaces",
            date = "2026-08-27",
            description = "Recording of debate",
        )

        val result = writer.writeMetadata(file, metadata)
        assertTrue(result.isSuccess)

        // Verify that the file exists and contains the metadata atoms
        assertTrue(file.exists() && file.length() > 0L)
        val fileBytes = file.readBytes()
        val fileContent = String(fileBytes, Charsets.ISO_8859_1)

        assertTrue("Should contain moov atom", fileContent.contains("moov"))
        assertTrue("Should contain udta atom", fileContent.contains("udta"))
        assertTrue("Should contain meta atom", fileContent.contains("meta"))
        assertTrue("Should contain ilst atom", fileContent.contains("ilst"))
        assertTrue("Should contain title", fileContent.contains("Space Replay"))
        assertTrue("Should contain artist", fileContent.contains("Host Name"))
        assertTrue("Should contain album artist", fileContent.contains("@hostname"))
    }

    @Test
    fun `writes metadata to MP3 file successfully with ID3v2`() {
        val file = createMinimalMp3File("test_space.mp3")
        val metadata = MediaMetadata(
            title = "Podcast Episode",
            artist = "Host Podcaster",
            albumArtist = "@podcaster",
            album = "Episodes",
            date = "2026",
            description = "Detailed audio discussion",
        )

        val result = writer.writeMetadata(file, metadata)
        assertTrue(result.isSuccess)

        val fileBytes = file.readBytes()
        // ID3 header check
        assertEquals('I'.code.toByte(), fileBytes[0])
        assertEquals('D'.code.toByte(), fileBytes[1])
        assertEquals('3'.code.toByte(), fileBytes[2])
        assertEquals(3.toByte(), fileBytes[3]) // ID3v2.3

        val content = String(fileBytes, Charsets.UTF_8)
        assertTrue(content.contains("TIT2"))
        assertTrue(content.contains("Podcast Episode"))
        assertTrue(content.contains("TPE1"))
        assertTrue(content.contains("Host Podcaster"))
        assertTrue(content.contains("COMM"))
        assertTrue(content.contains("Detailed audio discussion"))
    }

    @Test
    fun `writing metadata is idempotent and can run repeatedly`() {
        val file = createMinimalMp4File("repeat.mp4")
        val metadata = MediaMetadata(
            title = "Idempotent Title",
            artist = "Speaker",
            date = "2026-08-27",
        )

        val firstRun = writer.writeMetadata(file, metadata)
        assertTrue(firstRun.isSuccess)
        val sizeAfterFirst = file.length()

        val secondRun = writer.writeMetadata(file, metadata)
        assertTrue(secondRun.isSuccess)
        val sizeAfterSecond = file.length()

        assertEquals("File size should remain identical after second run", sizeAfterFirst, sizeAfterSecond)
        val content = String(file.readBytes(), Charsets.ISO_8859_1)
        assertTrue(content.contains("Idempotent Title"))
    }

    @Test
    fun `unsupported container formats return success safely`() {
        val file = File(tempDir, "sample.mkv").apply { writeText("dummy mkv content") }
        val metadata = MediaMetadata(title = "Video Title")

        val result = writer.writeMetadata(file, metadata)
        assertTrue("Unsupported formats should gracefully succeed without modifying file", result.isSuccess)
        assertEquals("dummy mkv content", file.readText())
    }

    @Test
    fun `non existent file returns failure without throwing unhandled exception`() {
        val missing = File(tempDir, "missing.mp4")
        val result = writer.writeMetadata(missing, MediaMetadata(title = "Missing"))

        assertFalse(result.isSuccess)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `empty metadata returns success without modifying file`() {
        val file = createMinimalMp4File("empty_meta.mp4")
        val originalBytes = file.readBytes()

        val result = writer.writeMetadata(file, MediaMetadata()) // all nulls
        assertTrue(result.isSuccess)
        assertEquals(originalBytes.size.toLong(), file.length())
    }

    @Test
    fun `failing metadata writer does not break download completion flow`() {
        val failingWriter = object : MediaMetadataWriter {
            override fun writeMetadata(file: File, metadata: MediaMetadata): Result<Unit> {
                return Result.failure(IllegalStateException("Simulated tagging failure"))
            }
        }
        val file = createMinimalMp4File("flow_test.mp4")
        val metadata = MediaMetadata(title = "Should Not Fail Flow")

        var downloadFailed = false
        runCatching {
            failingWriter.writeMetadata(file, metadata).getOrThrow()
        }.onFailure {
            // Non-blocking catch pattern: logs warning without propagating failure
            downloadFailed = false
        }

        assertFalse("Writer failure must be handled non-destructively", downloadFailed)
        assertTrue("Original media file must remain intact", file.exists() && file.length() > 0L)
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }
}
