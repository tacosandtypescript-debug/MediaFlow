package com.mediaflow.data

import com.mediaflow.data.ytdlp.YtDlpRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YtDlpRuntimeTest {
    @Test
    fun `analysis options skip media writes and pin a writable home path`() {
        val dir = File("build/tmp/yt-dlp-analysis").apply { mkdirs() }
        val opts = YtDlpRuntime.analysisOptions(dir)

        assertTrue(opts.getBoolean("skip_download"))
        assertTrue(opts.getBoolean("simulate"))
        assertFalse(opts.getBoolean("writethumbnail"))
        assertFalse(opts.getBoolean("check_formats"))
        assertFalse(opts.getBoolean("restrictfilenames"))
        assertTrue(opts.getBoolean("nopart"))
        assertFalse(opts.getBoolean("continuedl"))
        assertTrue(opts.getBoolean("overwrites"))
        assertEquals(3, opts.getInt("retries"))
        assertEquals(30, opts.getInt("socket_timeout"))
        assertEquals("0.0.0.0", opts.getString("source_address"))
        assertEquals(dir.absolutePath, opts.getJSONObject("paths").getString("home"))
        assertEquals(dir.absolutePath, opts.getJSONObject("paths").getString("temp"))
        val outtmpl = opts.getString("outtmpl")
        assertTrue(outtmpl.startsWith(dir.absolutePath))
        assertTrue(outtmpl.contains("analysis_%(id)s.%(ext)s"))
        assertEquals(
            listOf("tv", "web"),
            opts.getJSONObject("extractor_args")
                .getJSONObject("youtube")
                .getJSONArray("player_client")
                .let { array -> List(array.length()) { array.getString(it) } },
        )
        val ua = opts.getJSONObject("http_headers").getString("User-Agent")
        assertTrue(ua.contains("Chrome/13"))
        val chrome = Regex("Chrome/(\\d+)").find(ua)?.groupValues?.get(1)?.toInt()
        assertTrue(chrome in 131..136)
        assertFalse(ua.contains("Chrome/150"))
    }

    @Test
    fun `download options keep ipv4 retries and referer without downloading to cwd`() {
        val dir = File("build/tmp/yt-dlp-download").apply { mkdirs() }
        val template = File(dir, "clip.%(ext)s").absolutePath
        val opts = YtDlpRuntime.downloadOptions(
            outputDirectory = dir,
            outputTemplate = template,
            format = "18",
            referer = "https://www.facebook.com/",
        )

        assertFalse(opts.getBoolean("skip_download"))
        assertFalse(opts.getBoolean("check_formats"))
        assertEquals("18", opts.getString("format"))
        assertEquals(template, opts.getString("outtmpl"))
        assertEquals(dir.absolutePath, opts.getJSONObject("paths").getString("home"))
        assertEquals("https://www.facebook.com/", opts.getJSONObject("http_headers").getString("Referer"))
        assertEquals("0.0.0.0", opts.getString("source_address"))
        assertEquals(3, opts.getInt("retries"))
        assertTrue(opts.getBoolean("writethumbnail"))
    }

    @Test
    fun `file stem keeps titles that contain dots and only strips media extensions`() {
        val dottedTitle = "2.1M views Ya viene Halloween &#127875;"
        val stem = YtDlpRuntime.fileStem(dottedTitle)
        assertTrue(stem!!.startsWith("2.1M views Ya viene Halloween"))
        assertTrue(stem.length > 3)
        assertEquals(
            "2.1M views Ya viene Halloween",
            YtDlpRuntime.restrictStem("2.1M views Ya viene Halloween", "mediaflow_1"),
        )
        assertEquals("Mi video", YtDlpRuntime.fileStem("Mi video.mp4"))
        assertEquals("clip", YtDlpRuntime.fileStem("clip.m4a"))
    }

    @Test
    fun `findOutputFile matches restricted names and falls back to the written file`() {
        val dir = File("build/tmp/yt-dlp-find-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val startedAt = System.currentTimeMillis()
            val written = File(dir, "Ya_nadie_le_atora_a_una_mama_soltera_1068.mp4").apply {
                writeText("media")
                setLastModified(startedAt)
            }
            File(dir, "other.part").writeText("partial")

            val found = YtDlpRuntime.findOutputFile(
                directory = dir,
                startedAt = startedAt,
                expectedBaseName = "Ya nadie le atora a una mamá soltera😂",
            )
            assertEquals(written.canonicalFile, found?.canonicalFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `findOutputFile ignores thumbnail sidecars next to media`() {
        val dir = File("build/tmp/yt-dlp-find-thumb-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val startedAt = System.currentTimeMillis()
            val written = File(dir, "clip.mp4").apply {
                writeText("media")
                setLastModified(startedAt)
            }
            File(dir, "clip.jpg").apply {
                writeText("thumb")
                setLastModified(startedAt + 50)
            }

            val found = YtDlpRuntime.findOutputFile(
                directory = dir,
                startedAt = startedAt,
                expectedBaseName = "clip",
            )
            assertEquals(written.canonicalFile, found?.canonicalFile)

            File(dir, "clip.mp4").delete()
            val onlyThumb = YtDlpRuntime.findOutputFile(
                directory = dir,
                startedAt = startedAt,
                expectedBaseName = "clip",
            )
            assertEquals(null, onlyThumb)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `user agent is a real Chrome 131-136 string`() {
        val ua = YtDlpRuntime.USER_AGENT
        val chrome = Regex("Chrome/(\\d+)").find(ua)?.groupValues?.get(1)?.toInt()
        assertEquals(136, chrome)
        assertTrue(ua.startsWith("Mozilla/5.0"))
        assertTrue(ua.contains("Safari/537.36"))
    }
}
