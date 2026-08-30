package com.mediaflow.data.media

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadedMediaPurgerTest {

    @Test
    fun purgeDeletesPrivateFileAndLedger() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val file = File(downloadsDir, "clip.mp4").apply { writeText("media") }
        val uri = Uri.fromFile(file)
        val store = MediaFlowLibraryStore(context)
        store.add(uri)

        DownloadedMediaPurger.purge(
            context,
            DownloadItem(
                id = "dl-1",
                sourceUrl = "https://example.test/clip",
                title = "clip",
                fileName = "clip.mp4",
                mediaType = MediaType.VIDEO,
                localUri = uri.toString(),
            ),
        )

        assertFalse(file.exists())
        assertTrue(store.uris().isEmpty())
    }
}
