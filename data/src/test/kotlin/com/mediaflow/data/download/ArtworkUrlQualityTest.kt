package com.mediaflow.data.download

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkUrlQualityTest {
    @Test
    fun youtubeHqDefaultUpgradesToMaxres() {
        assertEquals(
            "https://i.ytimg.com/vi/jf6tbohQG_E/maxresdefault.jpg",
            ArtworkUrlQuality.upgrade("https://i.ytimg.com/vi/jf6tbohQG_E/hqdefault.jpg"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/jf6tbohQG_E/maxresdefault.jpg",
            ArtworkUrlQuality.upgrade("https://i.ytimg.com/vi/jf6tbohQG_E/mqdefault.jpg"),
        )
    }

    @Test
    fun youtubeMusicGoogleusercontentDropsTinySizeToken() {
        val small = "https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"
        assertEquals(
            "https://lh3.googleusercontent.com/abc=s0",
            ArtworkUrlQuality.upgrade(small),
        )
    }

    @Test
    fun pickBestPrefersWidestThumbnailEntry() {
        val thumbs = JSONArray()
            .put(JSONObject().put("url", "https://i.ytimg.com/vi/abc/default.jpg").put("width", 120).put("height", 90))
            .put(JSONObject().put("url", "https://i.ytimg.com/vi/abc/hqdefault.jpg").put("width", 480).put("height", 360))
            .put(JSONObject().put("url", "https://i.ytimg.com/vi/abc/maxresdefault.jpg").put("width", 1280).put("height", 720))
        assertEquals(
            "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
            ArtworkUrlQuality.pickBest(thumbs, "https://i.ytimg.com/vi/abc/hqdefault.jpg"),
        )
    }

    @Test
    fun candidatesTryMaxresBeforeHq() {
        val list = ArtworkUrlQuality.candidates("https://i.ytimg.com/vi/abc12345678/hqdefault.jpg")
        assertEquals("https://i.ytimg.com/vi/abc12345678/maxresdefault.jpg", list.first())
        assertTrue(list.contains("https://i.ytimg.com/vi/abc12345678/hqdefault.jpg"))
    }
}
