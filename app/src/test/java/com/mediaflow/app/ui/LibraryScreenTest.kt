package com.mediaflow.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.app.ui.common.media.AudioMediaRow
import com.mediaflow.app.ui.library.LibraryScreen
import com.mediaflow.app.ui.player.miniplayer.MiniPlayer
import com.mediaflow.app.ui.theme.MediaFlowTheme
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlayerServiceState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-normal-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryScreen_rendersHeaderAndSelectors() {
        composeRule.setContent {
            MediaFlowTheme {
                LibraryScreen(onOpenItem = {})
            }
        }

        composeRule.onNodeWithText("Tu biblioteca").assertIsDisplayed()
        composeRule.onNodeWithText("Audio").assertIsDisplayed()
        composeRule.onNodeWithText("Video").assertIsDisplayed()
        composeRule.onNodeWithText("Todos").assertIsDisplayed()
        composeRule.onNodeWithText("Favoritos").assertIsDisplayed()
        composeRule.onNodeWithText("Playlists").assertIsDisplayed()
    }

    @Test
    fun libraryScreen_switchingToPlaylistsTab_showsPlaylistsHeader() {
        composeRule.setContent {
            MediaFlowTheme {
                LibraryScreen(onOpenItem = {})
            }
        }

        composeRule.onNodeWithText("Playlists").performClick()
        composeRule.onNodeWithText("Tus playlists").assertIsDisplayed()
        composeRule.onNodeWithText("Nueva playlist").assertIsDisplayed()
    }

    @Test
    fun audioMediaRow_rendersContentAndHandlesClicks() {
        var played = false
        var favorited = false

        composeRule.setContent {
            MediaFlowTheme {
                AudioMediaRow(
                    title = "Episodio 12: Inteligencia Artificial",
                    subtitle = "Host: @antigravity",
                    artworkUrl = null,
                    durationText = "45:20",
                    isSpace = true,
                    isPlaying = true,
                    isFavorite = false,
                    onClick = { played = true },
                    onToggleFavorite = { favorited = true },
                    onAddToPlaylist = {},
                    onAddToQueue = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("Episodio 12: Inteligencia Artificial").assertIsDisplayed()
        composeRule.onNodeWithText("Host: @antigravity").assertIsDisplayed()
        composeRule.onNodeWithText("SPACE").assertIsDisplayed()
        composeRule.onNodeWithText("· 45:20").assertIsDisplayed()

        composeRule.onNodeWithTag("audio_media_row").performClick()
        assertTrue("Item click should invoke playback", played)

        composeRule.onNodeWithTag("favorite_btn_inactive").performClick()
        assertTrue("Favorite button click should toggle favorite", favorited)
    }

    @Test
    fun miniPlayer_rendersAndTriggersActions() {
        var opened = false
        var playToggled = false

        val testState = PlayerServiceState(
            mediaId = "file:///tmp/music.mp3",
            filePath = "file:///tmp/music.mp3",
            title = "Track Demo",
            artistOrHost = "Artist Name",
            playbackState = EnginePlaybackState.PLAYING,
            currentPositionMs = 30_000L,
            durationMs = 120_000L,
        )

        composeRule.setContent {
            MediaFlowTheme {
                MiniPlayer(
                    serviceState = testState,
                    onOpenPlayer = { opened = true },
                    onTogglePlayPause = { playToggled = true },
                    onSkipNext = {},
                )
            }
        }

        composeRule.onNodeWithText("Track Demo").assertIsDisplayed()
        composeRule.onNodeWithText("Artist Name").assertIsDisplayed()

        composeRule.onNodeWithTag("mini_player_play_pause_btn").performClick()
        assertTrue("MiniPlayer play/pause button should trigger toggle", playToggled)

        composeRule.onNodeWithTag("global_mini_player").performClick()
        assertTrue("MiniPlayer body click should open player", opened)
    }
}
