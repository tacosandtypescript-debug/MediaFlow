package com.mediaflow.data.provider.x

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XUrlParserLiveTest {

    @Test
    fun `extractDirectSpaceId extracts id from twitter com spaces url`() {
        val url = "https://twitter.com/i/spaces/1jGXgBDyzpNKZ"
        val spaceId = XUrlParser.extractDirectSpaceId(url)
        assertEquals("1jGXgBDyzpNKZ", spaceId)
    }

    @Test
    fun `extractDirectSpaceId extracts id from x com spaces url with tracking params`() {
        val url = "https://x.com/i/spaces/1jGXgBDyzpNKZ?s=20&t=abcdef123"
        val spaceId = XUrlParser.extractDirectSpaceId(url)
        assertEquals("1jGXgBDyzpNKZ", spaceId)
    }

    @Test
    fun `extractDirectSpaceId extracts id from mobile twitter com`() {
        val url = "https://mobile.twitter.com/i/spaces/1EAJbroXZNExL"
        val spaceId = XUrlParser.extractDirectSpaceId(url)
        assertEquals("1EAJbroXZNExL", spaceId)
    }

    @Test
    fun `isXUrl returns true for valid domains`() {
        assertTrue(XUrlParser.isXUrl("https://x.com/i/spaces/1jGXgBDyzpNKZ"))
        assertTrue(XUrlParser.isXUrl("https://twitter.com/i/spaces/1jGXgBDyzpNKZ"))
        assertTrue(XUrlParser.isXUrl("https://mobile.x.com/i/spaces/1jGXgBDyzpNKZ"))
    }
}
