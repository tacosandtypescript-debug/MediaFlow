package com.mediaflow.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.app.ui.player.PlayerScreen
import com.mediaflow.app.ui.player.SeekFeedbackEvent
import com.mediaflow.app.ui.player.components.AudioPlayerView
import com.mediaflow.app.ui.player.components.PlayPauseButton
import com.mediaflow.app.ui.player.components.PlayerTimeline
import com.mediaflow.app.ui.player.components.SeekFeedback
import com.mediaflow.app.ui.theme.MediaFlowTheme
import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.domain.player.EnginePlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-normal-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsPlayerSurfaceAndMediaName() {
        composeRule.setContent {
            MediaFlowTheme { PlayerScreen(mediaUri = "file:///tmp/sample.mp4", onBack = {}) }
        }
        composeRule.onNodeWithText("sample.mp4")
            .assertIsDisplayed()
    }

    @Test
    fun backButtonInvokesCallback() {
        var backClicked = false
        composeRule.setContent {
            MediaFlowTheme {
                PlayerScreen(mediaUri = "file:///tmp/sample.mp4", onBack = { backClicked = true })
            }
        }
        composeRule.onNodeWithContentDescription("Volver").performClick()
        assertTrue("Back button should invoke the callback", backClicked)
    }

    @Test
    fun playPauseButtonTogglesState() {
        var clicked = false
        composeRule.setContent {
            MediaFlowTheme {
                PlayPauseButton(
                    playbackState = EnginePlaybackState.PAUSED,
                    onClick = { clicked = true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Reproducir").assertIsDisplayed().performClick()
        assertTrue("Play button click should invoke callback", clicked)
    }

    @Test
    fun playerTimelineDisplaysFormattedTimestamps() {
        var seekTargetMs = -1L
        composeRule.setContent {
            MediaFlowTheme {
                PlayerTimeline(
                    currentPositionMs = 65_000L, // 01:05
                    durationMs = 300_000L,       // 05:00
                    onSeekTo = { seekTargetMs = it },
                )
            }
        }
        composeRule.onNodeWithText("01:05").assertIsDisplayed()
        composeRule.onNodeWithText("05:00").assertIsDisplayed()
    }

    @Test
    fun audioPlayerViewDisplaysXSpaceDetails() {
        val testSpace = XSpace(
            id = "1wGWjlyzqeNKQ",
            url = "https://x.com/fakekiffs/status/2092796653707067736",
            title = "ASOCIACIÓN DE MADRES SOLTERAS",
            state = XSpaceState.ENDED,
            host = XParticipant(
                displayName = "Fake Kiffs",
                username = "FakeKiffs",
                userId = "123",
                role = ParticipantRole.HOST,
            ),
            liveListenersCount = 190,
        )

        composeRule.setContent {
            MediaFlowTheme {
                AudioPlayerView(
                    title = testSpace.title,
                    isPlaying = true,
                    space = testSpace,
                )
            }
        }

        composeRule.onNodeWithText("ASOCIACIÓN DE MADRES SOLTERAS").assertIsDisplayed()
        composeRule.onNodeWithText("X SPACE AUDIO").assertIsDisplayed()
        composeRule.onNodeWithText("190 oyentes").assertIsDisplayed()
        composeRule.onNodeWithText("Host: Fake Kiffs (@FakeKiffs)").assertIsDisplayed()
    }

    @Test
    fun seekFeedbackRendersAnimatedPill() {
        composeRule.setContent {
            MediaFlowTheme {
                SeekFeedback(event = SeekFeedbackEvent.Forward(seconds = 10))
            }
        }
        composeRule.onNodeWithContentDescription("+10 segundos").assertIsDisplayed()
    }
}
