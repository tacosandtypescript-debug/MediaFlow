package com.mediaflow.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaflow.app.ui.player.PlayerScreen
import com.mediaflow.app.ui.player.SeekFeedbackEvent
import com.mediaflow.app.ui.player.components.AudioPlayerView
import com.mediaflow.app.ui.player.components.LivePlayerView
import com.mediaflow.app.ui.player.components.PlayPauseButton
import com.mediaflow.app.ui.player.components.PlayerSecondaryActions
import com.mediaflow.app.ui.player.components.PlayerTimeline
import com.mediaflow.app.ui.player.components.SeekFeedback
import com.mediaflow.app.ui.player.live.LiveEndedContent
import com.mediaflow.app.ui.theme.MediaFlowTheme
import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.domain.live.LiveSpaceEndState
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
        composeRule.onNodeWithText("sample", substring = true)
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
    fun shareButtonInvokesCallback() {
        var shared = false
        composeRule.setContent {
            MediaFlowTheme {
                PlayerSecondaryActions(
                    speed = 1.0f,
                    queueCount = 1,
                    isLive = false,
                    onSpeedChange = {},
                    onAddToPlaylist = {},
                    onOpenQueue = {},
                    onShare = { shared = true },
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("player_share_btn").assertIsDisplayed().performClick()
        assertTrue(shared)
    }

    @Test
    fun playPauseButtonTogglesState() {
        var clicked = false
        composeRule.setContent {
            MediaFlowTheme {
                PlayPauseButton(
                    playbackState = EnginePlaybackState.PAUSED,
                    isPlaying = false,
                    onClick = { clicked = true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Reproducir").assertIsDisplayed().performClick()
        assertTrue("Play button click should invoke callback", clicked)
    }

    @Test
    fun playPauseButtonFollowsEnginePlayingFlag() {
        composeRule.setContent {
            MediaFlowTheme {
                PlayPauseButton(
                    playbackState = EnginePlaybackState.PAUSED,
                    isPlaying = true,
                    onClick = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Pausa").assertIsDisplayed()
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
        composeRule.onNodeWithText("X SPACE").assertIsDisplayed()
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

    @Test
    fun livePlayerViewShowsEnVivoAndAutoDownloadToggle() {
        val liveSpace = XSpace(
            id = "1wGWjlyzqeNKQ",
            url = "https://x.com/i/spaces/1wGWjlyzqeNKQ",
            title = "Space en vivo",
            state = XSpaceState.LIVE,
            host = XParticipant(
                displayName = "Fake Kiffs",
                username = "FakeKiffs",
                userId = "123",
                role = ParticipantRole.HOST,
            ),
            liveListenersCount = 12,
            startedAtMs = System.currentTimeMillis() - 60_000L,
        )
        composeRule.setContent {
            MediaFlowTheme {
                LivePlayerView(
                    space = liveSpace,
                    playbackState = EnginePlaybackState.PLAYING,
                    liveEndState = LiveSpaceEndState.ActiveLive,
                    isAutoDownloadEnabled = false,
                    onTogglePlayPause = {},
                    onToggleAutoDownload = {},
                    onDownloadReplay = {},
                    onCheckReplayAgain = {},
                    isBroadcastLive = true,
                )
            }
        }
        composeRule.onNodeWithText("EN VIVO").assertIsDisplayed()
        composeRule.onNodeWithText("Space en vivo").assertIsDisplayed()
        composeRule.onNodeWithText("En el aire").assertExists()
        composeRule.onNodeWithTag("auto_download_toggle").assertExists()
    }

    @Test
    fun `livePlayerView shows FINALIZADO and waiting replay copy`() {
        val endedSpace = XSpace(
            id = "1wGWjlyzqeNKQ",
            url = "https://x.com/i/spaces/1wGWjlyzqeNKQ",
            title = "Space cerrado",
            state = XSpaceState.ENDED,
            host = XParticipant(
                displayName = "Fake Kiffs",
                username = "FakeKiffs",
                userId = "123",
                role = ParticipantRole.HOST,
            ),
        )
        composeRule.setContent {
            MediaFlowTheme {
                LivePlayerView(
                    space = endedSpace,
                    playbackState = EnginePlaybackState.ENDED,
                    liveEndState = LiveSpaceEndState.EndedReplayProcessing("Esperando repetición"),
                    isAutoDownloadEnabled = false,
                    onTogglePlayPause = {},
                    onToggleAutoDownload = {},
                    onDownloadReplay = {},
                    onCheckReplayAgain = {},
                    isBroadcastLive = false,
                )
            }
        }
        composeRule.onNodeWithText("FINALIZADO").assertIsDisplayed()
        composeRule.onNodeWithText("Esperando repetición").assertIsDisplayed()
    }

    @Test
    fun liveEndedContentShowsWaitingForReplay() {
        composeRule.setContent {
            MediaFlowTheme {
                LiveEndedContent(
                    endState = LiveSpaceEndState.EndedReplayProcessing("Esperando repetición"),
                    onDownloadReplay = {},
                    onCheckReplayAgain = {},
                )
            }
        }
        composeRule.onNodeWithText("Esperando repetición").assertIsDisplayed()
        composeRule.onAllNodesWithText("Comprobar de nuevo").assertCountEquals(0)
    }
}
