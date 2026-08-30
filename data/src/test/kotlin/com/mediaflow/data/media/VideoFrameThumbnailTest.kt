package com.mediaflow.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameThumbnailTest {
    @Test
    fun cacheFileNameIsStableForSameUri() {
        val uri = "file:///data/user/0/com.mediaflow.app/files/downloads/clip.mp4"
        val first = VideoFrameThumbnail.cacheFileName(uri)
        val second = VideoFrameThumbnail.cacheFileName(uri)
        assertEquals(first, second)
        assertTrue(first.endsWith(".jpg"))
        assertEquals(24 + 4, first.length)
        val other = VideoFrameThumbnail.cacheFileName("content://media/external/video/media/12")
        assertTrue(first != other)
    }

    @Test
    fun needsFrameWhenThumbIsMissingOrNotAnImage() {
        assertTrue(VideoFrameThumbnail.needsFrame(null))
        assertTrue(VideoFrameThumbnail.needsFrame(""))
        assertTrue(VideoFrameThumbnail.needsFrame("file:///cache/clip.mp4"))
        assertTrue(VideoFrameThumbnail.needsFrame("content://media/external/video/media/12"))
        assertFalse(VideoFrameThumbnail.needsFrame("file:///files/thumbs/dl-1.jpg"))
        assertFalse(VideoFrameThumbnail.needsFrame("https://cdn.example/cover.webp"))
    }
}
