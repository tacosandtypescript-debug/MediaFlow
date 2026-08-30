package com.mediaflow.data.download.formats

import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealFormatCatalogTest {
    @Test
    fun `360 plus 720 plus 1080 plus 137 140 does not invent 480 1440 2160`() {
        val formats = youtubeLike3607201080AndAudio()
        val heights = RealFormatCatalog.listedHeights(formats)
        val labels = RealFormatCatalog.listed(formats).map { it.label }

        assertEquals(setOf(360, 720, 1080), heights.toSet())
        assertFalse(heights.contains(480))
        assertFalse(heights.contains(1440))
        assertFalse(heights.contains(2160))
        labels.forEach { label ->
            assertFalse(label.contains("480p"))
            assertFalse(label.contains("1440p"))
            assertFalse(label.contains("2160p"))
        }
        assertFalse(RealFormatCatalog.listedFps(formats).any { it !in formats.mapNotNull { f -> f.fps } })
        assertEquals(
            setOf("avc1.42001E", "avc1.64001F", "avc1.640028"),
            RealFormatCatalog.listedVideoCodecs(formats).toSet(),
        )
        assertEquals(setOf("mp4a.40.2"), RealFormatCatalog.listedAudioCodecs(formats).toSet())
    }

    @Test
    fun `same height different codec and fps stay distinct`() {
        val formats = listOf(
            video("248", height = 1080, fps = 30.0, vcodec = "vp9", ext = "webm"),
            video("137", height = 1080, fps = 30.0, vcodec = "avc1.640028", ext = "mp4"),
            video("299", height = 1080, fps = 60.0, vcodec = "avc1.64002A", ext = "mp4"),
        )
        val listed = RealFormatCatalog.listed(formats)
        assertEquals(3, listed.size)
        assertEquals(3, listed.map { it.label }.distinct().size)
        assertTrue(listed.any { it.label.contains("vp9") && it.label.contains("30fps") })
        assertTrue(listed.any { it.label.contains("avc1.640028") && it.label.contains("30fps") })
        assertTrue(listed.any { it.label.contains("avc1.64002A") && it.label.contains("60fps") })
    }

    @Test
    fun `missing height is not labeled unknown and does not invent a p rung`() {
        val format = MediaFormat(
            formatId = "mystery",
            extension = "mp4",
            mediaType = MediaType.VIDEO,
            videoCodec = "avc1",
            fps = 24.0,
        )
        val label = RealFormatCatalog.labelFor(format)
        assertEquals("avc1 · 24fps · MP4", label)
        assertTrue(RealFormatCatalog.listedHeights(listOf(format)).isEmpty())
        assertFalse(label.matches(Regex(".*\\d+p.*")))
        assertFalse(label.contains("unknown", ignoreCase = true))
    }

    @Test
    fun `aac with abr and m4a uses extractor fields only`() {
        val format = MediaFormat(
            formatId = "140",
            extension = "m4a",
            mediaType = MediaType.AUDIO,
            audioCodec = "mp4a.40.2",
            bitrate = 128,
            container = "m4a",
        )
        assertEquals("AAC · 128 kbps · M4A", RealFormatCatalog.labelFor(format))
    }

    @Test
    fun `opus with abr and webm uses extractor fields only`() {
        val format = MediaFormat(
            formatId = "251",
            extension = "webm",
            mediaType = MediaType.AUDIO,
            audioCodec = "opus",
            bitrate = 160,
            container = "webm",
        )
        assertEquals("Opus · 160 kbps · WebM", RealFormatCatalog.labelFor(format))
    }

    @Test
    fun `aac and opus without abr do not invent kbps`() {
        val aac = MediaFormat(
            formatId = "140",
            extension = "m4a",
            mediaType = MediaType.AUDIO,
            audioCodec = "mp4a.40.2",
            container = "m4a",
        )
        val opus = MediaFormat(
            formatId = "251",
            extension = "webm",
            mediaType = MediaType.AUDIO,
            audioCodec = "opus",
        )
        assertEquals("AAC · M4A", RealFormatCatalog.labelFor(aac))
        assertFalse(RealFormatCatalog.labelFor(aac).contains("kbps"))
        val opusLabel = RealFormatCatalog.labelFor(opus)
        assertTrue(opusLabel.contains("Opus"))
        assertFalse(opusLabel.contains("kbps"))
        assertFalse(opusLabel.contains("unknown", ignoreCase = true))
    }

    @Test
    fun `audio-only with no height does not produce Unknown`() {
        val format = MediaFormat(
            formatId = "140",
            extension = "m4a",
            mediaType = MediaType.AUDIO,
            audioCodec = "mp4a.40.2",
        )
        val label = RealFormatCatalog.labelFor(format)
        assertFalse(label.contains("unknown", ignoreCase = true))
        assertFalse(label.contains("Unknown"))
    }

    @Test
    fun `equivalent audio streams collapse to one chip`() {
        val formats = listOf(
            MediaFormat(
                formatId = "140",
                extension = "m4a",
                mediaType = MediaType.AUDIO,
                audioCodec = "mp4a.40.2",
                bitrate = 128,
                container = "m4a",
            ),
            MediaFormat(
                formatId = "140-dup",
                extension = "m4a",
                mediaType = MediaType.AUDIO,
                audioCodec = "mp4a.40.2",
                bitrate = 128,
                container = "m4a",
            ),
        )
        val listed = RealFormatCatalog.listed(formats)
        assertEquals(1, listed.size)
        assertEquals("AAC · 128 kbps · M4A", listed.single().label)
    }

    @Test
    fun `bestCompatible prefers progressive mp4 avc aac over vp9 av1`() {
        val progressive = video(
            "18",
            height = 360,
            fps = 30.0,
            vcodec = "avc1.42001E",
            acodec = "mp4a.40.2",
            ext = "mp4",
            progressive = true,
        )
        val vp9 = video("248", height = 1080, fps = 30.0, vcodec = "vp9", ext = "webm", mux = true)
        val av1 = video("399", height = 1080, fps = 30.0, vcodec = "av01.0.08M.08", ext = "mp4", mux = true)
        val avcMux = video("137", height = 1080, fps = 30.0, vcodec = "avc1.640028", ext = "mp4", mux = true)

        assertEquals("18", RealFormatCatalog.bestCompatible(listOf(vp9, av1, progressive, avcMux))?.formatId)
        assertEquals("137", RealFormatCatalog.bestCompatible(listOf(vp9, av1, avcMux))?.formatId)
    }

    private fun youtubeLike3607201080AndAudio(): List<MediaFormat> = listOf(
        video("18", height = 360, fps = 30.0, vcodec = "avc1.42001E", acodec = "mp4a.40.2", ext = "mp4", progressive = true),
        video("22", height = 720, fps = 30.0, vcodec = "avc1.64001F", acodec = "mp4a.40.2", ext = "mp4", progressive = true),
        video("137", height = 1080, fps = 30.0, vcodec = "avc1.640028", ext = "mp4", mux = true),
        MediaFormat(
            formatId = "140",
            extension = "m4a",
            mediaType = MediaType.AUDIO,
            qualityLabel = "audio",
            audioCodec = "mp4a.40.2",
            container = "m4a",
        ),
    )

    private fun video(
        id: String,
        height: Int? = null,
        fps: Double? = null,
        vcodec: String? = null,
        acodec: String? = null,
        ext: String = "mp4",
        progressive: Boolean = false,
        mux: Boolean = false,
    ) = MediaFormat(
        formatId = id,
        extension = ext,
        mediaType = MediaType.VIDEO,
        qualityLabel = height?.let { "${it}p" },
        height = height,
        fps = fps,
        container = ext,
        videoCodec = vcodec,
        audioCodec = acodec,
        isProgressive = progressive,
        requiresMuxing = mux,
    )
}
