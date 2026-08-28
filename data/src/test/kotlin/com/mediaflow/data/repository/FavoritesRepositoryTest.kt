package com.mediaflow.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FavoritesRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: FavoritesRepositoryImpl
    private lateinit var storageFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storageFile = File(context.filesDir, "favorites.json")
        storageFile.delete()
        repository = FavoritesRepositoryImpl(context)
    }

    @After
    fun tearDown() {
        storageFile.delete()
    }

    @Test
    fun toggleFavorite_addsAndRemoves() = runTest {
        val uri = "file:///music/song.mp3"
        assertFalse(repository.isFavorite(uri))

        val added = repository.toggleFavorite(uri)
        assertTrue(added)
        assertTrue(repository.isFavorite(uri))
        assertEquals(setOf(uri), repository.observeFavoriteMediaUris().first())

        val removed = repository.toggleFavorite(uri)
        assertFalse(removed)
        assertFalse(repository.isFavorite(uri))
        assertTrue(repository.observeFavoriteMediaUris().first().isEmpty())
    }
}
