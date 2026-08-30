package com.mediaflow.app.ui.player.controls

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.app.ui.theme.MediaFlowTheme
import com.mediaflow.domain.player.EnginePlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioPrimaryControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun skipButtonsInvokeTrackSkipNotSeekTenSeconds() {
        var previous = 0
        var next = 0
        var playPause = 0
        var seekCalls = 0
        composeRule.setContent {
            MediaFlowTheme {
                AudioPrimaryControls(
                    playbackState = EnginePlaybackState.PLAYING,
                    isPlaying = true,
                    isBuffering = false,
                    onPlayPause = { playPause++ },
                    onPrevious = { previous++ },
                    onNext = { next++ },
                )
            }
        }
        composeRule.onNodeWithTag("player_skip_back").performClick()
        composeRule.onNodeWithTag("player_skip_forward").performClick()
        composeRule.onNodeWithContentDescription("Pausa").performClick()
        assertEquals(1, previous)
        assertEquals(1, next)
        assertEquals(1, playPause)
        assertEquals(0, seekCalls)
        assertTrue("audio transport must skip tracks, not seek ±10s", seekCalls == 0)
    }
}
