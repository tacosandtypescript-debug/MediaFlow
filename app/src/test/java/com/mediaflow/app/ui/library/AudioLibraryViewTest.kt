package com.mediaflow.app.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.app.ui.library.components.AudioLibraryView
import com.mediaflow.app.ui.theme.MediaFlowTheme
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-normal-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioLibraryViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleItems() = listOf(
        DownloadItem(id = "a", sourceUrl = "s-a", title = "A", mediaType = MediaType.AUDIO),
        DownloadItem(id = "b", sourceUrl = "s-b", title = "B", mediaType = MediaType.AUDIO),
    )

    @Test
    fun shuffleChipTogglesAndShowsSelectedState() {
        var shuffle by mutableStateOf(false)
        composeRule.setContent {
            MediaFlowTheme {
                AudioLibraryView(
                    items = sampleItems(),
                    spacesMap = emptyMap(),
                    progressMap = emptyMap(),
                    playingMediaId = null,
                    isPlayerPlaying = false,
                    favoriteUris = emptySet(),
                    onPlayItem = { _, _ -> },
                    onToggleFavorite = {},
                    onAddToPlaylist = {},
                    onAddToQueue = {},
                    onDeleteMedia = {},
                    shuffleEnabled = shuffle,
                    onShuffleChange = { shuffle = it },
                    onShuffleAll = { shuffle = true },
                )
            }
        }
        composeRule.onNodeWithTag("library_shuffle_btn").assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithTag("library_shuffle_btn").performClick()
        composeRule.waitForIdle()
        assertEquals(true, shuffle)
        composeRule.onNodeWithTag("library_shuffle_btn").assertIsSelected()
    }
}
