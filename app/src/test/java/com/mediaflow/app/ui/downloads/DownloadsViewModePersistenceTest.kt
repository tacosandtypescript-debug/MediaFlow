package com.mediaflow.app.ui.downloads

import androidx.test.core.app.ApplicationProvider
import com.mediaflow.app.data.DownloadsPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsViewModePersistenceTest {
    @Test
    fun persistsGridPreference() = runBlocking {
        val prefs = DownloadsPreferences(ApplicationProvider.getApplicationContext())
        prefs.setViewMode(DownloadsViewMode.GRID)
        assertEquals(DownloadsViewMode.GRID, prefs.viewMode.first())
        prefs.setViewMode(DownloadsViewMode.LIST)
        assertEquals(DownloadsViewMode.LIST, prefs.viewMode.first())
    }
}
