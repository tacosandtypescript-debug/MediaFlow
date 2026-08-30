package com.mediaflow.data.download.extractors

import android.media.MediaFormat as AndroidMediaFormat
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaType
import com.mediaflow.data.download.PlatformFormatSelector
import com.mediaflow.data.download.extractors.facebook.FacebookExtractor
import com.mediaflow.data.download.extractors.spaces.SpacesExtractor
import com.mediaflow.data.download.extractors.tiktok.TikTokExtractor
import com.mediaflow.data.download.extractors.twitter.TwitterExtractor
import com.mediaflow.data.download.extractors.youtube.YoutubeExtractor
import com.mediaflow.data.download.formats.RealFormatCatalog
import com.mediaflow.data.download.processing.DownloadCompletionGate
import com.mediaflow.data.media.MediaExtractorAdapter
import com.mediaflow.data.media.MediaFileValidator
import com.mediaflow.data.media.MediaTrackMuxer
import com.mediaflow.domain.repository.DownloadRequest
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
class PlatformExtractorPipelineTest {
    private lateinit var tempDir: File
    private val originalFactory = MediaFileValidator.extractorFactory

    @Before
    fun setUp() {
        tempDir = File.createTempFile("pipeline", "").apply { delete(); mkdirs() }
    }

    @After
    fun tearDown() {
        MediaFileValidator.extractorFactory = originalFactory
        tempDir.deleteRecursively()
    }

    @Test
    fun youtubeExtractsRealHeightsMerges137AndValidatesBeforeComplete() {
        val formats = YtDlpFormatParser.parseRoot(YOUTUBE_FIXTURE)
        assertEquals(setOf(360, 720, 1080), RealFormatCatalog.listedHeights(formats).toSet())
        assertFalse(480 in RealFormatCatalog.listedHeights(formats))
        assertFalse(2160 in RealFormatCatalog.listedHeights(formats))
        val videoOnly = formats.first { it.formatId == "137" }
        assertTrue(DownloadCompletionGate.needsMerge(videoOnly))
        assertTrue(YoutubeExtractor.profile.muxVideoOnlyWithAac)
        val selector = PlatformFormatSelector.select(
            DownloadRequest(
                sourceUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                mediaType = MediaType.VIDEO,
                formatId = "137",
                requiresMuxing = true,
            ),
        )
        assertTrue(selector.startsWith("137+"))
        assertEquals(
            DownloadStatus.FAILED,
            DownloadCompletionGate.statusAfter(rejectEmpty("yt.mp4", MediaType.VIDEO)),
        )
        assertEquals(
            DownloadStatus.COMPLETED,
            DownloadCompletionGate.statusAfter(acceptFile("yt.mp4", MediaType.VIDEO, video = true)),
        )
        assertTrue(MediaTrackMuxer.canMuxWithoutTranscoding("video/avc", "audio/mp4a-latm"))
    }

    @Test
    fun tiktokVerticalDoesNotInventLandscapeRungs() {
        val formats = YtDlpFormatParser.parseRoot(TIKTOK_FIXTURE)
        assertEquals(setOf(1920), RealFormatCatalog.listedHeights(formats).toSet())
        assertEquals(1080, formats.single().width)
        assertEquals(1920, formats.single().height)
        assertEquals("https://www.tiktok.com/", TikTokExtractor.profile.pageReferer)
        assertFalse(720 in RealFormatCatalog.listedHeights(formats))
    }

    @Test
    fun facebookListsOnlyExtractorHeights() {
        val formats = YtDlpFormatParser.parseRoot(FACEBOOK_FIXTURE)
        assertEquals(setOf(720), RealFormatCatalog.listedHeights(formats).toSet())
        assertEquals("https://www.facebook.com/", FacebookExtractor.profile.pageReferer)
        assertFalse(1080 in RealFormatCatalog.listedHeights(formats))
    }

    @Test
    fun twitterVariantsStayDistinctAndEmptyFileCannotComplete() {
        val formats = YtDlpFormatParser.parseRoot(TWITTER_FIXTURE)
        assertEquals(setOf(720, 480), RealFormatCatalog.listedHeights(formats).toSet())
        assertEquals("https://x.com/", TwitterExtractor.profile.pageReferer)
        assertEquals(
            DownloadStatus.FAILED,
            DownloadCompletionGate.statusAfter(rejectEmpty("x.mp4", MediaType.VIDEO)),
        )
    }

    @Test
    fun spacesUrlIsNotANormalVideoAndDoesNotMux() {
        assertTrue(SpacesExtractor.isSpaceUrl("https://x.com/i/spaces/1jGXgBDyzpNKZ"))
        assertTrue(SpacesExtractor.isSpaceUrl("https://twitter.com/i/spaces/1jGXgBDyzpNKZ"))
        assertFalse(SpacesExtractor.isSpaceUrl("https://x.com/user/status/123"))
        assertFalse(SpacesExtractor.profile.muxVideoOnlyWithAac)
        assertEquals(
            SpacesExtractor.profile,
            PlatformDownloadProfiles.forUrl("https://x.com/i/spaces/1jGXgBDyzpNKZ"),
        )
    }

    private fun rejectEmpty(name: String, type: MediaType) =
        DownloadCompletionGate.evaluate(
            File(tempDir, name).apply { writeBytes(ByteArray(0)) },
            DownloadRequest("https://example.com", type, extension = "mp4"),
        )

    private fun acceptFile(name: String, type: MediaType, video: Boolean): Result<MediaFileValidator.ValidatedMedia> {
        val file = File(tempDir, name).apply { writeBytes(ByteArray(64) { 1 }) }
        val track = if (video) {
            AndroidMediaFormat.createVideoFormat("video/avc", 1920, 1080).apply {
                setLong(AndroidMediaFormat.KEY_DURATION, 5_000_000L)
            }
        } else {
            AndroidMediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2).apply {
                setLong(AndroidMediaFormat.KEY_DURATION, 5_000_000L)
            }
        }
        MediaFileValidator.extractorFactory = {
            object : MediaExtractorAdapter {
                override val trackCount: Int = 1
                override fun getTrackFormat(index: Int) = track
                override fun release() {}
            }
        }
        return DownloadCompletionGate.evaluate(
            file,
            DownloadRequest("https://www.youtube.com/watch?v=dQw4w9WgXcQ", type, extension = "mp4"),
        )
    }

    private companion object {
        const val YOUTUBE_FIXTURE = """
        {"title":"Demo","duration":12,"formats":[
          {"format_id":"18","ext":"mp4","width":640,"height":360,"fps":30,"vcodec":"avc1","acodec":"mp4a","filesize":1000},
          {"format_id":"22","ext":"mp4","width":1280,"height":720,"fps":30,"vcodec":"avc1","acodec":"mp4a","filesize":2000},
          {"format_id":"137","ext":"mp4","width":1920,"height":1080,"fps":30,"vcodec":"avc1","acodec":"none","filesize":3000},
          {"format_id":"140","ext":"m4a","acodec":"mp4a","vcodec":"none","filesize":200}
        ]}
        """
        const val TIKTOK_FIXTURE = """
        {"title":"Vertical","duration":8,"formats":[
          {"format_id":"h264_1080p","ext":"mp4","width":1080,"height":1920,"fps":30,"vcodec":"h264","acodec":"aac"}
        ]}
        """
        const val FACEBOOK_FIXTURE = """
        {"title":"Reel","duration":15,"formats":[
          {"format_id":"hd","ext":"mp4","width":1280,"height":720,"fps":30,"vcodec":"avc1","acodec":"mp4a"}
        ]}
        """
        const val TWITTER_FIXTURE = """
        {"title":"Post","duration":6,"formats":[
          {"format_id":"http-2176","ext":"mp4","width":1280,"height":720,"vcodec":"avc1","acodec":"mp4a","tbr":2176},
          {"format_id":"http-832","ext":"mp4","width":640,"height":480,"vcodec":"avc1","acodec":"mp4a","tbr":832}
        ]}
        """
    }
}
