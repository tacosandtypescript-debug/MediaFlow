package com.mediaflow.data

import android.net.Uri
import com.mediaflow.data.media.MediaFlowLibraryStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaFlowLibraryStoreTest {
    @Test
    fun persistsOnlyRegisteredMediaStoreUris() {
        val file = File.createTempFile("mediaflow-library", ".json")
        try {
            val store = MediaFlowLibraryStore(file, Unit)
            val owned = Uri.parse("content://media/external/video/media/230")
            val external = Uri.parse("content://media/external/video/media/231")

            store.add(owned)

            assertEquals(setOf(owned.toString()), store.uris())
            assertTrue(owned.toString() in store.uris())
            assertFalse(external.toString() in store.uris())

            store.remove(owned)
            assertTrue(store.uris().isEmpty())
        } finally {
            file.delete()
        }
    }
}
