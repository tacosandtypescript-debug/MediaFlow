package com.mediaflow.data.player

import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.PlaybackStatus
import com.mediaflow.data.repository.ProgressRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ProgressRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `saveProgress persists to store and can be retrieved`() = runTest(testDispatcher) {
        val file = File(tempFolder.root, "progress.json")
        val store = PlatformProgressStore(file, Unit)
        val repository = ProgressRepositoryImpl(store, testDispatcher)

        val progress = PlaybackProgress(
            mediaId = "media-1",
            filePath = "/storage/emulated/0/Movies/MediaFlow/video.mp4",
            totalDurationMs = 120_000L,
            currentPositionMs = 60_000L,
            status = PlaybackStatus.IN_PROGRESS,
            lastPlayedAt = 123456789L,
            playCount = 1,
        )

        repository.saveProgress(progress)

        val retrieved = repository.getProgress("media-1")
        assertNotNull(retrieved)
        assertEquals("media-1", retrieved?.mediaId)
        assertEquals(60_000L, retrieved?.currentPositionMs)
        assertEquals(120_000L, retrieved?.totalDurationMs)
        assertEquals(PlaybackStatus.IN_PROGRESS, retrieved?.status)
    }

    @Test
    fun `progress is recovered across new repository instances simulating app restart`() = runTest(testDispatcher) {
        val file = File(tempFolder.root, "progress.json")
        val store1 = PlatformProgressStore(file, Unit)
        val repo1 = ProgressRepositoryImpl(store1, testDispatcher)

        repo1.saveProgress(
            PlaybackProgress(
                mediaId = "media-restart",
                filePath = "/test/path.mp4",
                totalDurationMs = 200_000L,
                currentPositionMs = 150_000L,
                status = PlaybackStatus.IN_PROGRESS,
                lastPlayedAt = 999999L,
            )
        )

        // Create a completely new repository reading from the same file
        val store2 = PlatformProgressStore(file, Unit)
        val repo2 = ProgressRepositoryImpl(store2, testDispatcher)

        val recovered = repo2.getProgress("media-restart")
        assertNotNull(recovered)
        assertEquals(150_000L, recovered?.currentPositionMs)
        assertEquals(PlaybackStatus.IN_PROGRESS, recovered?.status)
    }

    @Test
    fun `markCompleted updates status to COMPLETED and sets position to duration`() = runTest(testDispatcher) {
        val file = File(tempFolder.root, "progress.json")
        val store = PlatformProgressStore(file, Unit)
        val repository = ProgressRepositoryImpl(store, testDispatcher)

        repository.saveProgress(
            PlaybackProgress(
                mediaId = "media-comp",
                filePath = "/test/comp.mp4",
                totalDurationMs = 100_000L,
                currentPositionMs = 50_000L,
                status = PlaybackStatus.IN_PROGRESS,
            )
        )

        repository.markCompleted("media-comp", 100_000L)

        val completed = repository.getProgress("media-comp")
        assertNotNull(completed)
        assertEquals(PlaybackStatus.COMPLETED, completed?.status)
        assertEquals(100_000L, completed?.currentPositionMs)
        assertEquals(1f, completed?.playbackPercentage ?: 0f, 0.001f)
    }

    @Test
    fun `resetProgress sets position back to 0 and status to NEW`() = runTest(testDispatcher) {
        val file = File(tempFolder.root, "progress.json")
        val store = PlatformProgressStore(file, Unit)
        val repository = ProgressRepositoryImpl(store, testDispatcher)

        repository.saveProgress(
            PlaybackProgress(
                mediaId = "media-reset",
                filePath = "/test/reset.mp4",
                totalDurationMs = 100_000L,
                currentPositionMs = 80_000L,
                status = PlaybackStatus.IN_PROGRESS,
            )
        )

        repository.resetProgress("media-reset")

        val reset = repository.getProgress("media-reset")
        assertNotNull(reset)
        assertEquals(0L, reset?.currentPositionMs)
        assertEquals(PlaybackStatus.NEW, reset?.status)
        assertEquals(0f, reset?.playbackPercentage ?: 0f, 0.001f)
    }

    @Test
    fun `corrupt json file gracefully falls back to empty map`() = runTest(testDispatcher) {
        val file = File(tempFolder.root, "progress.json")
        file.writeText("invalid json content [[[")

        val store = PlatformProgressStore(file, Unit)
        val repository = ProgressRepositoryImpl(store, testDispatcher)

        val item = repository.getProgress("any-id")
        assertNull(item)
    }

    @Test
    fun `observeAllProgress emits live updates`() = runTest(testDispatcher) {
        val file = File(tempFolder.root, "progress.json")
        val store = PlatformProgressStore(file, Unit)
        val repository = ProgressRepositoryImpl(store, testDispatcher)

        val initialAll = repository.observeAllProgress().first()
        assertTrue(initialAll.isEmpty())

        repository.saveProgress(PlaybackProgress.new("flow-1", "/test/1.mp4"))

        val updatedAll = repository.observeAllProgress().first()
        assertEquals(1, updatedAll.size)
        assertNotNull(updatedAll["flow-1"])
    }
}
