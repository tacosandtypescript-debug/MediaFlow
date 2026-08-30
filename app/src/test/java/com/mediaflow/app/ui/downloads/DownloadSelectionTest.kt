package com.mediaflow.app.ui.downloads

import com.mediaflow.app.ui.home.QualityOption
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.SourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadSelectionTest {

    private val mixedSource = SourceInfo(
        sourceUrl = "https://example.com/watch?v=mediaflow",
        title = "Vídeo de prueba",
        availableFormats = listOf(
            MediaFormat(
                formatId = "137",
                extension = "mp4",
                mimeType = "video/mp4",
                mediaType = MediaType.VIDEO,
                qualityLabel = "1080p",
                height = 1080,
                videoCodec = "avc1",
                requiresMuxing = true,
            ),
            MediaFormat(
                formatId = "140",
                extension = "m4a",
                mimeType = "audio/mp4",
                mediaType = MediaType.AUDIO,
                qualityLabel = "128k",
                audioCodec = "mp4a",
            ),
        ),
    )

    @Test
    fun `audio selection never sends a video format`() {
        val selected = selectFormatForDownload(
            source = mixedSource,
            targetType = MediaType.AUDIO,
            quality = QualityOption.AUTO,
            selectedFormatId = "140",
        )

        assertEquals(MediaType.AUDIO, selected?.mediaType)
        assertEquals("140", selected?.formatId)
    }

    @Test
    fun `video selection ignores a stale audio format id`() {
        val selected = selectFormatForDownload(
            source = mixedSource,
            targetType = MediaType.VIDEO,
            quality = QualityOption.AUTO,
            selectedFormatId = "140",
        )

        assertEquals(MediaType.VIDEO, selected?.mediaType)
        assertEquals("137", selected?.formatId)
    }

    @Test
    fun `audio selection creates an audio request for video-only sources`() {
        val videoOnlySource = mixedSource.copy(
            availableFormats = mixedSource.availableFormats.filter { it.mediaType == MediaType.VIDEO },
        )

        val selected = selectFormatForDownload(
            source = videoOnlySource,
            targetType = MediaType.AUDIO,
            quality = QualityOption.AUTO,
            selectedFormatId = "bestaudio",
        )

        assertEquals(MediaType.AUDIO, selected?.mediaType)
        assertEquals("bestaudio", selected?.formatId)
        assertNull(selected?.videoCodec)
    }
}
