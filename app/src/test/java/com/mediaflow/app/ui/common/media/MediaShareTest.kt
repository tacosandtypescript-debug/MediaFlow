package com.mediaflow.app.ui.common.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaShareTest {
    @Test
    fun `remote streams are not shareable files`() {
        assertTrue(MediaShare.isRemoteStream("https://example.com/live.m3u8"))
        assertTrue(MediaShare.isRemoteStream("http://cdn.example/a.mp4"))
        assertFalse(MediaShare.isRemoteStream("file:///data/data/com.mediaflow.app/files/downloads/clip.mp4"))
        assertFalse(MediaShare.isRemoteStream("content://media/external/video/media/12"))
        assertFalse(MediaShare.isRemoteStream("/data/user/0/com.mediaflow.app/files/downloads/song.m4a"))
    }

    @Test
    fun `mime type follows the real file extension`() {
        assertEquals("video/mp4", MediaShare.mimeFromName("clip.mp4", isAudio = false))
        assertEquals("audio/mp4", MediaShare.mimeFromName("space.m4a", isAudio = true))
        assertEquals("audio/mpeg", MediaShare.mimeFromName("song.mp3", isAudio = true))
        assertEquals("video/webm", MediaShare.mimeFromName("reel.webm", isAudio = false))
        assertEquals("audio/*", MediaShare.mimeFromName("unknown", isAudio = true))
        assertEquals("video/*", MediaShare.mimeFromName("unknown", isAudio = false))
    }
}
