package com.mediaflow.data.provider.x.recording

import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.dvr.DvrWindowMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)

class SpaceRecorderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun recorder(libraryFile: java.io.File = temp.newFile("library.json")): SpaceRecorder {
        val dir = temp.newFolder()
        return SpaceRecorder(
            workDir = dir,
            library = RecordedSpaceLibrary(libraryFile),
            window = DvrWindowMinutes.FIVE,
            spaceId = "space_abc",
            originalUrl = "https://x.com/i/spaces/space_abc",
        )
    }

    @Test
    fun `record off does not emit persisted library item`() {
        val libFile = temp.newFile("library-off.json")
        val rec = recorder(libFile)
        rec.acceptRecorderTick(ByteArray(8) { 1 })
        rec.acceptRecorderTick(ByteArray(8) { 2 })
        val saved = rec.onLiveEnded(XSpaceState.ENDED)
        assertNull(saved)
        assertEquals(RecordingPhase.OFF, rec.phase)
        assertTrue(RecordedSpaceLibrary(libFile).items().isEmpty())
    }

    @Test
    fun `record on plus pause playback still accepts recorder ticks`() {
        val rec = recorder()
        rec.setRecordEnabled(true)
        rec.acceptRecorderTick(ByteArray(4) { 1 })
        rec.playbackPaused = true
        rec.acceptRecorderTick(ByteArray(4) { 2 })
        rec.acceptRecorderTick(ByteArray(4) { 3 })
        assertEquals(RecordingPhase.RECORDING, rec.phase)
        assertEquals(12L, rec.elapsedMs)
        assertTrue(rec.playbackPaused)
    }

    @Test
    fun `mark stores relative timestamps`() {
        val rec = recorder()
        rec.setRecordEnabled(true)
        rec.acceptRecorderTick(ByteArray(10))
        val first = rec.mark("clip")
        rec.acceptRecorderTick(ByteArray(5))
        val second = rec.mark()
        assertEquals(10L, first.relativeTimestampMs)
        assertEquals(15L, second.relativeTimestampMs)
        assertEquals("clip", first.label)
    }

    @Test
    fun `checkpoint finalize recoverable partial then saved with space id and url`() {
        val work = temp.newFolder("session-work")
        val libFile = temp.newFile("lib2.json")
        val library = RecordedSpaceLibrary(libFile)
        val rec = SpaceRecorder(
            workDir = work,
            library = library,
            spaceId = "space_abc",
            originalUrl = "https://x.com/i/spaces/space_abc",
        )
        rec.setRecordEnabled(true)
        rec.acceptRecorderTick(byteArrayOf(9, 8, 7, 6))
        rec.mark("m1")

        val recovered = SpaceRecorder(
            workDir = work,
            library = library,
            spaceId = "space_abc",
            originalUrl = "https://x.com/i/spaces/space_abc",
        ).recoverPartialThenFinalize()

        requireNotNull(recovered)
        assertEquals("space_abc", recovered.spaceId)
        assertEquals("https://x.com/i/spaces/space_abc", recovered.originalUrl)
        assertEquals(RecordingPhase.SAVED, rec.recoverPhaseAfter(library))
        assertEquals(1, library.items().size)
        assertEquals("space_abc", library.items().single().spaceId)
        assertEquals("https://x.com/i/spaces/space_abc", library.items().single().originalUrl)
        assertEquals(1, recovered.markers.size)
        assertEquals(4L, recovered.markers.single().relativeTimestampMs)
    }

    @Test
    fun `reconnect backoff bounded`() {
        val backoff = ReconnectBackoff(listOf(1L, 2L, 4L))
        assertEquals(1L, backoff.nextDelayMs())
        assertEquals(2L, backoff.nextDelayMs())
        assertEquals(4L, backoff.nextDelayMs())
        assertNull(backoff.nextDelayMs())
        assertTrue(backoff.exhausted)
        assertEquals(3, backoff.maxAttempts)
    }

    private fun SpaceRecorder.recoverPhaseAfter(library: RecordedSpaceLibrary): RecordingPhase {
        return if (library.items().any { it.spaceId == spaceId }) RecordingPhase.SAVED else phase
    }
}
