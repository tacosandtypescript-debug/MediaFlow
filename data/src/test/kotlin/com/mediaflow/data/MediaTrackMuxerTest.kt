package com.mediaflow.data

import com.mediaflow.data.media.MediaTrackMuxer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTrackMuxerTest {
    @Test
    fun `supports h264 and aac without transcoding`() {
        assertTrue(MediaTrackMuxer.canMuxWithoutTranscoding("video/avc", "audio/mp4a-latm"))
    }

    @Test
    fun `rejects webm codecs instead of claiming mp4 merge`() {
        assertFalse(MediaTrackMuxer.canMuxWithoutTranscoding("video/vp9", "audio/opus"))
    }

    @Test
    fun `rejects missing track mime`() {
        assertFalse(MediaTrackMuxer.canMuxWithoutTranscoding(null, "audio/mp4a-latm"))
    }
}
