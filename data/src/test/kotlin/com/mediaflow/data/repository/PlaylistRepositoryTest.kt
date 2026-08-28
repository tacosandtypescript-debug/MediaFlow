package com.mediaflow.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlaylistRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: PlaylistRepositoryImpl
    private lateinit var storageFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storageFile = File(context.filesDir, "playlists.json")
        storageFile.delete()
        repository = PlaylistRepositoryImpl(context)
    }

    @After
    fun tearDown() {
        storageFile.delete()
    }

    @Test
    fun createPlaylist_persistsAndEmits() = runTest {
        val playlist = repository.createPlaylist("Mis Spaces Favoritos")
        assertEquals("Mis Spaces Favoritos", playlist.name)
        assertEquals(0, playlist.itemCount)

        val list = repository.observePlaylists().first()
        assertEquals(1, list.size)
        assertEquals("Mis Spaces Favoritos", list.first().name)
    }

    @Test
    fun addAndRemoveMedia_updatesPlaylist() = runTest {
        val playlist = repository.createPlaylist("Podcast")
        repository.addMediaToPlaylist(playlist.id, "file:///path/audio1.m4a")
        repository.addMediaToPlaylist(playlist.id, "file:///path/audio2.m4a")

        assertTrue(repository.isMediaInPlaylist(playlist.id, "file:///path/audio1.m4a"))
        val updated = repository.getPlaylist(playlist.id)
        assertNotNull(updated)
        assertEquals(2, updated!!.itemCount)

        repository.removeMediaFromPlaylist(playlist.id, "file:///path/audio1.m4a")
        assertFalse(repository.isMediaInPlaylist(playlist.id, "file:///path/audio1.m4a"))
        assertEquals(1, repository.getPlaylist(playlist.id)?.itemCount)
    }

    @Test
    fun renameAndDeletePlaylist_functionsCorrectly() = runTest {
        val playlist = repository.createPlaylist("Original")
        repository.renamePlaylist(playlist.id, "Renombrado")

        assertEquals("Renombrado", repository.getPlaylist(playlist.id)?.name)

        repository.deletePlaylist(playlist.id)
        assertNull(repository.getPlaylist(playlist.id))
        assertTrue(repository.observePlaylists().first().isEmpty())
    }
}
