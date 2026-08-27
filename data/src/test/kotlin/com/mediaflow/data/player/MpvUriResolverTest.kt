package com.mediaflow.data.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class MpvUriResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val resolver by lazy { MpvUriResolver(context) }

    @Test
    fun `resolves file URI to absolute path when file exists`() {
        val file = tempFolder.newFile("sample_video.mp4")
        val fileUri = "file://${file.absolutePath}"

        val resolved = resolver.resolve(fileUri)
        assertEquals(file.absolutePath, resolved.path)
        resolved.close()
    }

    @Test
    fun `resolves raw absolute path when file exists`() {
        val file = tempFolder.newFile("audio_track.mp3")

        val resolved = resolver.resolve(file.absolutePath)
        assertEquals(file.absolutePath, resolved.path)
        resolved.close()
    }

    @Test
    fun `resolves http and https URLs directly`() {
        val httpUrl = "https://example.test/stream.m3u8"
        val resolved = resolver.resolve(httpUrl)
        assertEquals(httpUrl, resolved.path)
        resolved.close()
    }

    @Test(expected = FileNotFoundException::class)
    fun `throws FileNotFoundException for non-existent file path`() {
        resolver.resolve("/non/existent/path/missing_file.webm")
    }

    @Test(expected = FileNotFoundException::class)
    fun `throws FileNotFoundException for blank input`() {
        resolver.resolve("   ")
    }
}
