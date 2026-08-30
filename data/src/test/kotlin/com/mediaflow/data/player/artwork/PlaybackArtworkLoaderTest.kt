package com.mediaflow.data.player.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlaybackArtworkLoaderTest {

    @Test
    fun loadsJpegFromFileUri() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "art.jpg")
        val original = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { original.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val loaded = PlaybackArtworkLoader.load(context, "file://${file.absolutePath}")
        assertNotNull(loaded)
        assertTrue(loaded!!.width >= 8)
        BitmapFactory.decodeFile(file.absolutePath).also { assertNotNull(it) }
    }

    @Test
    fun rejectsBlankAndMissing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertNull(PlaybackArtworkLoader.load(context, null))
        assertNull(PlaybackArtworkLoader.load(context, ""))
        assertNull(PlaybackArtworkLoader.load(context, "file:///tmp/missing-mediaflow-art.jpg"))
    }
}
