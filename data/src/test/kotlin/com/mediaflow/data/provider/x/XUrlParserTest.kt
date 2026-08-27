package com.mediaflow.data.provider.x

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XUrlParserTest {

    @Test
    fun `identifies X and Twitter URLs`() {
        assertTrue(XUrlParser.isXUrl("https://x.com/fakekiffs/status/2092796653707067736?s=46"))
        assertTrue(XUrlParser.isXUrl("https://twitter.com/i/spaces/1wGWjlyzqeNKQ"))
        assertTrue(XUrlParser.isXUrl("https://mobile.twitter.com/user/status/12345"))
        assertTrue(XUrlParser.isXUrl("https://vxtwitter.com/i/spaces/1wGWjlyzqeNKQ"))

        assertFalse(XUrlParser.isXUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(XUrlParser.isXUrl("https://www.instagram.com/p/C-12345/"))
        assertFalse(XUrlParser.isXUrl("https://www.tiktok.com/@user/video/123"))
        assertFalse(XUrlParser.isXUrl("invalid-url"))
    }

    @Test
    fun `extracts direct space ID`() {
        val spaceId = XUrlParser.extractDirectSpaceId("https://x.com/i/spaces/1wGWjlyzqeNKQ")
        assertEquals("1wGWjlyzqeNKQ", spaceId)

        val twitterSpaceId = XUrlParser.extractDirectSpaceId("https://twitter.com/i/spaces/1OwxWwQOPlNxQ?s=20")
        assertEquals("1OwxWwQOPlNxQ", twitterSpaceId)

        assertNull(XUrlParser.extractDirectSpaceId("https://x.com/fakekiffs/status/2092796653707067736"))
    }

    @Test
    fun `extracts status tweet ID`() {
        val statusId = XUrlParser.extractStatusId("https://x.com/fakekiffs/status/2092796653707067736?s=46")
        assertEquals("2092796653707067736", statusId)

        assertNull(XUrlParser.extractStatusId("https://x.com/i/spaces/1wGWjlyzqeNKQ"))
    }
}
