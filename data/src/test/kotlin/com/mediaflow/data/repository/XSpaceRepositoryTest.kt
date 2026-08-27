package com.mediaflow.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.spaces.XSpaceStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class XSpaceRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var store: XSpaceStore
    private lateinit var repository: XSpaceRepositoryImpl

    private val sampleHost = XParticipant("Test Host", "testhost", "u1", role = ParticipantRole.HOST)
    private val sampleSpeaker = XParticipant("Speaker User", "speaker1", "u2", role = ParticipantRole.SPEAKER)

    private val sampleSpace = XSpace(
        id = "space_123",
        url = "https://x.com/i/spaces/space_123",
        title = "Important Tech Space",
        state = XSpaceState.ENDED,
        host = sampleHost,
        cohosts = emptyList(),
        speakers = listOf(sampleSpeaker),
        participants = listOf(sampleHost, sampleSpeaker),
        durationSeconds = 3600L,
        recordingAvailable = true,
        liveListenersCount = 500,
        replayCount = 42,
    )

    @Before
    fun setup() {
        store = XSpaceStore(context, "test_spaces_${System.currentTimeMillis()}.json")
        repository = XSpaceRepositoryImpl(context, store, testScope.backgroundScope)
    }

    @Test
    fun `save and retrieve space metadata`() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        repository.saveSpace(sampleSpace, mediaId = "local_media_999")

        val retrieved = repository.getSpace("space_123")
        assertNotNull(retrieved)
        assertEquals("Important Tech Space", retrieved?.title)
        assertEquals("testhost", retrieved?.host?.username)
        assertEquals(1, retrieved?.speakers?.size)
        assertEquals("speaker1", retrieved?.speakers?.get(0)?.username)

        // Retrieve by associated mediaId
        val byMedia = repository.getSpaceForMedia("local_media_999")
        assertNotNull(byMedia)
        assertEquals("space_123", byMedia?.id)
    }

    @Test
    fun `recovers space records across repository restarts`() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        repository.saveSpace(sampleSpace, mediaId = "media_item_1")

        // Create new repository instance pointing to same persistent store
        val restartedRepo = XSpaceRepositoryImpl(context, store, testScope.backgroundScope)
        testScheduler.advanceUntilIdle()

        val space = restartedRepo.getSpace("space_123")
        assertNotNull(space)
        assertEquals("Important Tech Space", space?.title)
        assertEquals(XSpaceState.ENDED, space?.state)
        assertEquals(3600L, space?.durationSeconds)
        assertEquals(500, space?.liveListenersCount)
        assertEquals(42, space?.replayCount)
    }
}
