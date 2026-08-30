package com.mediaflow.data.provider.x.dvr

import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.recording.RecordedSpaceLibrary
import com.mediaflow.data.provider.x.recording.RecordingPhase
import com.mediaflow.data.provider.x.recording.SpaceRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class HlsLiveIngestTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun parserReadsExtinfUrisAgainstFixturePlaylist() {
        val playlist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXTINF:2.000,
            seg0.ts
            #EXTINF:2.000,
            https://cdn.example/live/seg1.ts
        """.trimIndent()
        val refs = HlsMediaPlaylistParser.parse(
            playlist,
            "https://cdn.example/live/index.m3u8",
        )
        assertEquals(2, refs.size)
        assertEquals("https://cdn.example/live/seg0.ts", refs[0].uri)
        assertEquals(2_000L, refs[0].durationMs)
        assertEquals("https://cdn.example/live/seg1.ts", refs[1].uri)
    }

    @Test
    fun ingestFeedsSpaceRecorderRealSegmentBytesNotTimerPadding() {
        val playlistUrl = "https://cdn.example/live/index.m3u8"
        val bodies = mapOf(
            playlistUrl to """
                #EXTM3U
                #EXTINF:1.000,
                a.ts
                #EXTINF:1.000,
                b.ts
            """.trimIndent().toByteArray(),
            "https://cdn.example/live/a.ts" to byteArrayOf(1, 2, 3, 4),
            "https://cdn.example/live/b.ts" to byteArrayOf(9, 8, 7),
        )
        val ingest = HlsLiveIngest { url -> bodies.getValue(url) }
        val rec = SpaceRecorder(
            workDir = temp.newFolder(),
            library = RecordedSpaceLibrary(temp.newFile("lib.json")),
            spaceId = "1rGmqplYpggGy",
            originalUrl = "https://x.com/i/spaces/1rGmqplYpggGy",
        )
        rec.setRecordEnabled(true)
        rec.playbackPaused = true
        val pulled = ingest.pull(playlistUrl)
        assertEquals(2, pulled.size)
        assertTrue(pulled[0].contentEquals(byteArrayOf(1, 2, 3, 4)))
        pulled.forEach { rec.acceptRecorderTick(it) }
        assertEquals(RecordingPhase.RECORDING, rec.phase)
        assertEquals(7L, rec.elapsedMs)
        assertTrue(rec.playbackPaused)
        val again = ingest.pull(playlistUrl)
        assertTrue(again.isEmpty())
        assertEquals(null, rec.onLiveEnded(XSpaceState.LIVE))
        val saved = rec.onLiveEnded(XSpaceState.ENDED)
        requireNotNull(saved)
        assertEquals("1rGmqplYpggGy", saved.spaceId)
        assertTrue(java.io.File(saved.filePath).length() > 0L)
    }

    @Test
    fun recordOffStillPullsButDoesNotSaveLibrary() {
        val playlistUrl = "https://cdn.example/live/index.m3u8"
        val bodies = mapOf(
            playlistUrl to "#EXTM3U\n#EXTINF:1.000,\nx.ts\n".toByteArray(),
            "https://cdn.example/live/x.ts" to byteArrayOf(5, 5),
        )
        val ingest = HlsLiveIngest { url -> bodies.getValue(url) }
        val libFile = temp.newFile("off.json")
        val rec = SpaceRecorder(
            workDir = temp.newFolder(),
            library = RecordedSpaceLibrary(libFile),
            spaceId = "space_off",
            originalUrl = "https://x.com/i/spaces/space_off",
        )
        ingest.pull(playlistUrl).forEach { rec.acceptRecorderTick(it) }
        assertEquals(null, rec.onLiveEnded(XSpaceState.ENDED))
        assertTrue(RecordedSpaceLibrary(libFile).items().isEmpty())
        assertFalse(rec.recordEnabled)
    }
}
